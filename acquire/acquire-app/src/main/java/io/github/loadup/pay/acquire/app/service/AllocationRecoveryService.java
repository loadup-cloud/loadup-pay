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
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.delayedtask.DelayedTask;
import io.github.loadup.components.delayedtask.DelayedTaskHandler;
import io.github.loadup.components.delayedtask.DelayedTaskStatus;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import io.github.loadup.pay.acquire.client.command.PaymentProgressRefreshCommand;
import io.github.loadup.pay.acquire.domain.gateway.AllocationRecoveryGateway;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.gateway.PaymentOutcomeGateway;
import io.github.loadup.pay.acquire.domain.model.AllocationRecoveryCursor;
import io.github.loadup.pay.acquire.domain.model.AllocationRecoveryKey;
import io.github.loadup.pay.acquire.domain.model.AllocationState;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.acquire.domain.model.StoredPaymentAllocation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Resumes committed A2 intents; native task ownership never grants a new payment or financial evidence.
 */
@Service
public class AllocationRecoveryService implements DelayedTaskHandler {
    private final AllocationRecoveryGateway cursors;
    private final AllocationRecoveryQueue queue;
    private final DelayedTaskTemplate tasks;
    private final OrderCreationGateway orders;
    private final PaymentOutcomeGateway allocations;
    private final PaymentInitiationOperations initiation;
    private final PaymentOutcomeOperations outcomes;
    private final OrderCreationTransactions transactions;
    private final Clock clock;

