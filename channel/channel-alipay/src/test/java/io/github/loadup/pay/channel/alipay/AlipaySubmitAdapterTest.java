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
package io.github.loadup.pay.channel.alipay;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.alipay.protocol.AlipayMethod;
import io.github.loadup.pay.channel.alipay.protocol.AlipayProtocolClient;
import io.github.loadup.pay.channel.alipay.protocol.AlipayVerifiedResponse;
import io.github.loadup.pay.channel.alipay.config.AlipayNotificationProperties;
import io.github.loadup.pay.channel.api.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AlipaySubmitAdapterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC);
    private static final Money AMOUNT = Money.ofMinor(125_000, "CNY");
    private static final String HASH = "a".repeat(64);
    private static final String ORDER_NO = "payment-request-18";
    private static final String TRADE_NO = "ali-trade-18";
    private static final OffsetDateTime EXPIRY = OffsetDateTime.parse("2026-10-23T19:00:00+08:00");
    private static final String SUBJECT = "Original lunch order";

    private final AlipayProtocolClient client = mock(AlipayProtocolClient.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final AlipayPaymentAdapter queries = new AlipayPaymentAdapter(client, json, CLOCK);
    private final AlipayNotificationProperties notifications = new AlipayNotificationProperties(Map.of());
    private final AlipaySubmitAdapter adapter = new AlipaySubmitAdapter(client, queries, json, CLOCK,
            notifications);
    private final ChannelExecutionContext direct = context("revision-1", HASH, AccessMode.DIRECT,
            new AlipayAccountIdentity("main-app", "fixed-seller", null, null, null));

    @Test
    void payerCodePaySendsOriginalFixedFactsAndCanonicalizesSuccessThroughQuery() throws Exception {
        var request = request(direct, PaymentMethod.PAYMENT_CODE, new ChannelPaymentCodeInput("2812345678901234"), EXPIRY);
        var payPayload = "{\"code\":\"10000\",\"out_trade_no\":\"" + ORDER_NO
                + "\",\"total_amount\":\"1250.00\",\"trade_no\":\"" + TRADE_NO + "\"}";
        var queryPayload = successQuery(TRADE_NO, "1250.00");
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.PAY), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.PAY, payPayload));
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.QUERY), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.QUERY, queryPayload));

        var result = TenantUtil.callWithTenant("tenant", () -> adapter.submit(direct, request));

        assertEquals(ChannelPaymentOutcome.Kind.SUCCEEDED, result.observation().kind());
        assertEquals(request.payment().identity(), result.identity());
        assertEquals(AMOUNT, result.amount());
        assertEquals(TRADE_NO, result.observation().channelTransactionNo());
        assertEquals(PaymentObservation.Source.QUERY_RESPONSE, result.observation().source());
        assertEquals(OffsetDateTime.parse("2026-10-23T10:30:00Z").toLocalDateTime(), result.observation().succeededAt());
        var body = json.readValue(requestBody(AlipayMethod.PAY), Map.class);
        assertEquals(ORDER_NO, body.get("out_trade_no"));
        assertEquals("fixed-seller", body.get("seller_id"));
        assertEquals(SUBJECT, body.get("subject"));
        assertEquals("1250.00", body.get("total_amount"));
        assertEquals("2026-10-23 19:00:00", body.get("time_expire"));
        assertEquals("bar_code", body.get("scene"));
        assertEquals("2812345678901234", body.get("auth_code"));
        verify(client, times(1)).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.PAY), anyString(), eq(Map.of()));
        verify(client, times(1)).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.QUERY), anyString(), eq(Map.of()));
    }

    @Test
    void acceptedAndOtherPayCodesDoNotAutomaticallyResubmitOrCreateActions() {
        var request = request(direct, PaymentMethod.PAYMENT_CODE, new ChannelPaymentCodeInput("2812345678901234"), EXPIRY);
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.PAY), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.PAY, "{\"code\":\"10003\"}"));

        var accepted = TenantUtil.callWithTenant("tenant", () -> adapter.submit(direct, request));

        assertEquals(ChannelPaymentOutcome.Kind.ACCEPTED, accepted.observation().kind());
        assertNull(accepted.action());
        verify(client, times(1)).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.PAY), anyString(), eq(Map.of()));
        verifyNoMoreInteractions(client);

        clearInvocations(client);
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.PAY), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.PAY, "{\"code\":\"40004\"}"));
        var unknown = TenantUtil.callWithTenant("tenant", () -> adapter.submit(direct, request));
        assertEquals(ChannelPaymentOutcome.Kind.UNKNOWN, unknown.observation().kind());
        assertNull(unknown.action());
        verify(client, times(1)).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.PAY), anyString(), eq(Map.of()));
        verifyNoMoreInteractions(client);
    }

    @Test
    void rejectsPayResponseIdentityAmountAndMalformedCode() {
        var request = request(direct, PaymentMethod.PAYMENT_CODE, new ChannelPaymentCodeInput("2812345678901234"), EXPIRY);
        stubPay("{\"code\":\"10000\",\"out_trade_no\":\"other-order\",\"total_amount\":\"1250.00\","
                + "\"trade_no\":\"" + TRADE_NO + "\"}");
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.submit(direct, request)));

        stubPay("{\"code\":\"10000\",\"out_trade_no\":\"" + ORDER_NO + "\",\"total_amount\":\"1249.99\","
                + "\"trade_no\":\"" + TRADE_NO + "\"}");
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.submit(direct, request)));

        stubPay("{\"code\":10000}");
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.submit(direct, request)));
    }

    @Test
    void createsMainAndOperatorAppOpenIdRequestsWithBoundAppScopes() throws Exception {
        var mainRequest = request(direct, PaymentMethod.JSAPI,
                new ChannelPayerIdentityInput(Channel.ALIPAY, PayerIdentityType.OPEN_ID, "payer-open-id", "main-app"), EXPIRY);
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.CREATE), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.CREATE, "{\"code\":\"10000\",\"out_trade_no\":\"" + ORDER_NO
                        + "\",\"trade_no\":\"" + TRADE_NO + "\",\"buyer_open_id\":\"payer-open-id\"}"));
        var main = TenantUtil.callWithTenant("tenant", () -> adapter.submit(direct, mainRequest));
        assertEquals(ChannelPaymentOutcome.Kind.ACCEPTED, main.observation().kind());
        assertEquals(TRADE_NO, main.observation().channelTransactionNo());
        var action = assertInstanceOf(AlipayJsapiChannelAction.class, main.action());
        assertEquals(TRADE_NO, action.tradeNo());
        assertEquals(EXPIRY, action.expiresAt());
        var body = json.readValue(requestBody(AlipayMethod.CREATE), Map.class);
        assertEquals("payer-open-id", body.get("buyer_open_id"));
        assertFalse(body.containsKey("op_app_id"));

        var operator = context("provider-revision", HASH, AccessMode.SERVICE_PROVIDER,
                new AlipayAccountIdentity("main-app", "fixed-seller", "operator-app", "authorization-ref", "3"));
        var providerRequest = request(operator, PaymentMethod.JSAPI,
                new ChannelPayerIdentityInput(Channel.ALIPAY, PayerIdentityType.OPEN_ID, "operator-open-id", "operator-app"), EXPIRY);
        when(client.call(eq("tenant"), eq(operator.binding()), eq(AlipayMethod.CREATE), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.CREATE, "{\"code\":\"10000\",\"out_trade_no\":\"" + ORDER_NO
                        + "\",\"trade_no\":\"operator-trade\",\"op_buyer_open_id\":\"operator-open-id\"}"));
        var provider = TenantUtil.callWithTenant("tenant", () -> adapter.submit(operator, providerRequest));
        assertEquals(ChannelPaymentOutcome.Kind.ACCEPTED, provider.observation().kind());
        var providerBody = json.readValue(requestBody(AlipayMethod.CREATE), Map.class);
        assertEquals("operator-app", providerBody.get("op_app_id"));
        assertEquals("operator-open-id", providerBody.get("op_buyer_open_id"));
    }

    @Test
    void onlyJsapiCreateUsesTenantAndFrozenBindingNotificationUrl() throws Exception {
        var callback = java.net.URI.create("https://callbacks.example/tenant/payment-notify");
        var notifications = new AlipayNotificationProperties(Map.of(
                "tenant", Map.of("revision-1", callback, "other-revision", java.net.URI.create("https://other.example/path")),
                "other-tenant", Map.of("revision-1", java.net.URI.create("https://other-tenant.example/path"))));
        var configured = new AlipaySubmitAdapter(client, queries, json, CLOCK, notifications);
        var expectedAdditional = Map.of("notify_url", callback.toASCIIString());
        var jsapiRequest = request(direct, PaymentMethod.JSAPI,
                new ChannelPayerIdentityInput(Channel.ALIPAY, PayerIdentityType.OPEN_ID, "payer-open-id", "main-app"), EXPIRY);
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.CREATE), anyString(), eq(expectedAdditional)))
                .thenReturn(verified(AlipayMethod.CREATE, "{\"code\":\"10000\",\"out_trade_no\":\"" + ORDER_NO
                        + "\",\"trade_no\":\"" + TRADE_NO + "\",\"buyer_open_id\":\"payer-open-id\"}"));

        var created = TenantUtil.callWithTenant("tenant", () -> configured.submit(direct, jsapiRequest));

        assertEquals(TRADE_NO, created.observation().channelTransactionNo());
        var createCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(client).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.CREATE), createCaptor.capture(), eq(expectedAdditional));
        assertFalse(json.readValue(createCaptor.getValue(), Map.class).containsKey("notify_url"));

        clearInvocations(client);
        var codeRequest = request(direct, PaymentMethod.PAYMENT_CODE,
                new ChannelPaymentCodeInput("2812345678901234"), EXPIRY);
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.PAY), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.PAY, "{\"code\":\"10003\"}"));
        TenantUtil.callWithTenant("tenant", () -> configured.submit(direct, codeRequest));
        verify(client).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.PAY), anyString(), eq(Map.of()));
        verify(client, never()).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.CREATE), anyString(), anyMap());
    }

    @Test
    void createsUserIdJsapiWithBuyerIdAndCreateFailureWithoutAction() throws Exception {
        var request = request(direct, PaymentMethod.JSAPI,
                new ChannelPayerIdentityInput(Channel.ALIPAY, PayerIdentityType.USER_ID, "2088123456789012", "main-app"), EXPIRY);
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.CREATE), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.CREATE, "{\"code\":\"10000\",\"out_trade_no\":\"" + ORDER_NO
                        + "\",\"trade_no\":\"" + TRADE_NO + "\",\"buyer_id\":\"2088123456789012\"}"));
        var created = TenantUtil.callWithTenant("tenant", () -> adapter.submit(direct, request));
        assertEquals(ChannelPaymentOutcome.Kind.ACCEPTED, created.observation().kind());
        assertInstanceOf(AlipayJsapiChannelAction.class, created.action());
        assertEquals("2088123456789012", json.readValue(requestBody(AlipayMethod.CREATE), Map.class).get("buyer_id"));

        clearInvocations(client);
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.CREATE), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.CREATE, "{\"code\":\"40004\"}"));
        var failed = TenantUtil.callWithTenant("tenant", () -> adapter.submit(direct, request));
        assertEquals(ChannelPaymentOutcome.Kind.UNKNOWN, failed.observation().kind());
        assertNull(failed.action());
    }

    @Test
    void rejectsCreateResponseWithWrongIdentityBuyerOrAmountAndAppScopeMismatch() {
        var payer = new ChannelPayerIdentityInput(Channel.ALIPAY, PayerIdentityType.OPEN_ID, "payer-open-id", "main-app");
        var request = request(direct, PaymentMethod.JSAPI, payer, EXPIRY);
        stubCreate("{\"code\":\"10000\",\"out_trade_no\":\"other-order\",\"trade_no\":\"" + TRADE_NO
                + "\",\"buyer_open_id\":\"payer-open-id\"}");
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.submit(direct, request)));

        stubCreate("{\"code\":\"10000\",\"out_trade_no\":\"" + ORDER_NO + "\",\"trade_no\":\"" + TRADE_NO
                + "\",\"buyer_open_id\":\"different-id\"}");
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.submit(direct, request)));

        stubCreate("{\"code\":\"10000\",\"out_trade_no\":\"" + ORDER_NO + "\",\"total_amount\":\"1.00\","
                + "\"trade_no\":\"" + TRADE_NO + "\",\"buyer_open_id\":\"payer-open-id\"}");
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.submit(direct, request)));

        clearInvocations(client);
        var wrongScope = request(direct, PaymentMethod.JSAPI,
                new ChannelPayerIdentityInput(Channel.ALIPAY, PayerIdentityType.OPEN_ID, "payer-open-id", "other-app"), EXPIRY);
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.submit(direct, wrongScope)));
        verifyNoInteractions(client);
    }

    @Test
    void expiryAndContextFailuresAndH5AreRejectedBeforeProtocolIo() {
        var codeRequest = request(direct, PaymentMethod.PAYMENT_CODE,
                new ChannelPaymentCodeInput("2812345678901234"), EXPIRY);
        var tooSoon = request(direct, PaymentMethod.PAYMENT_CODE,
                new ChannelPaymentCodeInput("2812345678901234"), OffsetDateTime.parse("2026-10-23T10:00:59Z"));
        var tooLate = request(direct, PaymentMethod.PAYMENT_CODE,
                new ChannelPaymentCodeInput("2812345678901234"), OffsetDateTime.parse("2026-11-07T10:00:01Z"));
        var h5 = request(direct, PaymentMethod.H5, new NoChannelInput(), EXPIRY);
        var badCode = request(direct, PaymentMethod.PAYMENT_CODE, new ChannelPaymentCodeInput("bad"), EXPIRY);
        var badScope = request(direct, PaymentMethod.JSAPI,
                new ChannelPayerIdentityInput(Channel.ALIPAY, PayerIdentityType.OPEN_ID, "payer-open-id", "other-app"), EXPIRY);

        TenantUtil.runWithTenant("tenant", () -> {
            assertThrows(CommonException.class, () -> adapter.validate(direct, validation(h5)));
            assertThrows(CommonException.class, () -> adapter.validate(direct, validation(badCode)));
            assertThrows(CommonException.class, () -> adapter.validate(direct, validation(badScope)));
            assertThrows(CommonException.class, () -> adapter.validate(direct, validation(tooSoon)));
            assertThrows(CommonException.class, () -> adapter.validate(direct, validation(tooLate)));
        });

        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.submit(direct, tooSoon)));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.submit(direct, tooLate)));

        var wrongTenant = context("revision-1", HASH, AccessMode.DIRECT,
                new AlipayAccountIdentity("main-app", "fixed-seller", null, null, null));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("other", () -> adapter.submit(wrongTenant, codeRequest)));
        var wrongRevision = context("other-revision", HASH, AccessMode.DIRECT,
                new AlipayAccountIdentity("main-app", "fixed-seller", null, null, null));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.submit(wrongRevision, codeRequest)));
        var wrongHash = context("revision-1", "b".repeat(64), AccessMode.DIRECT,
                new AlipayAccountIdentity("main-app", "fixed-seller", null, null, null));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.submit(wrongHash, codeRequest)));

        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.submit(direct, h5)));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.submit(direct, codeRequest)));
        } finally {
            TransactionSynchronizationManager.clear();
        }
        verifyNoInteractions(client);
    }

    @Test
    void channelInputDiagnosticsHidePayerCodeAndResolvedIdentifier() {
        var code = new ChannelPaymentCodeInput("2812345678901234");
        var payer = new ChannelPayerIdentityInput(Channel.ALIPAY, PayerIdentityType.OPEN_ID, "private-open-id", "main-app");
        assertFalse(code.toString().contains("2812345678901234"));
        assertFalse(payer.toString().contains("private-open-id"));
        assertFalse(request(direct, PaymentMethod.JSAPI, payer, EXPIRY).toString().contains("private-open-id"));
    }

    private void stubPay(String payload) {
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.PAY), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.PAY, payload));
    }

    private void stubCreate(String payload) {
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.CREATE), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.CREATE, payload));
    }

    private String requestBody(AlipayMethod method) throws Exception {
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(client, atLeastOnce()).call(eq("tenant"), any(), eq(method), captor.capture(), eq(Map.of()));
        return captor.getValue();
    }

    private static String successQuery(String tradeNo, String amount) {
        return "{\"code\":\"10000\",\"out_trade_no\":\"" + ORDER_NO + "\",\"total_amount\":\"" + amount
                + "\",\"trade_no\":\"" + tradeNo + "\",\"trade_status\":\"TRADE_SUCCESS\","
                + "\"send_pay_date\":\"2026-10-23 18:30:00\"}";
    }

    private static AlipayVerifiedResponse verified(AlipayMethod method, String payload) {
        return new AlipayVerifiedResponse(method, payload, DigestUtils.sha256(payload));
    }

    private static ChannelPaymentRequest request(ChannelExecutionContext context, PaymentMethod method,
            ChannelPaymentInput input, OffsetDateTime payableUntil) {
        var identity = new ChannelPaymentIdentity("payment-1", "attempt-1", Channel.ALIPAY,
                context.binding().id(), context.binding().accountScopeHash(), ORDER_NO);
        var payment = new PreparedChannelPayment(identity, AMOUNT, method, 1, "input-ref");
        return new ChannelPaymentRequest(payment, SUBJECT, payableUntil, input);
    }

    private static ChannelPaymentValidation validation(ChannelPaymentRequest request) {
        var payment = request.payment();
        return new ChannelPaymentValidation(payment.identity(), payment.amount(), payment.method(), request.subject(),
                request.payableUntil(), request.input());
    }

    private static ChannelExecutionContext context(String revision, String hash, AccessMode mode, AlipayAccountIdentity account) {
        var binding = new ChannelBindingRevision(revision, "merchant-1", Channel.ALIPAY, mode, hash,
                "credential-ref", "1", account, Set.of(PaymentMethod.PAYMENT_CODE, PaymentMethod.JSAPI, PaymentMethod.H5));
        return new ChannelExecutionContext("tenant", binding);
    }
}
