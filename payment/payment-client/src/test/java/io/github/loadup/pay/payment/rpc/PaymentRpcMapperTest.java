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


import io.github.loadup.pay.channel.api.model.H5SceneType;
import io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand;
import io.github.loadup.pay.payment.client.dto.H5SceneInteractionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentCodeInteractionDTO;
import io.github.loadup.pay.payment.client.dto.RefundSettlementFactsDTO;
import io.github.loadup.pay.payment.domain.model.RefundState;
import io.github.loadup.pay.payment.client.event.RefundOutcomeEventDTO;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentInputPrepareCommand;
import io.github.loadup.pay.payment.rpc.proto.WirePaymentInteractionDTO;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentRpcMapperTest {
    @Test
    void inputExpiryRoundTripRequiresItsScopeDispositionAndOriginalFencedProof() throws Exception {
        var command = new io.github.loadup.pay.payment.client.command.PaymentInputExpireCommand("input", "payment", "order", "allocation", "merchant",
                9_007_199_254_740_993L, "CNY", "action", new io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO(
                io.github.loadup.commons.payment.Channel.WECHAT, io.github.loadup.commons.payment.PaymentMethod.H5));
        var wire = PaymentRpcMapper.INSTANCE.toWirePaymentInputExpireCommand(command);
        assertEquals(command, PaymentRpcMapper.INSTANCE.fromWirePaymentInputExpireCommand(
                io.github.loadup.pay.payment.rpc.proto.WirePaymentInputExpireCommand.parseFrom(wire.toByteArray())));
        assertThrows(IllegalArgumentException.class, () -> PaymentRpcMapper.INSTANCE.fromWirePaymentInputExpireCommand(wire.toBuilder().clearInputRef().build()));
        assertThrows(IllegalArgumentException.class, () -> PaymentRpcMapper.INSTANCE.fromWirePaymentInputExpireCommand(wire.toBuilder().clearAmountMinor().build()));
        var proof = new io.github.loadup.pay.payment.client.dto.PaymentCloseDTO("payment", "order", 1L, true, null, "never-accepted-proof");
        var result = new io.github.loadup.pay.payment.client.dto.PaymentInputExpiryDTO("payment", "order",
                io.github.loadup.pay.payment.domain.model.PaymentInputExpiryDisposition.EXPIRED_CLOSED, proof);
        var encoded = PaymentRpcMapper.INSTANCE.toWirePaymentInputExpiryDTO(result);
        assertEquals(result, PaymentRpcMapper.INSTANCE.fromWirePaymentInputExpiryDTO(
                io.github.loadup.pay.payment.rpc.proto.WirePaymentInputExpiryDTO.parseFrom(encoded.toByteArray())));
        assertThrows(IllegalArgumentException.class, () -> PaymentRpcMapper.INSTANCE.fromWirePaymentInputExpiryDTO(encoded.toBuilder().clearDisposition().build()));
        assertThrows(IllegalArgumentException.class, () -> PaymentRpcMapper.INSTANCE.fromWirePaymentInputExpiryDTO(encoded.toBuilder().setDisposition("UNKNOWN").clearClosed().build()));
        assertThrows(IllegalArgumentException.class, () -> PaymentRpcMapper.INSTANCE.fromWirePaymentInputExpiryDTO(encoded.toBuilder().clearClosed().build()));
        assertThrows(IllegalArgumentException.class, () -> PaymentRpcMapper.INSTANCE.fromWirePaymentInputExpiryDTO(encoded.toBuilder()
                .setClosed(encoded.getClosed().toBuilder().setFenced(false)).build()));
        assertThrows(IllegalArgumentException.class, () -> PaymentRpcMapper.INSTANCE.fromWirePaymentInputExpiryDTO(encoded.toBuilder()
                .setClosed(encoded.getClosed().toBuilder().setPaymentId("other")).build()));
        for (var disposition : java.util.List.of(io.github.loadup.pay.payment.domain.model.PaymentInputExpiryDisposition.INPUT_ACTIVE,
                io.github.loadup.pay.payment.domain.model.PaymentInputExpiryDisposition.PAYMENT_EXISTS)) {
            var unchanged = new io.github.loadup.pay.payment.client.dto.PaymentInputExpiryDTO("payment", "order", disposition, null);
            assertEquals(unchanged, PaymentRpcMapper.INSTANCE.fromWirePaymentInputExpiryDTO(PaymentRpcMapper.INSTANCE.toWirePaymentInputExpiryDTO(unchanged)));
        }
    }

    @Test
    void inputLookupRoundTripPreservesScopeExactAmountAndExplicitAbsence() throws Exception {
        var query = new io.github.loadup.pay.payment.client.query.PaymentInputLookupQuery("payment", "order", "allocation", "merchant",
                9_007_199_254_740_993L, "CNY", "action", new io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO(
                io.github.loadup.commons.payment.Channel.WECHAT, io.github.loadup.commons.payment.PaymentMethod.H5));
        var wire = PaymentRpcMapper.INSTANCE.toWirePaymentInputLookupQuery(query);
        assertEquals(query, PaymentRpcMapper.INSTANCE.fromWirePaymentInputLookupQuery(
                io.github.loadup.pay.payment.rpc.proto.WirePaymentInputLookupQuery.parseFrom(wire.toByteArray())));
        assertThrows(IllegalArgumentException.class, () -> PaymentRpcMapper.INSTANCE.fromWirePaymentInputLookupQuery(wire.toBuilder().clearAmountMinor().build()));
        assertThrows(IllegalArgumentException.class, () -> PaymentRpcMapper.INSTANCE.fromWirePaymentInputLookupQuery(wire.toBuilder().clearChoice().build()));
        assertThrows(IllegalArgumentException.class, () -> PaymentRpcMapper.INSTANCE.fromWirePaymentInputLookupQuery(wire.toBuilder()
                .setChoice(wire.getChoice().toBuilder().setChannel("UNKNOWN_CHANNEL")).build()));
        var absent = new io.github.loadup.pay.payment.client.dto.PaymentInputLookupDTO(false, null);
        var absentWire = PaymentRpcMapper.INSTANCE.toWirePaymentInputLookupDTO(absent);
        assertTrue(absentWire.hasFound());
        assertFalse(absentWire.getFound());
        assertFalse(absentWire.hasInput());
        assertEquals(absent, PaymentRpcMapper.INSTANCE.fromWirePaymentInputLookupDTO(absentWire));
        var input = new io.github.loadup.pay.payment.client.dto.PaymentInputDTO("original-input", "a".repeat(64),
                OffsetDateTime.parse("2026-10-24T12:00:00.123456+08:00"));
        var found = new io.github.loadup.pay.payment.client.dto.PaymentInputLookupDTO(true, input);
        var roundTrip = PaymentRpcMapper.INSTANCE.fromWirePaymentInputLookupDTO(
                io.github.loadup.pay.payment.rpc.proto.WirePaymentInputLookupDTO.parseFrom(
                        PaymentRpcMapper.INSTANCE.toWirePaymentInputLookupDTO(found).toByteArray()));
        assertTrue(roundTrip.found());
        assertEquals(input, roundTrip.input());
        assertThrows(IllegalArgumentException.class, () -> PaymentRpcMapper.INSTANCE.fromWirePaymentInputLookupDTO(absentWire.toBuilder().clearFound().build()));
        assertThrows(IllegalArgumentException.class, () -> PaymentRpcMapper.INSTANCE.fromWirePaymentInputLookupDTO(absentWire.toBuilder().setFound(true).build()));
        assertThrows(IllegalArgumentException.class, () -> PaymentRpcMapper.INSTANCE.fromWirePaymentInputLookupDTO(absentWire.toBuilder()
                .setInput(PaymentRpcMapper.INSTANCE.toWirePaymentInputDTO(input)).build()));
    }

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
