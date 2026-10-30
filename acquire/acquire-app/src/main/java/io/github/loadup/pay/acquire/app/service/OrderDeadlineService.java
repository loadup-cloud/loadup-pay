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
import io.github.loadup.components.delayedtask.DelayedTask;
import io.github.loadup.components.delayedtask.DelayedTaskHandler;
import io.github.loadup.components.delayedtask.DelayedTaskStatus;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import io.github.loadup.pay.acquire.domain.gateway.OrderClosureGateway;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.OrderTaskKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

/**
 * A native due wake-up uses the persisted deadline version, never a timer as financial evidence.
 */
@Service
public class OrderDeadlineService implements DelayedTaskHandler {
    private final DelayedTaskTemplate tasks;
    private final OrderCreationGateway orders;
    private final io.github.loadup.pay.acquire.domain.gateway.OrderExtensionGateway extensions;
    private final OrderClosureGateway closes;
    private final OrderClosureOperations operations;
    private final OrderCreationTransactions transactions;
    private final OrderSnapshotEvents snapshots;
    private final Clock clock;

    public OrderDeadlineService(DelayedTaskTemplate tasks, OrderCreationGateway orders,
                                io.github.loadup.pay.acquire.domain.gateway.OrderExtensionGateway extensions, OrderClosureGateway closes,
                                OrderClosureOperations operations, OrderCreationTransactions transactions, OrderSnapshotEvents snapshots,
                                Clock clock) {
        this.tasks = tasks;
        this.orders = orders;
        this.extensions = extensions;
        this.closes = closes;
        this.operations = operations;
        this.transactions = transactions;
        this.snapshots = snapshots;
        this.clock = clock;
    }

    @Override
    public String taskType() {
        return OrderDeadlineTasks.TYPE;
    }

    @Override
    public void handle(DelayedTask task) {
        var key = validate(task);
        if (key.generation() < 1) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        String tenant = TenantUtil.getTenantId();
        transactions.writeLocal(tenant, () -> {
            var order = orders.lockOrder(tenant, key.orderId());
            if (!order.order().id().equals(key.orderId())) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            long version = extensions.deadlineVersion(tenant, order.order());
            if (key.generation() < version) {
                return true;
            }
            if (key.generation() > version || !task.executeAt().equals(order.order().orderDueAt())) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            var at = LocalDateTime.now(clock);
            if (at.isBefore(order.order().orderDueAt())) {
                throw new CommonException(INVALID_STATE);
            }
            var scope = new OrderPaymentScope(tenant, order.callerScopeHash());
            if (order.order().state() == AcquireOrderState.PAID || order.order().state() == AcquireOrderState.CLOSED) {
                return true;
            }
            if (order.order().state() == AcquireOrderState.CLOSING) {
                var original = closes.findIntent(tenant, key.orderId())
                        .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
                operations.isCurrent(scope, order, original, original.rowVersion());
                return true;
            }
            var due = order.order().checkDue(at);
            if (due.state() == AcquireOrderState.CLOSING) {
                operations.startLocked(scope, order, new io.github.loadup.pay.acquire.client.command.AcquireOrderCloseCommand(
                        key.orderId(), "order-deadline/" + version, "ORDER_EXPIRED"));
            } else if (!due.equals(order.order())) {
                closes.keepPending(tenant, order.order(), due);
                snapshots.publish(tenant, due.id(), due.version());
            }
            return true;
        });
    }

    private OrderTaskKey validate(DelayedTask task) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (task == null || !taskType().equals(task.taskType())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        final OrderTaskKey key;
        try {
            key = OrderTaskKey.parse(task.bizId());
        } catch (DomainViolation invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var nativeTask = tasks.find(taskType(), task.bizId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        var at = LocalDateTime.now(clock);
        if (!task.id().equals(nativeTask.id())
                || !taskType().equals(nativeTask.taskType()) || !task.bizId().equals(nativeTask.bizId())
                || !task.executeAt().equals(nativeTask.executeAt()) || nativeTask.status() != DelayedTaskStatus.RUNNING
                || nativeTask.attempts() != task.attempts() || nativeTask.leaseUntil() == null
                || !at.isBefore(nativeTask.leaseUntil()) || at.isBefore(nativeTask.nextExecuteAt())) {
            throw new CommonException(INVALID_STATE);
        }
        return key;
    }
}
