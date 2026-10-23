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
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.delayedtask.*;
import io.github.loadup.pay.payment.domain.gateway.PaymentRecoveryGateway;
import io.github.loadup.pay.payment.domain.model.PaymentRecoveryCursor;
import io.github.loadup.pay.payment.domain.model.PaymentRecoveryKey;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;

/** Continuation generations complement native task claims; neither is channel evidence. */
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
    private final long pollDelaySeconds;

    public PaymentRecoveryService(PaymentRecoveryGateway cursors, DelayedTaskTemplate tasks, PaymentExecutionLocks locks,
            PaymentExecutionService execution, io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway payments,
            PaymentAcceptanceTransactions transactions, @Qualifier("paymentClock") Clock clock,
            @Value("${loadup.modules.payment.payment-recovery.poll-delay-seconds:30}") long pollDelaySeconds) {
        DomainViolation.require(pollDelaySeconds >= 1 && pollDelaySeconds <= 86400, DomainViolation.Reason.INVALID_ARGUMENT);
        this.cursors = cursors; this.tasks = tasks; this.locks = locks; this.execution = execution;
        this.payments = payments; this.transactions = transactions; this.clock = clock; this.pollDelaySeconds = pollDelaySeconds;
    }

    /** The original acceptance and its first wake-up commit atomically. */
    public void register(String tenant, StoredPaymentAcceptance stored) {
        writable(tenant);
        if (stored.payment().state() != io.github.loadup.pay.payment.domain.model.PaymentState.INIT
                || stored.payment().version() != 0 || stored.payment().activeAttemptId() != null)
            throw new CommonException(INVALID_STATE);
        var cursor = PaymentRecoveryCursor.init(stored.payment().id(), stored.payment().initiatedAt());
        cursors.insert(tenant, cursor);
        tasks.register(TYPE, new PaymentRecoveryKey(cursor.paymentId(), cursor.generation()).bizId(), cursor.dueAt());
    }

    public void handle(DelayedTask task) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (task == null || task.tenantId() == null || !TYPE.equals(task.taskType()) || !task.tenantId().equals(TenantUtil.getTenantId()))
            throw new CommonException(SNAPSHOT_INVALID);
        DomainChecks.id(task.tenantId());
        final PaymentRecoveryKey key;
        try { key = PaymentRecoveryKey.parse(task.bizId()); }
        catch (DomainViolation invalid) { throw new CommonException(SNAPSHOT_INVALID); }
        var registered = tasks.find(TYPE, task.bizId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        var now = LocalDateTime.now(clock);
        if (!task.id().equals(registered.id()) || !task.tenantId().equals(registered.tenantId())
                || !TYPE.equals(registered.taskType()) || !task.bizId().equals(registered.bizId())
                || !task.executeAt().equals(registered.executeAt()) || registered.status() != DelayedTaskStatus.RUNNING
                || registered.attempts() != task.attempts() || registered.leaseUntil() == null
                || !now.isBefore(registered.leaseUntil()) || now.isBefore(registered.nextExecuteAt()))
            throw new CommonException(INVALID_STATE);
        boolean active = transactions.write(task.tenantId(), () -> {
            var request = lockPayment(task.tenantId(), key.paymentId());
            var cursor = cursor(task.tenantId(), key);
            if (cursor.completed() || key.generation() < cursor.generation()) return false;
            if (!cursor.dueAt().equals(task.executeAt())) throw new CommonException(SNAPSHOT_INVALID);
            if (terminal(request)) {
                cursors.update(task.tenantId(), cursor, cursor.finish());
                return false;
            }
            return true;
        });
        if (!active) return;
        // The durable Y2 winner owns first submit; recovery of a started attempt only queries or closes its original request.
        execution.progress(task.tenantId(), key.paymentId());
        transactions.write(task.tenantId(), () -> {
            var request = lockPayment(task.tenantId(), key.paymentId());
            var before = cursor(task.tenantId(), key);
            if (before.completed() || key.generation() < before.generation()) return true;
            if (terminal(request)) {
                cursors.update(task.tenantId(), before, before.finish());
            } else {
                var at = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
                var nextAt = (at.isBefore(before.dueAt()) ? before.dueAt() : at).plusSeconds(pollDelaySeconds);
                var next = before.schedule(nextAt, key.generation());
                cursors.update(task.tenantId(), before, next);
                // Native task claims are not rescheduled while RUNNING; commit a distinct successor atomically.
                tasks.register(TYPE, new PaymentRecoveryKey(next.paymentId(), next.generation()).bizId(), next.dueAt());
            }
            return true;
        });
    }

    private PaymentRecoveryCursor cursor(String tenant, PaymentRecoveryKey key) {
        var value = cursors.lock(tenant, key.paymentId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!value.paymentId().equals(key.paymentId()) || key.generation() > value.generation())
            throw new CommonException(SNAPSHOT_INVALID);
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
