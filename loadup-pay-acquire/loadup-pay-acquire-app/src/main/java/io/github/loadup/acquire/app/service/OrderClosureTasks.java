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

import io.github.loadup.acquire.domain.model.OrderCloseIntent;
import io.github.loadup.acquire.domain.model.OrderTaskKey;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.components.task.TaskSubmission;
import io.github.loadup.components.task.TaskTemplate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** The immutable close request and its scan rowVersion own all continuation identities. */
@Component
public class OrderClosureTasks {
    public static final String TYPE = "pay.acquire.order.close.progress";
    private final TaskTemplate tasks;
    private final long pollDelaySeconds;

    public OrderClosureTasks(
            TaskTemplate tasks,
            @Value("${loadup.modules.acquire.order-close.poll-delay-seconds:30}") long pollDelaySeconds) {
        DomainViolation.require(
                pollDelaySeconds >= 1 && pollDelaySeconds <= 86400, DomainViolation.Reason.INVALID_ARGUMENT);
        this.tasks = tasks;
        this.pollDelaySeconds = pollDelaySeconds;
    }

    public void register(OrderCloseIntent intent) {
        writable();
        if (intent.rowVersion() != 0L || !intent.cursorPaymentId().isEmpty()) {
            throw new CommonException(INVALID_STATE);
        }
        tasks.submit(TaskSubmission.scheduled(
                TYPE, new OrderTaskKey(intent.orderId(), intent.rowVersion()).bizId(), intent.requestedAt()));
    }

    void schedule(OrderCloseIntent advanced, LocalDateTime at) {
        writable();
        if (advanced.rowVersion() < 1) {
            throw new CommonException(INVALID_STATE);
        }
        tasks.submit(TaskSubmission.scheduled(
                TYPE,
                new OrderTaskKey(advanced.orderId(), advanced.rowVersion()).bizId(),
                at.truncatedTo(ChronoUnit.MICROS).plusSeconds(pollDelaySeconds)));
    }

    private void writable() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new CommonException(INVALID_STATE);
        }
    }
}
