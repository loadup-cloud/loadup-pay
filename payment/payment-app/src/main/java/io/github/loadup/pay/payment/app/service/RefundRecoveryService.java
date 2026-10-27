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
import io.github.loadup.components.delayedtask.*;
import io.github.loadup.pay.payment.domain.gateway.RefundRecoveryGateway;
import io.github.loadup.pay.payment.domain.model.RefundOrder;
import io.github.loadup.pay.payment.domain.model.RefundRecoveryCursor;
import io.github.loadup.pay.payment.domain.model.RefundRecoveryKey;
import io.github.loadup.pay.payment.domain.model.StoredRefundRequest;
import io.github.loadup.pay.payment.domain.policy.RecoveryPollingPolicy;
import io.github.loadup.pay.payment.app.model.RecoveryHandlingOutcome;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;

/** Continuation generations complement native task claims; neither is channel evidence. */
@Service
public class RefundRecoveryService {
    public static final String TYPE = "pay.payment.refund.progress";
    private final RefundRecoveryGateway cursors;
    private final DelayedTaskTemplate tasks;
    private final RefundExecutionLocks locks;
    private final RefundExecutionService execution;
    private final PaymentAcceptanceTransactions transactions;
    private final Clock clock;
    private final RecoveryPollingPolicy polling;

    public RefundRecoveryService(RefundRecoveryGateway cursors, DelayedTaskTemplate tasks, RefundExecutionLocks locks,
            RefundExecutionService execution, PaymentAcceptanceTransactions transactions, @Qualifier("paymentClock") Clock clock,
            RecoveryPollingPolicy polling) {
        this.cursors = cursors; this.tasks = tasks; this.locks = locks; this.execution = execution;
        this.transactions = transactions; this.clock = clock; this.polling = java.util.Objects.requireNonNull(polling);
    }

    /** Caller holds the original parent/request transaction when inserting the fixed refund plan. */
    public void register(String tenant, StoredRefundRequest stored) {
        writable(tenant);
        if (stored.request().version() != 0 || stored.request().children().stream().anyMatch(c -> c.state()
                != io.github.loadup.pay.payment.domain.model.RefundState.INIT)) throw new CommonException(INVALID_STATE);
        var cursor = RefundRecoveryCursor.init(stored.request().id(), stored.plannedAt());
        cursors.insert(tenant, cursor);
        tasks.register(TYPE, new RefundRecoveryKey(cursor.requestId(), cursor.generation()).bizId(), cursor.dueAt());
    }

    public RecoveryHandlingOutcome handle(DelayedTask task) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        var identity = RecoveryTaskProtocol.receive(TYPE, task, TenantUtil.getTenantId());
        var key = new RefundRecoveryKey(identity.subjectId(), identity.generation());
        var registered = tasks.find(TYPE, task.bizId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        RecoveryTaskProtocol.requireClaim(TYPE, task, registered, LocalDateTime.now(clock));
        boolean active = transactions.writeLocal(task.tenantId(), () -> {
            var request = locks.request(task.tenantId(), key.requestId());
            var cursor = cursor(task.tenantId(), key);
            if (!RecoveryTaskProtocol.current(identity, cursor.position(), task.executeAt())) return false;
            if (terminal(request)) {
                cursors.update(task.tenantId(), cursor, cursor.finish());
                return false;
            }
            return true;
        });
        if (!active) return RecoveryHandlingOutcome.SKIPPED;
        // Existing durable first-submit winner and original-request query logic own all channel side effects.
        execution.progress(task.tenantId(), key.requestId());
        return transactions.writeLocal(task.tenantId(), () -> {
            var request = locks.request(task.tenantId(), key.requestId());
            var before = cursor(task.tenantId(), key);
            if (!RecoveryTaskProtocol.current(identity, before.position(), task.executeAt())) return RecoveryHandlingOutcome.SKIPPED;
            if (terminal(request)) {
                cursors.update(task.tenantId(), before, before.finish());
                return RecoveryHandlingOutcome.COMPLETED;
            } else {
                var nextAt = polling.nextAt(task.tenantId(), TYPE, key.requestId(), before.generation(),
                        request.plannedAt(), before.dueAt(), LocalDateTime.now(clock));
                var next = before.schedule(nextAt, key.generation());
                cursors.update(task.tenantId(), before, next);
                // Native task claims are not rescheduled while RUNNING; commit a distinct successor atomically.
                tasks.register(TYPE, new RefundRecoveryKey(next.requestId(), next.generation()).bizId(), next.dueAt());
                return RecoveryHandlingOutcome.CONTINUED;
            }
        });
    }

    private RefundRecoveryCursor cursor(String tenant, RefundRecoveryKey key) {
        var value = cursors.lock(tenant, key.requestId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        return value;
    }

    private boolean terminal(StoredRefundRequest request) {
        return request.request().children().stream().allMatch(RefundOrder::terminal);
    }

    private void writable(String tenant) {
        if (!tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
    }
}
