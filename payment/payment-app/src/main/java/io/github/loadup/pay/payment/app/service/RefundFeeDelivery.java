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
package io.github.loadup.pay.payment.app.service;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.outbox.OutboxEvent;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.pay.payment.app.port.PaymentAcquireClient;
import io.github.loadup.pay.payment.app.port.RefundProjectionEnvelopeCodec;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Independent financial target; a successful realtime delivery cannot acknowledge this target.
 */
@Component
public class RefundFeeDelivery {
    private final OutboxTemplate outbox;
    private final RefundProjectionEnvelopeCodec codec;
    private final PaymentAcquireClient acquire;

    public RefundFeeDelivery(OutboxTemplate outbox, RefundProjectionEnvelopeCodec codec, PaymentAcquireClient acquire) {
        this.outbox = outbox;
        this.codec = codec;
        this.acquire = acquire;
    }

    public void deliver(OutboxEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (event == null
                || event.message() == null
                || event.message().version() != 1
                || !RefundProjectionEvents.FEE.equals(event.message().type())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var message = event.message();
        String tenant = TenantUtil.getTenantId();
        var row = outbox.find(event.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!row.id().equals(event.id())
                || !RefundProjectionEvents.FEE.equals(row.type())
                || !row.businessId().equals(message.businessId())
                || !"PROCESSING".equals(row.status())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var envelope = codec.fee(message.payload());
        if (!row.businessId().equals(envelope.aggregateId())
                || !row.type().equals(envelope.eventType())
                || envelope.aggregateVersion() <= 0) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var ack = acquire.acceptFeeRefund(envelope);
        if (ack == null
                || !ack.accepted()
                || !envelope.deliveryId().equals(ack.deliveryId())
                || !envelope.businessEventId().equals(ack.businessEventId())
                || !envelope.aggregateId().equals(ack.orderId())
                || ack.sourceVersion() != envelope.aggregateVersion()
                || ack.appliedVersion() < ack.sourceVersion()) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
