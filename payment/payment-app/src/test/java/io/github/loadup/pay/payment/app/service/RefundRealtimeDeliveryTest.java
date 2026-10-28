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
import io.github.loadup.pay.payment.app.port.PaymentRealtimeClient;
import io.github.loadup.pay.payment.app.port.RefundProjectionEnvelopeCodec;
import io.github.loadup.pay.payment.client.enums.RefundState;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO;
import io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundRealtimeDeliveryTest {
    final OutboxTemplate outbox = mock(OutboxTemplate.class);
    final RefundProjectionEnvelopeCodec codec = mock(RefundProjectionEnvelopeCodec.class);
    final PaymentRealtimeClient client = mock(PaymentRealtimeClient.class);
    final RefundRealtimeDelivery delivery = new RefundRealtimeDelivery(outbox, codec, client);
    final EventEnvelope<RefundSnapshotEventDTO> child = new EventEnvelope<>("delivery-child", "change-child", "payment", RefundProjectionEvents.CHILD,
            1, "refund", 2, BusinessTime.external(RefundResultFixtures.NOW), new RefundSnapshotEventDTO("refund", "request", "payment", "order", "merchant",
            1000, "CNY", RefundState.SUCCEEDED, BusinessTime.external(RefundResultFixtures.NOW.minusSeconds(30)), BusinessTime.external(RefundResultFixtures.NOW), 2));
    final EventEnvelope<OrderRefundSummaryEventDTO> summary = new EventEnvelope<>("delivery-summary", "change-summary", "payment", RefundProjectionEvents.SUMMARY,
            1, "order", 3, BusinessTime.external(RefundResultFixtures.NOW), new OrderRefundSummaryEventDTO("order", "merchant", "CNY", 1000, 500, 3));
    final OutboxEvent childEvent = event("child", RefundProjectionEvents.CHILD, "refund");
    final OutboxEvent summaryEvent = event("summary", RefundProjectionEvents.SUMMARY, "order");

    OutboxEvent event(String queue, String type, String aggregate) {
        return new OutboxEvent(queue, new OutboxMessage("tenant", type, 1, aggregate, queue + "-payload"), RefundResultFixtures.NOW.atZone(BusinessTime.zone()).toInstant(), null);
    }

    @BeforeEach
    void setup() {
        when(outbox.find("tenant", "child")).thenReturn(Optional.of(new OutboxRecord("child", "tenant", RefundProjectionEvents.CHILD, "refund", "PROCESSING", 1, null)));
        when(outbox.find("tenant", "summary")).thenReturn(Optional.of(new OutboxRecord("summary", "tenant", RefundProjectionEvents.SUMMARY, "order", "PROCESSING", 1, null)));
        when(client.supportsTenant("tenant")).thenReturn(true);
        when(codec.child("child-payload")).thenReturn(child);
        when(codec.summary("summary-payload")).thenReturn(summary);
        when(client.applyRefund("tenant", child)).thenReturn(new ProjectionAckDTO("delivery-child", "change-child", "payment", "refund", 2, 2, "APPLIED", true));
        when(client.applyRefundSummary("tenant", summary)).thenReturn(new ProjectionAckDTO("delivery-summary", "change-summary", "payment", "order", 3, 3, "APPLIED", true));
    }

    void child() {
        TenantUtil.runWithTenant("tenant", () -> delivery.child(childEvent));
    }

    void summary() {
        TenantUtil.runWithTenant("tenant", () -> delivery.summary(summaryEvent));
    }

    @Test
    void eachTargetReplaysItsOriginalCompleteSnapshotAndIdentity() {
        child();
        summary();
        child();
        verify(client, times(2)).applyRefund("tenant", child);
        verify(client).applyRefundSummary("tenant", summary);
    }

    @Test
    void failureOfTheChildDoesNotPreventTheIndependentSummaryTarget() {
        doThrow(new CommonException(DEPENDENCY_UNAVAILABLE)).when(client).applyRefund(anyString(), any());
        assertThrows(CommonException.class, this::child);
        assertDoesNotThrow(this::summary);
    }

    @Test
    void aStaleSummaryAckMustStillMatchTheOriginalIncomingVersionAndIdentity() {
        when(client.applyRefundSummary("tenant", summary)).thenReturn(new ProjectionAckDTO("delivery-summary", "change-summary", "payment", "order", 3, 7, "STALE", true));
        assertDoesNotThrow(this::summary);
        when(client.applyRefundSummary("tenant", summary)).thenReturn(new ProjectionAckDTO("other", "change-summary", "payment", "order", 3, 7, "STALE", true));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, this::summary).getResultCode());
    }

    @Test
    void wrongTenantAndUnclaimedQueueCannotMakeTheNetworkCall() {
        assertThrows(CommonException.class, () -> TenantUtil.runWithTenant("other", () -> delivery.child(childEvent)));
        when(outbox.find("tenant", "child")).thenReturn(Optional.empty());
        assertThrows(CommonException.class, this::child);
        verify(client, never()).applyRefund(anyString(), any());
    }
}
