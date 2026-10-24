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
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.outbox.OutboxEvent;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.pay.payment.app.port.PaymentAcquireClient;
import io.github.loadup.pay.payment.app.port.RefundProjectionEnvelopeCodec;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;

/** Independent Outbox target; acknowledgement means durable registration in acquire. */
@Component
public class RefundMerchantDelivery {
    private final OutboxTemplate outbox;
    private final RefundProjectionEnvelopeCodec codec;
    private final PaymentAcquireClient acquire;

    public RefundMerchantDelivery(OutboxTemplate outbox, RefundProjectionEnvelopeCodec codec, PaymentAcquireClient acquire) {
        this.outbox = outbox; this.codec = codec; this.acquire = acquire;
    }

    public void deliver(OutboxEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (event == null || event.message() == null || event.message().version() != 1
                || !RefundProjectionEvents.MERCHANT.equals(event.message().type())
                || !event.message().tenantId().equals(TenantUtil.getTenantId())) throw new CommonException(SNAPSHOT_INVALID);
        var message = event.message();
        var row = outbox.find(message.tenantId(), event.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!row.id().equals(event.id()) || !row.tenantId().equals(message.tenantId())
                || !message.type().equals(row.type()) || !message.businessId().equals(row.businessId())
                || !"PROCESSING".equals(row.status())) throw new CommonException(SNAPSHOT_INVALID);
        if (!acquire.supportsTenant(row.tenantId())) throw new CommonException(ACTION_FORBIDDEN);
        var envelope = codec.merchant(message.payload());
        if (!row.businessId().equals(envelope.aggregateId()) || !row.type().equals(envelope.eventType()))
            throw new CommonException(SNAPSHOT_INVALID);
        var data = envelope.data();
        data.requireEnvelope(envelope);
        String expectedEventId = DigestUtils.sha256(JsonUtil.toJson(List.of("pay-merchant-event-v1", row.tenantId(),
                data.merchantEventType(), envelope.aggregateId(), envelope.aggregateVersion())));
        var ack = acquire.acceptRefundNotification(row.tenantId(), envelope);
        if (ack == null || !envelope.deliveryId().equals(ack.deliveryId())
                || !envelope.businessEventId().equals(ack.businessEventId()) || !envelope.aggregateId().equals(ack.aggregateId())
                || envelope.aggregateVersion() != ack.sourceVersion() || !expectedEventId.equals(ack.merchantEventId())
                || !java.util.Set.of("ENQUEUED", "DISABLED").contains(ack.disposition()))
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
    }
}
