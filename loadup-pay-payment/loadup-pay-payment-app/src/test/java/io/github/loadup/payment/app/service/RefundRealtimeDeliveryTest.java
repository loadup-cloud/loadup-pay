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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.components.task.TaskExecution;
import io.github.loadup.components.task.TaskSubmission;
import io.github.loadup.components.task.TaskTemplate;
import io.github.loadup.contracts.event.payment.OrderRefundSummaryEventDTO;
import io.github.loadup.contracts.event.payment.RefundSnapshotEventDTO;
import io.github.loadup.contracts.event.payment.RefundState;
import io.github.loadup.payment.app.dispatch.PaymentDeliveryRoutes;
import io.github.loadup.payment.app.model.realtime.dto.ProjectionAckDTO;
import io.github.loadup.payment.app.port.PaymentRealtimeClient;
import io.github.loadup.payment.app.port.RefundProjectionEnvelopeCodec;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RefundRealtimeDeliveryTest {
    final TaskTemplate outbox = mock(TaskTemplate.class);
    final RefundProjectionEnvelopeCodec codec = mock(RefundProjectionEnvelopeCodec.class);
    final PaymentRealtimeClient client = mock(PaymentRealtimeClient.class);
    final RefundRealtimeDelivery delivery = new RefundRealtimeDelivery(outbox, codec, client);
    final EventEnvelope<RefundSnapshotEventDTO> child = new EventEnvelope<>(
            "delivery-child",
            "change-child",
            "payment",
            PaymentDeliveryRoutes.REFUND_CHILD,
            1,
            "refund",
            2,
            BusinessTime.external(RefundResultFixtures.NOW),
            new RefundSnapshotEventDTO(
                    "refund",
                    "request",
                    "payment",
                    "order",
                    "merchant",
                    1000,
                    "CNY",
                    RefundState.SUCCEEDED,
                    BusinessTime.external(RefundResultFixtures.NOW.minusSeconds(30)),
                    BusinessTime.external(RefundResultFixtures.NOW),
                    2));
    final EventEnvelope<OrderRefundSummaryEventDTO> summary = new EventEnvelope<>(
            "delivery-summary",
            "change-summary",
            "payment",
            PaymentDeliveryRoutes.REFUND_SUMMARY,
            1,
            "order",
            3,
            BusinessTime.external(RefundResultFixtures.NOW),
            new OrderRefundSummaryEventDTO("order", "merchant", "CNY", 1000, 500, 3));
    final TaskExecution childEvent = event("child", PaymentDeliveryRoutes.REFUND_CHILD, "delivery-child");
    final TaskExecution summaryEvent = event("summary", PaymentDeliveryRoutes.REFUND_SUMMARY, "delivery-summary");

    TaskExecution event(String queue, String type, String deliveryId) {
        return new TaskExecution(
                queue,
                new TaskSubmission(type, deliveryId, queue + "-payload", LocalDateTime.now()),
                RefundResultFixtures.NOW.atZone(BusinessTime.zone()).toInstant(),
                null);
    }

    @BeforeEach
    void setup() {
        when(outbox.find("child"))
                .thenReturn(Optional.of(new TaskExecution(
                        "child", PaymentDeliveryRoutes.REFUND_CHILD, "delivery-child", "RUNNING", 1, null)));
        when(outbox.find("summary"))
                .thenReturn(Optional.of(new TaskExecution(
                        "summary", PaymentDeliveryRoutes.REFUND_SUMMARY, "delivery-summary", "RUNNING", 1, null)));
        when(codec.child("child-payload")).thenReturn(child);
        when(codec.summary("summary-payload")).thenReturn(summary);
        when(client.applyRefund(child))
                .thenReturn(new ProjectionAckDTO(
                        "delivery-child", "change-child", "payment", "refund", 2, 2, "APPLIED", true));
        when(client.applyRefundSummary(summary))
                .thenReturn(new ProjectionAckDTO(
                        "delivery-summary", "change-summary", "payment", "order", 3, 3, "APPLIED", true));
    }

    void child() {
        delivery.child(childEvent);
    }

    void summary() {
        delivery.summary(summaryEvent);
    }

    @Test
    void eachTargetReplaysItsOriginalCompleteSnapshotAndIdentity() {
        child();
        summary();
        child();
        verify(client, times(2)).applyRefund(child);
        verify(client).applyRefundSummary(summary);
    }

    @Test
    void failureOfTheChildDoesNotPreventTheIndependentSummaryTarget() {
        doThrow(new CommonException(DEPENDENCY_UNAVAILABLE)).when(client).applyRefund(any());
        assertThrows(CommonException.class, this::child);
        assertDoesNotThrow(this::summary);
    }

    @Test
    void aStaleSummaryAckMustStillMatchTheOriginalIncomingVersionAndIdentity() {
        when(client.applyRefundSummary(summary))
                .thenReturn(new ProjectionAckDTO(
                        "delivery-summary", "change-summary", "payment", "order", 3, 7, "STALE", true));
        assertDoesNotThrow(this::summary);
        when(client.applyRefundSummary(summary))
                .thenReturn(new ProjectionAckDTO("other", "change-summary", "payment", "order", 3, 7, "STALE", true));
        assertSame(
                DEPENDENCY_UNAVAILABLE,
                assertThrows(CommonException.class, this::summary).getResultCode());
    }

    @Test
    void unclaimedQueueCannotMakeTheNetworkCall() {
        when(outbox.find("child")).thenReturn(Optional.empty());
        assertThrows(CommonException.class, this::child);
        verify(client, never()).applyRefund(any());
    }
}
