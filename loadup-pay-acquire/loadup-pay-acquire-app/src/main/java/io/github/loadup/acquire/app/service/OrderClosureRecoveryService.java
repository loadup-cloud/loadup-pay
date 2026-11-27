/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.acquire.app.service;

import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

import io.github.loadup.acquire.domain.gateway.OrderClosureGateway;
import io.github.loadup.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.acquire.domain.model.OrderCloseIntent;
import io.github.loadup.acquire.domain.model.OrderTaskKey;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.components.task.TaskExecution;
import io.github.loadup.components.task.TaskHandler;
import io.github.loadup.components.task.TaskStatus;
import io.github.loadup.components.task.TaskTemplate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Native at-least-once close continuation; the persisted original C0 supplies scope, reason and
 * financial identities.
 */
@Service
public class OrderClosureRecoveryService implements TaskHandler {
    private final TaskTemplate tasks;
    private final OrderCreationGateway orders;
    private final OrderClosureGateway closes;
    private final OrderClosureOperations operations;
    private final OrderCreationTransactions transactions;
    private final Clock clock;

    public OrderClosureRecoveryService(
            TaskTemplate tasks,
            OrderCreationGateway orders,
            OrderClosureGateway closes,
            OrderClosureOperations operations,
            OrderCreationTransactions transactions,
            Clock clock) {
        this.tasks = tasks;
        this.orders = orders;
        this.closes = closes;
        this.operations = operations;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public String taskType() {
        return OrderClosureTasks.TYPE;
    }

    @Override
    public void handle(TaskExecution task) {
        var key = validate(task);
        var pending = transactions.writeLocal(() -> {
            var order = orders.lockOrder(key.orderId());
            var intent = closes.findIntent(key.orderId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            if (!order.order().id().equals(key.orderId())
                    || !intent.orderId().equals(key.orderId())
                    || task.nextExecuteAt().isBefore(intent.requestedAt())) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            var scope = new OrderPaymentScope(order.callerScopeHash());
            return operations.isCurrent(scope, order, intent, key.generation())
                    ? Optional.of(intent)
                    : Optional.<OrderCloseIntent>empty();
        });
        if (pending.isPresent()) {
            var intent = pending.get();
            operations.resume(new OrderPaymentScope(intent.callerScopeHash()), intent);
        }
    }

    private OrderTaskKey validate(TaskExecution task) {
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
                || !taskType().equals(nativeTask.taskType())
                || !task.bizId().equals(nativeTask.bizId())
                || task.nextExecuteAt() == null
                || !task.nextExecuteAt().equals(nativeTask.nextExecuteAt())
                || nativeTask.status() != TaskStatus.RUNNING
                || nativeTask.attempts() != task.attempts()
                || nativeTask.leaseUntil() == null
                || !at.isBefore(nativeTask.leaseUntil())
                || at.isBefore(nativeTask.nextExecuteAt())) {
            throw new CommonException(INVALID_STATE);
        }
        return key;
    }
}
