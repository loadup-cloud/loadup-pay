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
package io.github.loadup.pay.payment.infrastructure.codec;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.RefundMerchantEventDTO;
import io.github.loadup.pay.payment.client.event.RefundOutcomeEventDTO;
import io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO;
import io.github.loadup.pay.payment.domain.model.RefundState;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundProjectionEnvelopeCodecImplTest {
    final JsonMapper json = JsonUtil.customize(JsonMapper.builder()).build();
    final RefundProjectionEnvelopeCodecImpl codec = new RefundProjectionEnvelopeCodecImpl(json);
    final OffsetDateTime at = OffsetDateTime.parse("2026-10-23T12:00:00.123456+08:00");

    @Test
    void childRetainsOriginalAuditTimeAndChannelTimeWithoutInventingClockOrdering() {
        var data = new RefundSnapshotEventDTO("refund", "request", "payment", "order", "merchant", 1000, "CNY", RefundState.SUCCEEDED,
                at, at.minusNanos(123456000), 2);
        var event = new EventEnvelope<>("delivery", "change", "payment", "pay.payment.refund.realtime.v1", 1, "refund", 2, at, data);
        assertEquals(event, codec.child(json.writeValueAsString(event)));
        assertEquals(at, data.refundCreatedAt());
        assertEquals(at.minusNanos(123456000), data.succeededAt());
    }

    @Test
    void boundedMonetarySummaryRoundTripsWithoutAnyRequestHistory() {
        var data = new OrderRefundSummaryEventDTO("order", "merchant", "CNY", 1000, 500, 3);
        var event = new EventEnvelope<>("delivery", "change", "payment", "pay.payment.refund-summary.realtime.v1", 1, "order", 3, at, data);
        assertEquals(event, codec.summary(json.writeValueAsString(event)));
        assertTrue(json.writeValueAsString(event).length() < 2048);
    }

    @Test
    void aFeeStreamPayloadCannotBeDeliveredAsTheRealtimeSummaryStream() {
        var data = new OrderRefundSummaryEventDTO("order", "merchant", "CNY", 1000, 500, 3);
        var event = new EventEnvelope<>("delivery", "change", "payment", "pay.payment.refund.fee.v1", 1, "order", 3, at, data);
        assertThrows(CommonException.class, () -> codec.summary(json.writeValueAsString(event)));
    }

    @Test
    void feeStreamRoundTripsOnlyThroughTheFeeCodec() {
        var data = new OrderRefundSummaryEventDTO("order", "merchant", "CNY", 1000, 500, 3);
        var event = new EventEnvelope<>("delivery-fee", "change-fee", "payment", "pay.payment.refund.fee.v1", 1, "order", 3, at, data);
        var payload = json.writeValueAsString(event);
        assertEquals(event, codec.fee(payload));
        assertThrows(CommonException.class, () -> codec.summary(payload));

        var realtime = new EventEnvelope<>("delivery-summary", "change-summary", "payment", "pay.payment.refund-summary.realtime.v1", 1, "order", 3, at, data);
        assertEquals(realtime, codec.summary(json.writeValueAsString(realtime)));
        assertThrows(CommonException.class, () -> codec.fee(json.writeValueAsString(realtime)));
    }

    @Test
    void successfulLimitStreamRoundTripsOnlyThroughTheLimitCodec() {
        var data = new RefundOutcomeEventDTO("refund", "request", "payment", "order", "operation", "merchant",
                1000, "CNY", RefundState.SUCCEEDED, 2, 1000, "refund-evidence");
        var event = new EventEnvelope<>("delivery-limit", "change-limit", "payment", "pay.payment.refund.limit.v1",
                1, "refund", 2, at, data);
        var payload = json.writeValueAsString(event);
        assertEquals(event, codec.limit(payload));
        assertThrows(CommonException.class, () -> codec.fee(payload));
        assertThrows(CommonException.class, () -> codec.summary(payload));

        var summary = new OrderRefundSummaryEventDTO("order", "merchant", "CNY", 1000, 500, 3);
        var feeEvent = new EventEnvelope<>("delivery-fee", "change-fee", "payment", "pay.payment.refund.fee.v1",
                1, "order", 3, at, summary);
        var summaryEvent = new EventEnvelope<>("delivery-summary", "change-summary", "payment",
                "pay.payment.refund-summary.realtime.v1", 1, "order", 3, at, summary);
        assertThrows(CommonException.class, () -> codec.limit(json.writeValueAsString(feeEvent)));
        assertThrows(CommonException.class, () -> codec.limit(json.writeValueAsString(summaryEvent)));

        var failed = new RefundOutcomeEventDTO("refund", "request", "payment", "order", "operation", "merchant",
                1000, "CNY", RefundState.FAILED, 2, 0, "refund-evidence");
        var failedEvent = new EventEnvelope<>("delivery-failed", "change-failed", "payment", "pay.payment.refund.limit.v1",
                1, "refund", 2, at, failed);
        assertThrows(CommonException.class, () -> codec.limit(json.writeValueAsString(failedEvent)));
    }

    @Test
    void invalidVersionUnknownFieldsAndOversizedPayloadAreRejected() {
        var data = new OrderRefundSummaryEventDTO("order", "merchant", "CNY", 1000, 500, 3);
        var wrong = new EventEnvelope<>("delivery", "change", "payment", "pay.payment.refund-summary.realtime.v1", 1, "order", 4, at, data);
        assertThrows(CommonException.class, () -> codec.summary(json.writeValueAsString(wrong)));
        var valid = new EventEnvelope<>("delivery", "change", "payment", "pay.payment.refund-summary.realtime.v1", 1, "order", 3, at, data);
        var payload = json.writeValueAsString(valid);
        assertThrows(CommonException.class, () -> codec.summary(payload.substring(0, payload.length() - 1) + ",\"requests\":[]}"));
        assertThrows(CommonException.class, () -> codec.child("x".repeat(65537)));
    }

    @Test
    void merchantStreamPreservesExactChildAndRequestIdentitiesWithoutInventingCrossClockOrdering() {
        var child = new RefundMerchantEventDTO("RefundOrder", "refund", "request", "payment", "order", "merchant", "R001",
                1000, "CNY", "SUCCEEDED", 1000, 0, 9007199254740993L, at.plusSeconds(1));
        var childEvent = new EventEnvelope<>("delivery", "change", "payment", RefundMerchantEventDTO.TYPE, 1,
                "refund", child.sourceVersion(), at, child);
        assertEquals(childEvent, codec.merchant(json.writeValueAsString(childEvent)));
        var request = new RefundMerchantEventDTO("RefundRequest", null, "request", null, "order", "merchant", "R001",
                1500, "CNY", "PARTIAL_SUCCEEDED", 1000, 500, 5, null);
        var requestEvent = new EventEnvelope<>("request-delivery", "request-change", "payment", RefundMerchantEventDTO.TYPE, 1, "request", 5, at, request);
        assertEquals(requestEvent, codec.merchant(json.writeValueAsString(requestEvent)));
        assertThrows(CommonException.class, () -> codec.limit(json.writeValueAsString(childEvent)));
    }

    @Test
    void merchantCodecRejectsMismatchedSourceDuplicateFieldsAndTrailingDocuments() {
        var data = new RefundMerchantEventDTO("RefundOrder", "refund", "request", "payment", "order", "merchant", "R001",
                1000, "CNY", "FAILED", 0, 1000, 3, null);
        var event = new EventEnvelope<>("delivery", "change", "payment", RefundMerchantEventDTO.TYPE, 1, "refund", 3, at, data);
        String payload = json.writeValueAsString(event);
        assertThrows(CommonException.class, () -> codec.merchant(payload + " {}"));
        assertThrows(CommonException.class, () -> codec.merchant(payload.replace("\"producer\":\"payment\"", "\"producer\":\"payment\",\"producer\":\"payment\"")));
        var wrong = new EventEnvelope<>("delivery", "change", "payment", RefundMerchantEventDTO.TYPE, 1, "refund", 4, at, data);
        assertThrows(CommonException.class, () -> codec.merchant(json.writeValueAsString(wrong)));
    }
}
