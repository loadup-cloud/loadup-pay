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
package io.github.loadup.payment.app.service;

import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.components.task.TaskExecution;
import io.github.loadup.components.task.TaskStatus;
import io.github.loadup.components.task.TaskTemplate;
import io.github.loadup.contracts.event.FrozenEventTypes;
import io.github.loadup.payment.app.dispatch.PaymentDeliveryRoutes;
import io.github.loadup.payment.app.port.PaymentAcquireClient;
import io.github.loadup.payment.app.port.RefundProjectionEnvelopeCodec;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Independent Outbox target; acknowledgement means durable registration in acquire.
 */
@Component
public class RefundMerchantDelivery {
    private final TaskTemplate outbox;
    private final RefundProjectionEnvelopeCodec codec;
    private final PaymentAcquireClient acquire;

    public RefundMerchantDelivery(
            TaskTemplate outbox, RefundProjectionEnvelopeCodec codec, PaymentAcquireClient acquire) {
        this.outbox = outbox;
        this.codec = codec;
        this.acquire = acquire;
    }

    public void deliver(TaskExecution event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (event == null || !PaymentDeliveryRoutes.REFUND_MERCHANT.equals(event.taskType())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var message = event;
        var row = outbox.find(event.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!row.id().equals(event.id())
                || !message.taskType().equals(row.taskType())
                || !message.bizId().equals(row.bizId())
                || row.status() != TaskStatus.RUNNING) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var envelope = codec.merchant(message.payload());
        if (!row.bizId().equals(envelope.deliveryId())
                || !FrozenEventTypes.accepts(
                        PaymentDeliveryRoutes.factType(row.taskType()), row.taskType(), envelope.eventType())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var data = envelope.data();
        data.requireEnvelope(envelope);
        String expectedEventId = DigestUtils.sha256(JsonUtil.toJson(List.of(
                "pay-merchant-event-v1",
                data.merchantEventType(),
                envelope.aggregateId(),
                envelope.aggregateVersion())));
        var ack = acquire.acceptRefundNotification(envelope);
        if (ack == null
                || !envelope.deliveryId().equals(ack.deliveryId())
                || !envelope.businessEventId().equals(ack.businessEventId())
                || !envelope.aggregateId().equals(ack.aggregateId())
                || envelope.aggregateVersion() != ack.sourceVersion()
                || !expectedEventId.equals(ack.merchantEventId())
                || !Set.of("ENQUEUED", "DISABLED").contains(ack.disposition())) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
