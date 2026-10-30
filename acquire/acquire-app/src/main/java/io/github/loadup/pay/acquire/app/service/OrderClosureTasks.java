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
import io.github.loadup.pay.acquire.domain.model.OrderCloseIntent;
import io.github.loadup.pay.acquire.domain.model.OrderTaskKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;

/**
 * The immutable close request and its scan rowVersion own all continuation identities.
 */
@Component
public class OrderClosureTasks {
    public static final String TYPE = "pay.acquire.order.close.progress";
    private final DelayedTaskTemplate tasks;
    private final long pollDelaySeconds;

    public OrderClosureTasks(DelayedTaskTemplate tasks,
                             @Value("${loadup.modules.acquire.order-close.poll-delay-seconds:30}") long pollDelaySeconds) {
        DomainViolation.require(pollDelaySeconds >= 1 && pollDelaySeconds <= 86400, DomainViolation.Reason.INVALID_ARGUMENT);
        this.tasks = tasks;
        this.pollDelaySeconds = pollDelaySeconds;
    }

    public void register(String tenant, OrderCloseIntent intent) {
        writable(tenant);
        if (intent.rowVersion() != 0L || !intent.cursorPaymentId().isEmpty()) {
            throw new CommonException(INVALID_STATE);
        }
        tasks.register(TYPE, new OrderTaskKey(intent.orderId(), intent.rowVersion()).bizId(), intent.requestedAt());
    }

    void schedule(String tenant, OrderCloseIntent advanced, LocalDateTime at) {
        writable(tenant);
        if (advanced.rowVersion() < 1) {
            throw new CommonException(INVALID_STATE);
        }
        tasks.register(TYPE, new OrderTaskKey(advanced.orderId(), advanced.rowVersion()).bizId(),
                at.truncatedTo(ChronoUnit.MICROS).plusSeconds(pollDelaySeconds));
    }

    private void writable(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new CommonException(INVALID_STATE);
        }
    }
}
