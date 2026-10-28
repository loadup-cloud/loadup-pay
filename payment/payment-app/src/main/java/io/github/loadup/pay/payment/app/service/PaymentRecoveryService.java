/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.payment.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.delayedtask.DelayedTask;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import io.github.loadup.pay.payment.app.model.RecoveryHandlingOutcome;
import io.github.loadup.pay.payment.domain.gateway.PaymentRecoveryGateway;
import io.github.loadup.pay.payment.domain.model.PaymentRecoveryCursor;
import io.github.loadup.pay.payment.domain.model.PaymentRecoveryKey;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.policy.RecoveryPollingPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Continuation generations complement native task claims; neither is channel evidence.
 */
@Service
public class PaymentRecoveryService {
    public static final String TYPE = "pay.payment.collection.progress";
    private final PaymentRecoveryGateway cursors;
    private final DelayedTaskTemplate tasks;
    private final PaymentExecutionLocks locks;
    private final io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway payments;
    private final PaymentExecutionService execution;
    private final PaymentAcceptanceTransactions transactions;
    private final Clock clock;
    private final RecoveryPollingPolicy polling;

    public PaymentRecoveryService(PaymentRecoveryGateway cursors, DelayedTaskTemplate tasks, PaymentExecutionLocks locks,
                                  PaymentExecutionService execution, io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway payments,
                                  PaymentAcceptanceTransactions transactions, @Qualifier("paymentClock") Clock clock,
                                  RecoveryPollingPolicy polling) {
        this.cursors = cursors;
        this.tasks = tasks;
        this.locks = locks;
        this.execution = execution;
        this.payments = payments;
        this.transactions = transactions;
        this.clock = clock;
        this.polling = java.util.Objects.requireNonNull(polling);
    }

    /**
     * The original acceptance and its first wake-up commit atomically.
     */
    public void register(String tenant, StoredPaymentAcceptance stored) {
        writable(tenant);
        if (stored.payment().state() != io.github.loadup.pay.payment.domain.model.PaymentState.INIT
                || stored.payment().version() != 0 || stored.payment().activeAttemptId() != null)
            throw new CommonException(INVALID_STATE);
        var cursor = PaymentRecoveryCursor.init(stored.payment().id(), stored.payment().initiatedAt());
        cursors.insert(tenant, cursor);
        tasks.register(TYPE, new PaymentRecoveryKey(cursor.paymentId(), cursor.generation()).bizId(), cursor.dueAt());
    }

    public RecoveryHandlingOutcome handle(DelayedTask task) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        var identity = RecoveryTaskProtocol.receive(TYPE, task, TenantUtil.getTenantId());
        var key = new PaymentRecoveryKey(identity.subjectId(), identity.generation());
        var registered = tasks.find(TYPE, task.bizId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        RecoveryTaskProtocol.requireClaim(TYPE, task, registered, LocalDateTime.now(clock));
        boolean active = transactions.writeLocal(task.tenantId(), () -> {
            var request = lockPayment(task.tenantId(), key.paymentId());
            var cursor = cursor(task.tenantId(), key);
            if (!RecoveryTaskProtocol.current(identity, cursor.position(), task.executeAt())) return false;
            if (terminal(request)) {
                cursors.update(task.tenantId(), cursor, cursor.finish());
                return false;
            }
            return true;
        });
        if (!active) return RecoveryHandlingOutcome.SKIPPED;
        // The durable Y2 winner owns first submit; recovery of a started attempt only queries or closes its original request.
        execution.progress(task.tenantId(), key.paymentId());
        return transactions.writeLocal(task.tenantId(), () -> {
            var request = lockPayment(task.tenantId(), key.paymentId());
            var before = cursor(task.tenantId(), key);
            if (!RecoveryTaskProtocol.current(identity, before.position(), task.executeAt()))
                return RecoveryHandlingOutcome.SKIPPED;
            if (terminal(request)) {
                cursors.update(task.tenantId(), before, before.finish());
                return RecoveryHandlingOutcome.COMPLETED;
            } else {
                var nextAt = polling.nextAt(task.tenantId(), TYPE, key.paymentId(), before.generation(),
                        request.payment().initiatedAt(), before.dueAt(), LocalDateTime.now(clock));
                var next = before.schedule(nextAt, key.generation());
                cursors.update(task.tenantId(), before, next);
                // Native task claims are not rescheduled while RUNNING; commit a distinct successor atomically.
                tasks.register(TYPE, new PaymentRecoveryKey(next.paymentId(), next.generation()).bizId(), next.dueAt());
                return RecoveryHandlingOutcome.CONTINUED;
            }
        });
    }

    private PaymentRecoveryCursor cursor(String tenant, PaymentRecoveryKey key) {
        var value = cursors.lock(tenant, key.paymentId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        return value;
    }

    private StoredPaymentAcceptance lockPayment(String tenant, String paymentId) {
        var accepted = payments.find(tenant, paymentId).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        return locks.lock(tenant, paymentId, accepted.callerServiceId()).payment();
    }

    private boolean terminal(StoredPaymentAcceptance payment) {
        return PaymentExecutionService.terminal(payment.payment().state());
    }

    private void writable(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
    }
}