    public AllocationRecoveryService(AllocationRecoveryGateway cursors, AllocationRecoveryQueue queue,
                                     DelayedTaskTemplate tasks, OrderCreationGateway orders, PaymentOutcomeGateway allocations,
                                     PaymentInitiationOperations initiation, PaymentOutcomeOperations outcomes,
                                     OrderCreationTransactions transactions, Clock clock) {
        this.cursors = cursors;
        this.queue = queue;
        this.tasks = tasks;
        this.orders = orders;
        this.allocations = allocations;
        this.initiation = initiation;
        this.outcomes = outcomes;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public String taskType() {
        return AllocationRecoveryQueue.TYPE;
    }

    @Override
    public void handle(DelayedTask task) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        var key = validateTask(task);
        var tenant = TenantUtil.getTenantId();
        var located = cursors.find(tenant, key.paymentId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        var pending = transactions.writeLocal(tenant, () -> {
            var locked = lock(tenant, key, located.orderId());
            if (!current(locked.cursor(), key, task)) {
                return Optional.<StoredPaymentAllocation>empty();
            }
            if (settled(tenant, locked)) {
                queue.finish(tenant, locked.cursor());
                return Optional.<StoredPaymentAllocation>empty();
            }
            return Optional.of(locked.allocation());
        });
        if (pending.isEmpty()) {
            return;
        }

        var stored = pending.get();
        var original = stored.allocation();
        var owner = new OrderPaymentScope(tenant, stored.callerScopeHash());
        // Durable original ownership authorizes continuation, never a new A2 or renewed checkout capability.
        var principal = new PaymentInitiationPrincipal(tenant, stored.callerScopeHash(), original.actorScope(), (order, creating) -> {
            if (creating || !order.order().id().equals(original.orderId())
                    || !order.callerScopeHash().equals(stored.callerScopeHash())) {
                throw new CommonException(ACTION_FORBIDDEN);
            }
        });
        var command = outcomes.bound(owner, orders.findOrder(tenant, original.orderId())
                .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID)), stored);
        var recovered = initiation.recover(principal, original.orderId(), original.actionKey(),
                original.amount().getCent(), command.choice());
        if (recovered == null || recovered.state() == PaymentIntentRecovery.State.NOT_OBSERVED) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        if (recovered.state() == PaymentIntentRecovery.State.RESOLVED) {
            if (!original.paymentId().equals(recovered.paymentId())) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            if (recovered.closed() == null) {
                outcomes.refresh(owner, new PaymentProgressRefreshCommand(original.orderId(), original.paymentId()));
            } else {
                outcomes.applyInputExpiry(owner, original.orderId(), original.paymentId(), recovered.closed());
            }
        }
        transactions.writeLocal(tenant, () -> {
            var locked = lock(tenant, key, located.orderId());
            if (!current(locked.cursor(), key, task)) {
                return true;
            }
            if (settled(tenant, locked)) {
                queue.finish(tenant, locked.cursor());
            } else {
                queue.schedule(tenant, locked.cursor(), LocalDateTime.now(clock));
            }
            return true;
        });
    }

    private AllocationRecoveryKey validateTask(DelayedTask task) {
        if (task == null || !taskType().equals(task.taskType())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        final AllocationRecoveryKey key;
        try {
            DomainChecks.id(TenantUtil.getTenantId());
            key = AllocationRecoveryKey.parse(task.bizId());
        } catch (DomainViolation invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var registered = tasks.find(taskType(), task.bizId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        var now = LocalDateTime.now(clock);
        if (!task.id().equals(registered.id())
                || !taskType().equals(registered.taskType()) || !task.bizId().equals(registered.bizId())
                || !task.executeAt().equals(registered.executeAt()) || registered.status() != DelayedTaskStatus.RUNNING
                || registered.attempts() != task.attempts() || registered.leaseUntil() == null
                || !now.isBefore(registered.leaseUntil()) || now.isBefore(registered.nextExecuteAt())) {
            throw new CommonException(INVALID_STATE);
        }
        return key;
    }

    private AllocationView lock(String tenant, AllocationRecoveryKey key, String orderId) {
        // Consistent with A2/A3: order, allocation, then continuation cursor.
        var order = orders.lockOrder(tenant, orderId);
        var allocation = allocations.lockPayment(tenant, orderId, key.paymentId())
                .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        var cursor = cursors.lock(tenant, key.paymentId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!order.order().id().equals(orderId) || !allocation.allocation().orderId().equals(orderId)
                || !allocation.allocation().paymentId().equals(key.paymentId()) || !cursor.orderId().equals(orderId)
                || !cursor.paymentId().equals(key.paymentId()) || key.generation() > cursor.generation()) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return new AllocationView(order, allocation, cursor);
    }

    private boolean current(AllocationRecoveryCursor cursor, AllocationRecoveryKey key, DelayedTask task) {
        if (cursor.completed() || key.generation() < cursor.generation()) {
            return false;
        }
        if (!cursor.dueAt().equals(task.executeAt())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return true;
    }

    /**
     * Evaluate a consistent read snapshot or already-locked view; never acquires execution rights.
     */
    boolean isCurrent(String tenant, StoredAcquireOrder order, StoredPaymentAllocation allocation,
                      AllocationRecoveryCursor cursor, long generation) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (!tenant.equals(TenantUtil.getTenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        if (!order.order().id().equals(cursor.orderId()) || !allocation.allocation().orderId().equals(cursor.orderId())
                || !allocation.allocation().paymentId().equals(cursor.paymentId()) || generation > cursor.generation()) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        if (cursor.completed() || generation < cursor.generation()) {
            return false;
        }
        return !settled(tenant, new AllocationView(order, allocation, cursor));
    }

    private boolean settled(String tenant, AllocationView locked) {
        var stored = locked.allocation();
        var allocation = stored.allocation();
        if (!locked.order().callerScopeHash().equals(stored.callerScopeHash())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var command = outcomes.bound(new OrderPaymentScope(tenant, stored.callerScopeHash()), locked.order(), stored);
        if (!command.paymentId().equals(allocation.paymentId()) || !command.allocationId().equals(allocation.id())
                || command.amountMinor() != allocation.amount().getCent()
                || !command.currency().equals(allocation.amount().getCurrencyValue())
                || !command.requestDigest().equals(allocation.requestDigest())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var receipt = allocations.findReceipt(tenant, allocation.id());
        if (allocation.state() == AllocationState.HELD) {
            if (receipt.isPresent() || locked.order().order().paymentReservedMinor() < allocation.amount().getCent()
                    || locked.order().order().reservedPaymentCount() < 1) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            return false;
        }
        if (allocation.state() == AllocationState.INIT) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        try {
            receipt.orElseThrow(() -> new CommonException(SNAPSHOT_INVALID)).requireBoundTo(allocation);
        } catch (DomainViolation corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return true;
    }

    private record AllocationView(StoredAcquireOrder order, StoredPaymentAllocation allocation,
                                  AllocationRecoveryCursor cursor) {
        @Override
        public String toString() {
            return ToStringAsJson.reflectionToString(this);
        }
    }
}
