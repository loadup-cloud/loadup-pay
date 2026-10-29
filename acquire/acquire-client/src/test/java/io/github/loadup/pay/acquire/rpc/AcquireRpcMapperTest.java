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
package io.github.loadup.pay.acquire.rpc;

import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.acquire.rpc.proto.WirePayEventEnvelopeOfRefundMerchantEventDTO;
import io.github.loadup.pay.payment.client.event.RefundMerchantEventDTO;
import io.github.loadup.pay.payment.rpc.PaymentRpcMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AcquireRpcMapperTest {
    private static EventEnvelope<RefundMerchantEventDTO> envelope(String aggregateId, long version,
                                                                  RefundMerchantEventDTO data) {
        return new EventEnvelope<>("delivery", "business", "payment", RefundMerchantEventDTO.TYPE, 1,
                aggregateId, version,
                BusinessTime.external(LocalDateTime.of(2026, 10, 23, 12, 0, 0, 123456000)), data);
    }

    @Test
    void childSuccessBinaryRoundTripPreservesNullableFieldsAndExactVersion() throws Exception {
        long exact = 9007199254740993L;
        var data = new RefundMerchantEventDTO("RefundOrder", "refund", "request", "payment", "order",
                "merchant", "merchant-refund", 120, "CNY", "SUCCEEDED", 120, 0, exact,
                OffsetDateTime.parse("2026-10-23T12:00:00.123456Z"));
        var envelope = envelope("refund", exact, data);

        var wire = AcquireRpcMapper.INSTANCE.toWirePayEventEnvelopeOfRefundMerchantEventDTO(envelope);
        var restored = AcquireRpcMapper.INSTANCE.fromWirePayEventEnvelopeOfRefundMerchantEventDTO(
                WirePayEventEnvelopeOfRefundMerchantEventDTO.parseFrom(wire.toByteArray()));

        assertEquals(envelope, restored);
        assertTrue(wire.getData().hasRefundId());
        assertTrue(wire.getData().hasPaymentId());
        assertEquals(exact, restored.aggregateVersion());
    }

    @Test
    void completedPartialRequestBinaryRoundTripPreservesAbsentChildIds() throws Exception {
        long exact = 9007199254740995L;
        var data = new RefundMerchantEventDTO("RefundRequest", null, "request", null, "order",
                "merchant", "merchant-refund", 300, "CNY", "PARTIAL_SUCCEEDED", 100, 200, exact, null);
        var envelope = envelope("request", exact, data);

        var wire = AcquireRpcMapper.INSTANCE.toWirePayEventEnvelopeOfRefundMerchantEventDTO(envelope);
        var parsed = WirePayEventEnvelopeOfRefundMerchantEventDTO.parseFrom(wire.toByteArray());
        var restored = AcquireRpcMapper.INSTANCE.fromWirePayEventEnvelopeOfRefundMerchantEventDTO(parsed);

        assertEquals(envelope, restored);
        assertFalse(parsed.getData().hasRefundId());
        assertFalse(parsed.getData().hasPaymentId());
        assertEquals(exact, restored.data().sourceVersion());
    }

    @Test
    void rejectsMissingEnvelopeFieldsAndUnknownTerminalState() {
        long version = 7;
        var valid = envelope("refund", version, new RefundMerchantEventDTO("RefundOrder", "refund", "request",
                "payment", "order", "merchant", "merchant-refund", 100, "CNY", "FAILED", 0, 100,
                version, null));
        var wire = AcquireRpcMapper.INSTANCE.toWirePayEventEnvelopeOfRefundMerchantEventDTO(valid);

        assertThrows(RuntimeException.class, () -> AcquireRpcMapper.INSTANCE
                .fromWirePayEventEnvelopeOfRefundMerchantEventDTO(wire.toBuilder().clearOccurredAt().build()));
        assertThrows(RuntimeException.class, () -> AcquireRpcMapper.INSTANCE
                .fromWirePayEventEnvelopeOfRefundMerchantEventDTO(wire.toBuilder().clearData().build()));
        assertThrows(RuntimeException.class, () -> AcquireRpcMapper.INSTANCE
                .fromWirePayEventEnvelopeOfRefundMerchantEventDTO(wire.toBuilder().clearProducer().build()));

        var unknownState = PaymentRpcMapper.INSTANCE.toWireRefundMerchantEventDTO(valid.data()).toBuilder()
                .setState("UNKNOWN").build();
        assertThrows(RuntimeException.class, () -> PaymentRpcMapper.INSTANCE.fromWireRefundMerchantEventDTO(unknownState));
    }
}
