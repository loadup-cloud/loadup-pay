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
package io.github.loadup.components.payment.channel.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.loadup.commons.money.Money;
import io.github.loadup.components.payment.channel.contract.Channel;
import io.github.loadup.components.payment.channel.contract.PaymentMethod;
import org.junit.jupiter.api.Test;

class ChannelPaymentContractTest {
    private static final ChannelH5Input H5_INPUT = new ChannelH5Input("203.0.113.10", H5SceneType.WAP);
    private static final ChannelPayerIdentityInput PAYER_INPUT =
            new ChannelPayerIdentityInput(Channel.ALIPAY, PayerIdentityType.USER_ID, "buyer-1", "app-scope");

    @Test
    void preparedPaymentAndTargetRequirePositiveCnyAmountAndEpoch() {
        assertEquals(7, ApiFixtures.prepared(PaymentMethod.JSAPI).executionEpoch());
        assertThrows(
                RuntimeException.class,
                () -> new PreparedChannelPayment(
                        ApiFixtures.IDENTITY, Money.ofMinor(0, "CNY"), PaymentMethod.JSAPI, 7, "ref"));
        assertThrows(
                RuntimeException.class,
                () -> new PreparedChannelPayment(
                        ApiFixtures.IDENTITY, Money.ofMinor(1250, "USD"), PaymentMethod.JSAPI, 7, "ref"));
        assertThrows(
                RuntimeException.class,
                () -> new PreparedChannelPayment(
                        ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, PaymentMethod.JSAPI, 0, "ref"));
        assertThrows(
                RuntimeException.class,
                () -> new PreparedChannelPayment(null, ApiFixtures.AMOUNT, PaymentMethod.JSAPI, 7, "ref"));
        assertThrows(
                RuntimeException.class,
                () -> new PreparedChannelPayment(ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, null, 7, "ref"));
        assertEquals(ApiFixtures.AMOUNT, new ChannelPaymentTarget(ApiFixtures.IDENTITY, ApiFixtures.AMOUNT).amount());
        assertThrows(
                RuntimeException.class, () -> new ChannelPaymentTarget(ApiFixtures.IDENTITY, Money.ofMinor(0, "CNY")));
        assertThrows(RuntimeException.class, () -> new ChannelPaymentTarget(null, ApiFixtures.AMOUNT));
    }

    @Test
    void preparedRefundEnforcesOriginalAmountCurrencyAndIdentity() {
        assertEquals(ApiFixtures.REFUND_AMOUNT, ApiFixtures.refund().amount());
        assertThrows(
                RuntimeException.class,
                () -> new PreparedChannelRefund(
                        ApiFixtures.IDENTITY,
                        "refund-1",
                        "refund-no",
                        Money.ofMinor(2000, "CNY"),
                        ApiFixtures.AMOUNT,
                        "trade-1"));
        assertThrows(
                RuntimeException.class,
                () -> new PreparedChannelRefund(
                        ApiFixtures.IDENTITY,
                        "refund-1",
                        "refund-no",
                        Money.ofMinor(0, "CNY"),
                        ApiFixtures.AMOUNT,
                        "trade-1"));
        assertThrows(
                RuntimeException.class,
                () -> new PreparedChannelRefund(
                        ApiFixtures.IDENTITY,
                        "refund-1",
                        "refund-no",
                        Money.ofMinor(500, "USD"),
                        ApiFixtures.AMOUNT,
                        "trade-1"));
        assertThrows(
                RuntimeException.class,
                () -> new PreparedChannelRefund(
                        ApiFixtures.IDENTITY,
                        " ",
                        "refund-no",
                        ApiFixtures.REFUND_AMOUNT,
                        ApiFixtures.AMOUNT,
                        "trade-1"));
        assertThrows(
                RuntimeException.class,
                () -> new PreparedChannelRefund(
                        ApiFixtures.IDENTITY,
                        "refund-1",
                        " ",
                        ApiFixtures.REFUND_AMOUNT,
                        ApiFixtures.AMOUNT,
                        "trade-1"));
        assertThrows(
                RuntimeException.class,
                () -> new PreparedChannelRefund(
                        ApiFixtures.IDENTITY,
                        "refund-1",
                        "refund-no",
                        ApiFixtures.REFUND_AMOUNT,
                        ApiFixtures.AMOUNT,
                        " "));
        assertThrows(
                RuntimeException.class,
                () -> new PreparedChannelRefund(
                        null, "refund-1", "refund-no", ApiFixtures.REFUND_AMOUNT, ApiFixtures.AMOUNT, "trade-1"));
    }

