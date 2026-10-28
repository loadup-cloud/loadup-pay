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
package io.github.loadup.pay.acquire.infrastructure.codec;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.pay.acquire.client.command.AcquireOrderCreateCommand;
import io.github.loadup.pay.acquire.client.command.CheckoutCreateCommand;
import io.github.loadup.pay.acquire.domain.model.FeeRefundPolicy;
import io.github.loadup.pay.acquire.domain.model.OrderCreateIntent;
import io.github.loadup.pay.acquire.domain.model.OrderFeePolicy;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderCreationCodecImplTest {
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final JsonMapper mapper = JsonUtil.customize(JsonMapper.builder()).build();
    final OrderCreationCodecImpl codec = new OrderCreationCodecImpl(mapper, validation.getValidator());

    @AfterEach
    void close() {
        validation.close();
    }

    AcquireOrderCreateCommand command(OffsetDateTime due) {
        return new AcquireOrderCreateCommand("merchant", "M001", "scope", 9007199254740993L, "CNY", "order", due);
    }

    OrderCreateIntent intent(String payload) {
        return new OrderCreateIntent(
                "intent",
                "order",
                "merchant",
                "M001",
                "a".repeat(64),
                "b".repeat(64),
                "ORDER",
                null,
                payload,
                "INIT",
                null,
                null,
                null,
                0);
    }

    io.github.loadup.pay.acquire.client.command.DirectPayRecoverCommand direct(String action, long first,
                                                                               io.github.loadup.pay.acquire.domain.model.PaymentInteractionKind kind, io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO choice) {
        return new io.github.loadup.pay.acquire.client.command.DirectPayRecoverCommand("merchant", "M001", "scope", 9007199254740993L, "CNY", "order", null,
                action, first, choice, kind);
    }

    @Test
    void directPayMetadataRoundTripBindsFirstActionAndRejectsOtherEntryModes() {
        var choice = new io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO(io.github.loadup.commons.payment.Channel.WECHAT,
                io.github.loadup.commons.payment.PaymentMethod.H5);
        var value = direct("first", 4000L, io.github.loadup.pay.acquire.domain.model.PaymentInteractionKind.H5_SCENE, choice);
        var payload = codec.encodeDirectPay(value);
        var record = new OrderCreateIntent("intent", "order", "merchant", "M001", "a".repeat(64), "b".repeat(64), "DIRECT_PAY", null,
                payload, "INIT", null, null, null, 0L);
        assertEquals(value, codec.decodeDirectPay(record));
        assertThrows(CommonException.class, () -> codec.decodeIntent(record));
        assertThrows(CommonException.class, () -> codec.decodeDirectPay(intent(payload)));
        var digest = codec.directPayDigest("tenant", "actor", value);
        assertNotEquals(digest, codec.requestDigest("tenant", "actor", command(null)));
        assertNotEquals(digest, codec.directPayDigest("other", "actor", value));
        assertNotEquals(digest, codec.directPayDigest("tenant", "other", value));
        assertNotEquals(digest, codec.directPayDigest("tenant", "actor", direct("changed", 4000L, value.interactionKind(), choice)));
        assertNotEquals(digest, codec.directPayDigest("tenant", "actor", direct("first", 4001L, value.interactionKind(), choice)));
        assertNotEquals(digest, codec.directPayDigest("tenant", "actor", direct("first", 4000L,
                io.github.loadup.pay.acquire.domain.model.PaymentInteractionKind.NONE, choice)));
        assertNotEquals(digest, codec.directPayDigest("tenant", "actor", direct("first", 4000L, value.interactionKind(),
                new io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO(io.github.loadup.commons.payment.Channel.ALIPAY,
                        io.github.loadup.commons.payment.PaymentMethod.H5))));
        var unknown = payload.replace("H5_SCENE", "UNKNOWN_INPUT");
        var corrupt = new OrderCreateIntent("intent", "order", "merchant", "M001", "a".repeat(64), "b".repeat(64), "DIRECT_PAY", null,
                unknown, "INIT", null, null, null, 0L);
        assertThrows(CommonException.class, () -> codec.decodeDirectPay(corrupt));
    }

    @Test
    void frozenCreatePayloadRetainsIntegersAboveDoublePrecision() {
        var value = command(null);
        assertEquals(value, codec.decodeIntent(intent(codec.encodeIntent(value))));
    }

    @Test
    void digestBindsTenantCallerEntryAndAllMerchantIntentFields() {
        var value = command(null);
        var original = codec.requestDigest("tenant", "actor", value);
        assertNotEquals(original, codec.requestDigest("another", "actor", value));
        assertNotEquals(original, codec.requestDigest("tenant", "another", value));
        assertNotEquals(
                original,
                codec.requestDigest(
                        "tenant",
                        "actor",
                        new AcquireOrderCreateCommand(
                                "merchant", "M001", "scope", value.amountMinor(), "CNY", "changed", null)));
    }

    @Test
    void equivalentOffsetsNormalizeToTheSameDeadlineAndDigest() {
        var first = command(OffsetDateTime.parse("2026-10-21T12:00:00.123456+08:00"));
        var second = command(OffsetDateTime.parse("2026-10-21T04:00:00.123456Z"));
        assertEquals(first, second);
        assertEquals(codec.requestDigest("tenant", "actor", first), codec.requestDigest("tenant", "actor", second));
    }

    @Test
    void corruptOrUnboundStoredIntentCannotBeReplayed() {
        assertThrows(CommonException.class, () -> codec.decodeIntent(intent("not-json")));
        assertThrows(
                CommonException.class,
                () -> codec.decodeIntent(intent(codec.encodeIntent(
                        new AcquireOrderCreateCommand("other-merchant", "M001", "scope", 100, "CNY", "order", null)))));
        assertThrows(CommonException.class, () -> codec.decodeAdmission("{}", "a".repeat(64)));
    }

    @Test
    void ordinaryV1DigestRemainsCompatibleWithTheOriginalStoredProtocol() {
        assertEquals(
                "222a9db073ac1fafb4f2347a059eedab4829ec6c51a421453462a655383a99c4",
                codec.requestDigest("tenant", "actor", command(null)));
    }

    @Test
    void checkoutPayloadBindsEntryAndRequestedReturnTargetWithoutChangingOrdinaryPayloads() {
        var value =
                new CheckoutCreateCommand("merchant", "M001", "scope", 9007199254740993L, "CNY", "order", null, null);
        String digest = codec.checkoutDigest("tenant", "actor", value);
        var intent = new OrderCreateIntent(
                "intent",
                "order",
                "merchant",
                "M001",
                digest,
                "b".repeat(64),
                "CHECKOUT",
                "session",
                codec.encodeCheckout(value),
                "INIT",
                null,
                null,
                null,
                0);
        assertEquals(value, codec.decodeCheckout(intent));
        assertNotEquals(codec.requestDigest("tenant", "actor", command(null)), digest);
        assertNotEquals(
                digest,
                codec.checkoutDigest(
                        "tenant",
                        "actor",
                        new CheckoutCreateCommand(
                                "merchant", "M001", "scope", 9007199254740993L, "CNY", "order", null, "return-v1")));
        assertNotEquals(digest, codec.checkoutDigest("other", "actor", value));
        assertNotEquals(digest, codec.checkoutDigest("tenant", "other", value));
        assertThrows(CommonException.class, () -> codec.decodeIntent(intent));
        assertThrows(CommonException.class, () -> codec.decodeCheckout(intent(codec.encodeIntent(command(null)))));
        var finished =
                intent.finish("COMPLETED", "admission", java.time.LocalDateTime.of(2026, 10, 22, 12, 0), "SUCCESS");
        assertEquals("CHECKOUT", finished.entryMode());
        assertEquals("session", finished.initialSessionId());
    }

    @Test
    void feeSnapshotRetainsExactDecimalRateAndOriginalTotalCalculation() {
        var policy = new OrderFeePolicy(
                new BigDecimal("0.003000000000000000000001"),
                1,
                RoundingMode.HALF_UP,
                0,
                null,
                FeeRefundPolicy.NO_RETURN);
        var payload = codec.encodeFee(policy);
        var restored = mapper.readValue(payload, OrderFeePolicy.class);
        assertEquals(policy, restored);
        assertEquals(
                policy.calculate(io.github.loadup.commons.validation.DomainChecks.cnyAmount(9007199254740993L, "CNY")),
                restored.calculate(io.github.loadup.commons.validation.DomainChecks.cnyAmount(9007199254740993L, "CNY")));
    }
}
