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
import io.github.loadup.components.delayedtask.*;
import io.github.loadup.pay.acquire.domain.gateway.OrderClosureGateway;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.model.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Clock;
import java.time.LocalDateTime;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.*;

/** Native at-least-once close continuation; the persisted original C0 supplies scope, reason and financial identities. */
@Service
public class OrderClosureRecoveryService implements DelayedTaskHandler {
    private final DelayedTaskTemplate tasks;
    private final OrderCreationGateway orders;
    private final OrderClosureGateway closes;
    private final OrderClosureOperations operations;
    private final OrderCreationTransactions transactions;
    private final Clock clock;

    public OrderClosureRecoveryService(DelayedTaskTemplate tasks, OrderCreationGateway orders, OrderClosureGateway closes,
            OrderClosureOperations operations, OrderCreationTransactions transactions, @Qualifier("acquireClock") Clock clock) {
        this.tasks = tasks; this.orders = orders; this.closes = closes; this.operations = operations;
        this.transactions = transactions; this.clock = clock;
    }
    @Override public String taskType() { return OrderClosureTasks.TYPE; }
    @Override public void handle(DelayedTask task) {
        var key = validate(task);
        var pending = transactions.writeLocal(task.tenantId(), () -> {
            var order = orders.lockOrder(task.tenantId(), key.orderId());
            var intent = closes.findIntent(task.tenantId(), key.orderId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            if (!order.order().id().equals(key.orderId()) || !intent.orderId().equals(key.orderId())
                    || task.executeAt().isBefore(intent.requestedAt())) throw new CommonException(SNAPSHOT_INVALID);
            var scope = new OrderPaymentScope(task.tenantId(), order.callerScopeHash());
            return operations.isCurrent(scope, order, intent, key.generation())
                    ? java.util.Optional.of(intent) : java.util.Optional.<OrderCloseIntent>empty();
        });
        if (pending.isPresent()) {
            var intent = pending.get();
            operations.resume(new OrderPaymentScope(task.tenantId(), intent.callerScopeHash()), intent);
        }
    }

    private OrderTaskKey validate(DelayedTask task) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (task == null || task.tenantId() == null || !taskType().equals(task.taskType())
                || !task.tenantId().equals(TenantUtil.getTenantId())) throw new CommonException(SNAPSHOT_INVALID);
        final OrderTaskKey key;
        try { key = OrderTaskKey.parse(task.bizId()); }
        catch (DomainViolation invalid) { throw new CommonException(SNAPSHOT_INVALID); }
        var nativeTask = tasks.find(taskType(), task.bizId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        var at = LocalDateTime.now(clock);
        if (!task.id().equals(nativeTask.id()) || !task.tenantId().equals(nativeTask.tenantId())
                || !taskType().equals(nativeTask.taskType()) || !task.bizId().equals(nativeTask.bizId())
                || !task.executeAt().equals(nativeTask.executeAt()) || nativeTask.status() != DelayedTaskStatus.RUNNING
                || nativeTask.attempts() != task.attempts() || nativeTask.leaseUntil() == null
                || !at.isBefore(nativeTask.leaseUntil()) || at.isBefore(nativeTask.nextExecuteAt()))
            throw new CommonException(INVALID_STATE);
        return key;
    }
}