    @Test
    void paymentRequestMatchesMethodAndPayerChannel() {
        assertEquals(
                PaymentMethod.PAYMENT_CODE,
                request(PaymentMethod.PAYMENT_CODE, new ChannelPaymentCodeInput("2812345678901234"))
                        .payment()
                        .method());
        assertEquals(
                PaymentMethod.JSAPI,
                request(PaymentMethod.JSAPI, PAYER_INPUT).payment().method());
        assertEquals(
                PaymentMethod.H5,
                request(PaymentMethod.H5, new NoChannelInput()).payment().method());
        assertEquals(
                PaymentMethod.H5, request(PaymentMethod.H5, H5_INPUT).payment().method());
        assertThrows(RuntimeException.class, () -> request(PaymentMethod.PAYMENT_CODE, PAYER_INPUT));
        assertThrows(RuntimeException.class, () -> request(PaymentMethod.JSAPI, new ChannelPaymentCodeInput("code")));
        assertThrows(
                RuntimeException.class,
                () -> request(
                        PaymentMethod.JSAPI,
                        new ChannelPayerIdentityInput(Channel.WECHAT, PayerIdentityType.OPEN_ID, "openid", "scope")));
        assertThrows(RuntimeException.class, () -> request(PaymentMethod.H5, new ChannelPaymentCodeInput("code")));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentRequest(
                        null, "subject", ApiFixtures.EXPIRY, new ChannelPaymentCodeInput("code")));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentRequest(
                        ApiFixtures.prepared(PaymentMethod.PAYMENT_CODE),
                        " ",
                        ApiFixtures.EXPIRY,
                        new ChannelPaymentCodeInput("code")));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentRequest(
                        ApiFixtures.prepared(PaymentMethod.PAYMENT_CODE),
                        "subject",
                        null,
                        new ChannelPaymentCodeInput("code")));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentRequest(
                        ApiFixtures.prepared(PaymentMethod.PAYMENT_CODE), "subject", ApiFixtures.EXPIRY, null));
    }

    @Test
    void paymentValidationUsesTheSameMethodAndPayerRules() {
        assertEquals(
                PaymentMethod.PAYMENT_CODE,
                validation(PaymentMethod.PAYMENT_CODE, new ChannelPaymentCodeInput("2812345678901234"))
                        .method());
        assertEquals(
                PaymentMethod.JSAPI,
                validation(PaymentMethod.JSAPI, PAYER_INPUT).method());
        assertEquals(
                PaymentMethod.H5,
                validation(PaymentMethod.H5, new NoChannelInput()).method());
        assertThrows(RuntimeException.class, () -> validation(PaymentMethod.PAYMENT_CODE, PAYER_INPUT));
        assertThrows(
                RuntimeException.class,
                () -> validation(
                        PaymentMethod.JSAPI,
                        new ChannelPayerIdentityInput(Channel.WECHAT, PayerIdentityType.OPEN_ID, "openid", "scope")));
        assertThrows(RuntimeException.class, () -> validation(PaymentMethod.H5, new ChannelPaymentCodeInput("code")));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentValidation(
                        ApiFixtures.IDENTITY,
                        Money.ofMinor(0, "CNY"),
                        PaymentMethod.JSAPI,
                        "subject",
                        ApiFixtures.EXPIRY,
                        PAYER_INPUT));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentValidation(
                        ApiFixtures.IDENTITY,
                        ApiFixtures.AMOUNT,
                        PaymentMethod.JSAPI,
                        " ",
                        ApiFixtures.EXPIRY,
                        PAYER_INPUT));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentValidation(
                        ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, PaymentMethod.JSAPI, "subject", null, PAYER_INPUT));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentValidation(
                        null, ApiFixtures.AMOUNT, PaymentMethod.JSAPI, "subject", ApiFixtures.EXPIRY, PAYER_INPUT));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentValidation(
                        ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, null, "subject", ApiFixtures.EXPIRY, PAYER_INPUT));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentValidation(
                        ApiFixtures.IDENTITY,
                        ApiFixtures.AMOUNT,
                        PaymentMethod.JSAPI,
                        "subject",
                        ApiFixtures.EXPIRY,
                        null));
    }

    @Test
    void submissionSeparatesObservationsFromClientActions() {
        assertEquals(
                ChannelPaymentOutcome.Kind.ACCEPTED,
                new ChannelPaymentSubmission(
                                ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, ApiFixtures.acceptedObservation(), null)
                        .observation()
                        .kind());
        var alipayH5 = new AlipayH5ChannelAction(ApiFixtures.alipayH5Url(), ApiFixtures.EXPIRY);
        assertEquals(
                Channel.ALIPAY,
                new ChannelPaymentSubmission(ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, null, alipayH5)
                        .action()
                        .channel());
        var jsapi = new AlipayJsapiChannelAction("trade-1", ApiFixtures.EXPIRY);
        var accepted = ApiFixtures.acceptedObservation(ApiFixtures.IDENTITY, "trade-1", ApiFixtures.AMOUNT);
        assertEquals(
                ChannelPaymentOutcome.Kind.ACCEPTED,
                new ChannelPaymentSubmission(ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, accepted, jsapi)
                        .observation()
                        .kind());
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentSubmission(ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, null, null));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentSubmission(
                        ApiFixtures.IDENTITY, Money.ofMinor(0, "CNY"), ApiFixtures.acceptedObservation(), null));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentSubmission(null, ApiFixtures.AMOUNT, ApiFixtures.acceptedObservation(), null));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentSubmission(
                        ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, ApiFixtures.succeededObservation(), jsapi));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentSubmission(
                        ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, ApiFixtures.acceptedObservation(), alipayH5));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentSubmission(ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, null, jsapi));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentSubmission(
                        ApiFixtures.IDENTITY,
                        ApiFixtures.AMOUNT,
                        accepted,
                        new AlipayJsapiChannelAction("other-trade", ApiFixtures.EXPIRY)));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentSubmission(
                        ApiFixtures.IDENTITY,
                        ApiFixtures.AMOUNT,
                        new PaymentObservation(
                                ApiFixtures.WECHAT_IDENTITY,
                                ChannelPaymentOutcome.Kind.ACCEPTED,
                                ApiFixtures.AMOUNT,
                                null,
                                null,
                                PaymentObservation.Source.QUERY_RESPONSE,
                                "receipt-1",
                                ApiFixtures.HASH),
                        null));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentSubmission(
                        ApiFixtures.IDENTITY,
                        ApiFixtures.AMOUNT,
                        new PaymentObservation(
                                ApiFixtures.IDENTITY,
                                ChannelPaymentOutcome.Kind.ACCEPTED,
                                Money.ofMinor(1000, "CNY"),
                                null,
                                null,
                                PaymentObservation.Source.QUERY_RESPONSE,
                                "receipt-1",
                                ApiFixtures.HASH),
                        null));
        var wechatJsapi = new WechatJsapiChannelAction(
                "wx-app", "1700000000", "nonce", "prepay_id=wx123", "RSA", "A".repeat(342), ApiFixtures.EXPIRY);
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentSubmission(ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, accepted, wechatJsapi));
    }

    @Test
    void recoveryCarriesOnlyQueryContext() {
        var recovery = new ChannelPaymentRecovery(
                ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, PaymentMethod.JSAPI, 7, ApiFixtures.EXPIRY);
        assertEquals(ApiFixtures.AMOUNT, recovery.amount());
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentRecovery(
                        ApiFixtures.IDENTITY, Money.ofMinor(0, "CNY"), PaymentMethod.JSAPI, 7, ApiFixtures.EXPIRY));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentRecovery(
                        ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, PaymentMethod.JSAPI, 0, ApiFixtures.EXPIRY));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentRecovery(
                        ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, null, 7, ApiFixtures.EXPIRY));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentRecovery(
                        ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, PaymentMethod.JSAPI, 7, null));
        assertThrows(
                RuntimeException.class,
                () -> new ChannelPaymentRecovery(null, ApiFixtures.AMOUNT, PaymentMethod.JSAPI, 7, ApiFixtures.EXPIRY));
    }

    private static ChannelPaymentRequest request(PaymentMethod method, ChannelPaymentInput input) {
        return new ChannelPaymentRequest(ApiFixtures.prepared(method), "subject", ApiFixtures.EXPIRY, input);
    }

    private static ChannelPaymentValidation validation(PaymentMethod method, ChannelPaymentInput input) {
        return new ChannelPaymentValidation(
                ApiFixtures.IDENTITY, ApiFixtures.AMOUNT, method, "subject", ApiFixtures.EXPIRY, input);
    }
}
