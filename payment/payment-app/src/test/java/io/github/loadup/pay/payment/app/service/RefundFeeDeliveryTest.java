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
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.outbox.OutboxEvent;
import io.github.loadup.components.outbox.OutboxMessage;
import io.github.loadup.components.outbox.OutboxRecord;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.pay.acquire.client.dto.FeeRefundAckDTO;
import io.github.loadup.pay.payment.app.port.PaymentAcquireClient;
import io.github.loadup.pay.payment.app.port.RefundProjectionEnvelopeCodec;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundFeeDeliveryTest {
    final OutboxTemplate outbox = mock(OutboxTemplate.class);
    final RefundProjectionEnvelopeCodec codec = mock(RefundProjectionEnvelopeCodec.class);
    final PaymentAcquireClient acquire = mock(PaymentAcquireClient.class);
    final RefundFeeDelivery delivery = new RefundFeeDelivery(outbox, codec, acquire);
    final EventEnvelope<OrderRefundSummaryEventDTO> envelope = new EventEnvelope<>("delivery-fee", "business-fee", "payment",
            RefundProjectionEvents.FEE, 1, "order", 3, BusinessTime.external(RefundResultFixtures.NOW),
            new OrderRefundSummaryEventDTO("order", "merchant", "CNY", 1000, 500, 3));
    final OutboxEvent event = new OutboxEvent("queue-fee",
            new OutboxMessage(RefundProjectionEvents.FEE, 1, "order", "fee-payload"),
            RefundResultFixtures.NOW.atZone(BusinessTime.zone()).toInstant(), null);

    @BeforeEach
    void setup() {
        when(outbox.find("queue-fee")).thenReturn(Optional.of(
                new OutboxRecord("queue-fee", RefundProjectionEvents.FEE, "order", "PROCESSING", 1, null)));
        when(codec.fee("fee-payload")).thenReturn(envelope);
        when(acquire.supportsTenant("tenant")).thenReturn(true);
        when(acquire.acceptFeeRefund("tenant", envelope)).thenReturn(new FeeRefundAckDTO("delivery-fee", "business-fee", "order", 3, 4, true));
    }

    void deliver() {
        TenantUtil.runWithTenant("tenant", () -> delivery.deliver(event));
    }

    @Test
    void retriesReplayTheOriginalFeeSnapshotAndRequireTheMatchingAcknowledgement() {
        deliver();
        deliver();
        verify(acquire, times(2)).acceptFeeRefund("tenant", envelope);

        when(acquire.acceptFeeRefund("tenant", envelope)).thenReturn(new FeeRefundAckDTO("other", "business-fee", "order", 3, 4, true));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, this::deliver).getResultCode());
        when(acquire.acceptFeeRefund("tenant", envelope)).thenReturn(new FeeRefundAckDTO("delivery-fee", "other", "order", 3, 4, true));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, this::deliver).getResultCode());
        when(acquire.acceptFeeRefund("tenant", envelope)).thenReturn(new FeeRefundAckDTO("delivery-fee", "business-fee", "other-order", 3, 4, true));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, this::deliver).getResultCode());
        when(acquire.acceptFeeRefund("tenant", envelope)).thenReturn(new FeeRefundAckDTO("delivery-fee", "business-fee", "order", 2, 4, true));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, this::deliver).getResultCode());
    }

    @Test
    void unclaimedQueueAndWrongTenantCannotCallAcquire() {
        assertThrows(CommonException.class, () -> TenantUtil.runWithTenant("other", () -> delivery.deliver(event)));
        when(outbox.find("queue-fee")).thenReturn(Optional.empty());
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, this::deliver).getResultCode());
        when(outbox.find("queue-fee")).thenReturn(Optional.of(
                new OutboxRecord("queue-fee", RefundProjectionEvents.FEE, "order", "PENDING", 1, null)));
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, this::deliver).getResultCode());
        verify(acquire, never()).acceptFeeRefund(anyString(), any());
    }
}
