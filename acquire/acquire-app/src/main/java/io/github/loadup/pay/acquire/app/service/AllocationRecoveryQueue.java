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
package io.github.loadup.pay.acquire.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import io.github.loadup.pay.acquire.domain.gateway.AllocationRecoveryGateway;
import io.github.loadup.pay.acquire.domain.model.AllocationRecoveryCursor;
import io.github.loadup.pay.acquire.domain.model.AllocationRecoveryKey;
import io.github.loadup.pay.acquire.domain.model.AllocationState;
import io.github.loadup.pay.acquire.domain.model.StoredPaymentAllocation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;

/**
 * Business cursor and native wake-ups always join the acquiring transaction, never the payment RPC.
 */
@Component
public class AllocationRecoveryQueue {
    public static final String TYPE = "pay.acquire.payment.intent.progress";
    private final AllocationRecoveryGateway cursors;
    private final DelayedTaskTemplate tasks;
    private final long pollDelaySeconds;

    public AllocationRecoveryQueue(AllocationRecoveryGateway cursors, DelayedTaskTemplate tasks,
                                   @Value("${loadup.modules.acquire.payment-recovery.poll-delay-seconds:30}") long pollDelaySeconds) {
        DomainViolation.require(pollDelaySeconds >= 1 && pollDelaySeconds <= 86400, DomainViolation.Reason.INVALID_ARGUMENT);
        this.cursors = cursors;
        this.tasks = tasks;
        this.pollDelaySeconds = pollDelaySeconds;
    }

    public void register(String tenant, StoredPaymentAllocation allocation, LocalDateTime initiatedAt) {
        writable(tenant);
        if (allocation.allocation().state() != AllocationState.HELD || allocation.allocation().paymentSourceVersion() != 0L
                || allocation.rowVersion() != 0L) {
            throw new CommonException(INVALID_STATE);
        }
        var cursor = AllocationRecoveryCursor.init(allocation.allocation().paymentId(), allocation.allocation().orderId(),
                initiatedAt.truncatedTo(ChronoUnit.MICROS).plusSeconds(pollDelaySeconds));
        cursors.insert(tenant, cursor);
        tasks.register(TYPE, new AllocationRecoveryKey(cursor.paymentId(), cursor.generation()).bizId(), cursor.dueAt());
    }

    void schedule(String tenant, AllocationRecoveryCursor before, LocalDateTime observedAt) {
        writable(tenant);
        var base = observedAt.isBefore(before.dueAt()) ? before.dueAt() : observedAt;
        var after = before.schedule(base.truncatedTo(ChronoUnit.MICROS).plusSeconds(pollDelaySeconds), before.generation());
        cursors.update(tenant, before, after);
        tasks.register(TYPE, new AllocationRecoveryKey(after.paymentId(), after.generation()).bizId(), after.dueAt());
    }

    void finish(String tenant, AllocationRecoveryCursor before) {
        writable(tenant);
        if (!before.completed()) {
            cursors.update(tenant, before, before.finish());
        }
    }

    private void writable(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new CommonException(INVALID_STATE);
        }
    }
}
