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
import io.github.loadup.pay.acquire.client.dto.FeeRefundAckDTO;
import io.github.loadup.pay.acquire.rpc.proto.WireFeeRefundAckDTO;
import io.github.loadup.pay.acquire.rpc.proto.WirePayEventEnvelopeOfOrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class AcquireRpcMapperTest {
    @Test
    void feeEnvelopeBinaryRoundTripPreservesExactMoneyVersionAndIdentity() throws Exception {
        long exact = 9007199254740993L;
        var data = new OrderRefundSummaryEventDTO("order", "merchant", "CNY", exact, 0, exact);
        var envelope = new EventEnvelope<>("delivery", "business", "payment", "pay.payment.refund.fee.v1", 1, "order", exact,
                BusinessTime.external(LocalDateTime.of(2026, 10, 23, 12, 0, 0, 123456000)), data);
        var wire = AcquireRpcMapper.INSTANCE.toWirePayEventEnvelopeOfOrderRefundSummaryEventDTO(envelope);
        var restored = AcquireRpcMapper.INSTANCE.fromWirePayEventEnvelopeOfOrderRefundSummaryEventDTO(
                WirePayEventEnvelopeOfOrderRefundSummaryEventDTO.parseFrom(wire.toByteArray()));
        assertEquals(envelope, restored);
        assertTrue(wire.getData().hasRefundReservedMinor());
        assertEquals(0, wire.getData().getRefundReservedMinor());
    }

    @Test
    void ackBinaryRoundTripRetainsTheIncomingAndVerifiedVersionsSeparately() throws Exception {
        var ack = new FeeRefundAckDTO("delivery", "business", "order", 3, 7, true);
        var wire = AcquireRpcMapper.INSTANCE.toWireFeeRefundAckDTO(ack);
        assertEquals(ack, AcquireRpcMapper.INSTANCE.fromWireFeeRefundAckDTO(WireFeeRefundAckDTO.parseFrom(wire.toByteArray())));
        assertThrows(RuntimeException.class,
                () -> AcquireRpcMapper.INSTANCE.fromWireFeeRefundAckDTO(wire.toBuilder().clearSourceVersion().build()));
    }
}
