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
package io.github.loadup.pay.payment.client;

import io.github.loadup.pay.common.DomainViolation;
import io.github.loadup.pay.common.PayEventEnvelope;
import io.github.loadup.pay.payment.client.dto.RefundRequestSummaryDTO;
import io.github.loadup.pay.payment.client.enums.PaymentState;
import io.github.loadup.pay.payment.client.enums.RefundRequestState;
import io.github.loadup.pay.payment.client.enums.RefundState;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO;
import io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentEventContractTest {

    @Test
    void definitiveNoCollectionNeedsBothEvidenceAndExecutionFence() {
        assertThrows(
                DomainViolation.class,
                () -> new PaymentOutcomeEventDTO(
                        "payment",
                        "order",
                        "allocation",
                        "operation",
                        "merchant",
                        100,
                        "CNY",
                        PaymentState.FAILED,
                        2,
                        "evidence",
                        false));
        assertThrows(
                DomainViolation.class,
                () -> new PaymentOutcomeEventDTO(
                        "payment",
                        "order",
                        "allocation",
                        "operation",
                        "merchant",
                        100,
                        "CNY",
                        PaymentState.SUCCEEDED,
                        2,
                        null,
                        false));
        var event = new PaymentOutcomeEventDTO(
                "payment",
                "order",
                "allocation",
                "operation",
                "merchant",
                100,
                "CNY",
                PaymentState.UNKNOWN,
                2,
                null,
                false);
        assertEquals(PaymentState.UNKNOWN, event.state());
    }

    @Test
    void refundProgressSnapshotIsCompleteAndItsTotalsCannotBeForgedOrDoubleCounted() {
        var request = new RefundRequestSummaryDTO(
                "request", "merchant-refund", 100, RefundRequestState.PROCESSING, 40, 20, 1, 3);
        var mutable = new ArrayList<>(List.of(request));
        var event = new OrderRefundSummaryEventDTO("order", "merchant", "CNY", 40, 40, 2, mutable);
        mutable.clear();
        assertEquals(1, event.requests().size());
        assertThrows(
                DomainViolation.class,
                () -> new OrderRefundSummaryEventDTO("order", "merchant", "CNY", 41, 40, 2, List.of(request)));
        assertThrows(
                DomainViolation.class,
                () -> new OrderRefundSummaryEventDTO("order", "merchant", "CNY", 80, 80, 2, List.of(request, request)));
        assertThrows(
                DomainViolation.class,
                () -> new RefundRequestSummaryDTO(
                        "request", "merchant-refund", 100, RefundRequestState.SUCCEEDED, 40, 0, 1, 3));
    }

    @Test
    void projectionSnapshotsBindHeaderIdentityVersionAndLocalTimezone() {
        var created = OffsetDateTime.parse("2026-10-21T02:00:00Z");
        var event = new RefundSnapshotEventDTO(
                "refund",
                "request",
                "payment",
                "order",
                "merchant",
                100,
                "CNY",
                RefundState.SUCCEEDED,
                created,
                created.plusMinutes(1),
                3);
        assertEquals(ZoneOffset.ofHours(8), event.succeededAt().getOffset());
        var envelope = new PayEventEnvelope<>(
                "delivery",
                "business-event",
                "payment",
                "pay.payment.refund.realtime.v1",
                1,
                "refund",
                3,
                created,
                event);
        assertDoesNotThrow(() -> event.requireEnvelope(envelope));
        assertThrows(
                DomainViolation.class,
                () -> event.requireEnvelope(new PayEventEnvelope<>(
                        "delivery",
                        "business-event",
                        "payment",
                        "pay.payment.refund.realtime.v1",
                        1,
                        "other-refund",
                        3,
                        created,
                        event)));
        assertThrows(
                DomainViolation.class,
                () -> event.requireEnvelope(new PayEventEnvelope<>(
                        "delivery",
                        "business-event",
                        "payment",
                        "pay.payment.refund.realtime.v1",
                        2,
                        "refund",
                        3,
                        created,
                        event)));
    }
}
