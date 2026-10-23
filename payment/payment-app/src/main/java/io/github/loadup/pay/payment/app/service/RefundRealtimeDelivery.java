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
package io.github.loadup.pay.payment.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.outbox.*;
import io.github.loadup.pay.common.PayEventEnvelope;
import io.github.loadup.pay.payment.app.port.*;
import io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Set;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;

@Component
public class RefundRealtimeDelivery {
    private final OutboxTemplate outbox;
    private final RefundProjectionEnvelopeCodec codec;
    private final PaymentRealtimeClient client;
    public RefundRealtimeDelivery(OutboxTemplate outbox, RefundProjectionEnvelopeCodec codec, PaymentRealtimeClient client) {
        this.outbox = outbox; this.codec = codec; this.client = client;
    }
    public void child(OutboxEvent event) {
        var row = registered(event, RefundProjectionEvents.CHILD);
        var envelope = codec.child(event.message().payload());
        requireAggregate(row, envelope);
        ack(envelope, client.applyRefund(row.tenantId(), envelope));
    }
    public void summary(OutboxEvent event) {
        var row = registered(event, RefundProjectionEvents.SUMMARY);
        var envelope = codec.summary(event.message().payload());
        requireAggregate(row, envelope);
        ack(envelope, client.applyRefundSummary(row.tenantId(), envelope));
    }
    private OutboxRecord registered(OutboxEvent event, String type) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (event == null || event.message() == null || event.message().version() != 1 || !type.equals(event.message().type())
                || !event.message().tenantId().equals(TenantUtil.getTenantId())) throw new CommonException(SNAPSHOT_INVALID);
        var message = event.message();
        var row = outbox.find(message.tenantId(), event.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!row.id().equals(event.id()) || !row.tenantId().equals(message.tenantId()) || !row.type().equals(type)
                || !row.businessId().equals(message.businessId()) || !"PROCESSING".equals(row.status())) throw new CommonException(SNAPSHOT_INVALID);
        if (!client.supportsTenant(row.tenantId())) throw new CommonException(ACTION_FORBIDDEN);
        return row;
    }
    private void requireAggregate(OutboxRecord row, PayEventEnvelope<?> event) {
        if (!row.businessId().equals(event.aggregateId()) || !row.type().equals(event.eventType())) throw new CommonException(SNAPSHOT_INVALID);
    }
    private void ack(PayEventEnvelope<?> event, ProjectionAckDTO ack) {
        if (ack == null || !ack.accepted() || !event.deliveryId().equals(ack.deliveryId()) || !event.businessEventId().equals(ack.businessEventId())
                || !event.producer().equals(ack.producer()) || !event.aggregateId().equals(ack.aggregateId())
                || ack.sourceVersion() != event.aggregateVersion() || ack.projectedVersion() < ack.sourceVersion()
                || ack.disposition() == null || !Set.of("APPLIED", "DUPLICATE", "STALE").contains(ack.disposition()))
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
    }
}
