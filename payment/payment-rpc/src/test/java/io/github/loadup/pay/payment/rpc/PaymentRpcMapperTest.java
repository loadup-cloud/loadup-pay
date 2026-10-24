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
package io.github.loadup.pay.payment.rpc;


import io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand;
import io.github.loadup.pay.payment.client.dto.PaymentCodeInteractionDTO;
import io.github.loadup.pay.payment.client.dto.H5SceneInteractionDTO;
import io.github.loadup.pay.payment.client.dto.RefundSettlementFactsDTO;
import io.github.loadup.pay.payment.client.enums.RefundState;
import io.github.loadup.pay.payment.client.event.RefundOutcomeEventDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentInputPrepareCommand;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentInteractionDTO;
import io.github.loadup.pay.channel.api.model.H5SceneType;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import static org.junit.jupiter.api.Assertions.*;

class PaymentRpcMapperTest {
    @Test
    void binaryRoundTripPreservesSensitiveOneofAndAbsentValues() throws Exception {
        var input = new PaymentInputPrepareCommand("payment", "order", "allocation", "merchant", 100, "CNY", "input",
                null, new PaymentCodeInteractionDTO("synthetic-code"));
        var bytes = PaymentRpcMapper.INSTANCE.toWirePaymentInputPrepareCommand(input).toByteArray();
        assertEquals(input, PaymentRpcMapper.INSTANCE.fromWirePaymentInputPrepareCommand(WirePaymentInputPrepareCommand.parseFrom(bytes)));
        assertThrows(IllegalArgumentException.class,
                () -> PaymentRpcMapper.INSTANCE.fromWirePaymentInteractionDTO(WirePaymentInteractionDTO.getDefaultInstance()));
    }

    @Test
    void h5SceneInteractionBinaryRoundTripRequiresKnownSceneAndPresentFields() throws Exception {
        var input = new PaymentInputPrepareCommand("payment", "order", "allocation", "merchant", 100, "CNY", "input",
                null, new H5SceneInteractionDTO("203.0.113.1", H5SceneType.WAP));

        var bytes = PaymentRpcMapper.INSTANCE.toWirePaymentInputPrepareCommand(input).toByteArray();
        assertEquals(input, PaymentRpcMapper.INSTANCE.fromWirePaymentInputPrepareCommand(WirePaymentInputPrepareCommand.parseFrom(bytes)));

        var unknownScene = io.github.loadup.pay.payment.rpc.proto.WireH5SceneInteractionDTO.newBuilder()
                .setPayerClientIp("203.0.113.1").setSceneType("UNKNOWN").build();
        var missingScene = io.github.loadup.pay.payment.rpc.proto.WireH5SceneInteractionDTO.newBuilder()
                .setPayerClientIp("203.0.113.1").build();
        assertThrows(IllegalArgumentException.class, () -> PaymentRpcMapper.INSTANCE.fromWireH5SceneInteractionDTO(unknownScene));
        assertThrows(IllegalArgumentException.class, () -> PaymentRpcMapper.INSTANCE.fromWireH5SceneInteractionDTO(missingScene));
    }

    @Test
    void refundFactsBinaryRoundTripPreservesLargeVersionsEvidenceAndPaymentTime() throws Exception {
        long exactVersion = 9_007_199_254_741_111L;
        var initiatedAt = OffsetDateTime.parse("2026-10-23T12:34:56.123456+08:00");
        var refund = new RefundOutcomeEventDTO("refund", "request", "payment", "order", "operation", "merchant", 1000,
                "CNY", RefundState.SUCCEEDED, exactVersion, 1000, "refund-evidence");
        var facts = new RefundSettlementFactsDTO(refund, 2000, exactVersion + 1, "payment-evidence", "attempt", initiatedAt,
                exactVersion + 2);

        var bytes = PaymentRpcMapper.INSTANCE.toWireRefundSettlementFactsDTO(facts).toByteArray();
        var roundTrip = PaymentRpcMapper.INSTANCE.fromWireRefundSettlementFactsDTO(
                io.github.loadup.pay.payment.rpc.proto.WireRefundSettlementFactsDTO.parseFrom(bytes));

        assertEquals(exactVersion, roundTrip.refund().refundVersion());
        assertEquals(exactVersion + 1, roundTrip.paymentVersion());
        assertEquals(exactVersion + 2, roundTrip.paymentRefundVersion());
        assertEquals("refund-evidence", roundTrip.refund().evidenceId());
        assertEquals("payment-evidence", roundTrip.paymentEvidenceId());
        assertEquals(initiatedAt.toInstant(), roundTrip.paymentInitiatedAt().toInstant());
    }


}
