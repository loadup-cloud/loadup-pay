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
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import io.github.loadup.pay.acquire.domain.model.AcquireOrder;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.OrderTaskKey;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;

/**
 * The existing order header owns deadlineVersion; native tasks only wake its due check.
 */
@Component
public class OrderDeadlineTasks {
    public static final String TYPE = "pay.acquire.order.deadline";
    private final DelayedTaskTemplate tasks;

    public OrderDeadlineTasks(DelayedTaskTemplate tasks) {
        this.tasks = tasks;
    }

    public void register(String tenant, AcquireOrder order, long deadlineVersion) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        if (deadlineVersion < 1 || order.state() != AcquireOrderState.INIT && order.state() != AcquireOrderState.PENDING)
            throw new CommonException(INVALID_STATE);
        tasks.register(TYPE, new OrderTaskKey(order.id(), deadlineVersion).bizId(), order.orderDueAt());
    }
}
