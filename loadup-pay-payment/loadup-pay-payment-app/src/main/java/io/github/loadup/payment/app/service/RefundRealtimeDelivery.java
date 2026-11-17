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
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.components.task.TaskExecution;
import io.github.loadup.components.task.TaskStatus;
import io.github.loadup.components.task.TaskTemplate;
import io.github.loadup.contracts.event.FrozenEventTypes;
import io.github.loadup.payment.app.dispatch.PaymentDeliveryRoutes;
import io.github.loadup.payment.app.model.realtime.dto.ProjectionAckDTO;
import io.github.loadup.payment.app.port.PaymentRealtimeClient;
import io.github.loadup.payment.app.port.RefundProjectionEnvelopeCodec;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class RefundRealtimeDelivery {
    private final TaskTemplate outbox;
    private final RefundProjectionEnvelopeCodec codec;
    private final PaymentRealtimeClient client;

    public RefundRealtimeDelivery(
            TaskTemplate outbox, RefundProjectionEnvelopeCodec codec, PaymentRealtimeClient client) {
        this.outbox = outbox;
        this.codec = codec;
        this.client = client;
    }

    public void child(TaskExecution event) {
        var row = registered(event, PaymentDeliveryRoutes.REFUND_CHILD);
        var envelope = codec.child(event.payload());
        requireAggregate(row, envelope);
        ack(envelope, client.applyRefund(envelope));
    }

    public void summary(TaskExecution event) {
        var row = registered(event, PaymentDeliveryRoutes.REFUND_SUMMARY);
        var envelope = codec.summary(event.payload());
        requireAggregate(row, envelope);
        ack(envelope, client.applyRefundSummary(envelope));
    }

    private TaskExecution registered(TaskExecution event, String type) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (event == null || event.payloadVersion() != 1 || !type.equals(event.taskType())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var message = event;
        var row = outbox.find(event.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!row.id().equals(event.id())
                || !row.taskType().equals(type)
                || !row.bizId().equals(message.bizId())
                || row.status() != TaskStatus.RUNNING) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return row;
    }

    private void requireAggregate(TaskExecution row, EventEnvelope<?> event) {
        if (!row.bizId().equals(event.aggregateId())
                || !FrozenEventTypes.accepts(
                        PaymentDeliveryRoutes.factType(row.taskType()), row.taskType(), event.eventType())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private void ack(EventEnvelope<?> event, ProjectionAckDTO ack) {
        if (ack == null
                || !ack.accepted()
                || !event.deliveryId().equals(ack.deliveryId())
                || !event.businessEventId().equals(ack.businessEventId())
                || !event.producer().equals(ack.producer())
                || !event.aggregateId().equals(ack.aggregateId())
                || ack.sourceVersion() != event.aggregateVersion()
                || ack.projectedVersion() < ack.sourceVersion()
                || ack.disposition() == null
                || !Set.of("APPLIED", "DUPLICATE", "STALE").contains(ack.disposition())) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
