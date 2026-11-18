/*
 * Copyright (C) 2026 LoadUp Cloud
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
package io.github.loadup.payment.app.dispatch;

import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.task.TaskSubmission;
import io.github.loadup.components.task.TaskTemplate;
import io.github.loadup.contracts.event.BusinessFact;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Fan-out stays inside the aggregate's original local transaction; every target retains its own Outbox lifecycle. */
public final class PaymentFactPublisher {
    private final TaskTemplate outbox;
    private final IdGenerator ids;
    private final PaymentFactRoutes routes;

    public PaymentFactPublisher(TaskTemplate outbox, IdGenerator ids, PaymentFactRoutes routes) {
        this.outbox = outbox;
        this.ids = ids;
        this.routes = routes;
    }

    public void publish(BusinessFact<?> fact) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new CommonException(INVALID_STATE);
        }
        var destinations = routes.forFact(fact.eventType());
        if (!"payment".equals(fact.producer()) || fact.schemaVersion() != 1 || destinations.isEmpty()) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        for (var route : destinations) {
            var delivery = fact.delivery(ids.generate());
            outbox.submit(
                    TaskSubmission.immediate(route.deliveryType(), delivery.deliveryId(), JsonUtil.toJson(delivery)));
        }
    }
}
