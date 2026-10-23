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
import io.github.loadup.pay.channel.api.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AlipayPaymentAdapterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T10:11:12Z"), ZoneOffset.UTC);
    private static final Money AMOUNT = Money.ofMinor(125_000, "CNY");
    private static final String HASH = "a".repeat(64);
    private static final String ORDER_NO = "payment-request-18";
    private static final String TRADE_NO = "ali-trade-18";

    private final AlipayProtocolClient client = mock(AlipayProtocolClient.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final AlipayPaymentAdapter adapter = new AlipayPaymentAdapter(client, json, CLOCK);
    private final ChannelExecutionContext direct = context("tenant", "revision-1", HASH, AccessMode.DIRECT,
            new AlipayAccountIdentity("app", "seller", null, null, null));
    private final ChannelPaymentIdentity identity = identity("revision-1", HASH);
    private final ChannelPaymentTarget target = new ChannelPaymentTarget(identity, AMOUNT);

    @Test
    void queriesWaitingAndPaidTradesWithFrozenIdentityAmountAndBusinessTime() throws Exception {
        var waiting = query("WAIT_BUYER_PAY");
        stubQuery(waiting);
        var accepted = TenantUtil.callWithTenant("tenant", () -> adapter.query(direct, target));
        assertEquals(ChannelPaymentOutcome.Kind.ACCEPTED, accepted.kind());
        assertEquals(identity, accepted.identity());
        assertEquals(AMOUNT, accepted.amount());
        assertEquals(TRADE_NO, accepted.channelTransactionNo());
        assertNull(accepted.succeededAt());
        var request = json.readValue(capturedRequest(AlipayMethod.QUERY), Map.class);
        assertEquals(Map.of("out_trade_no", ORDER_NO), request);

        var paid = paid("TRADE_SUCCESS", "2026-10-23 18:11:12", "CNY");
        stubQuery(paid);
        var succeeded = TenantUtil.callWithTenant("tenant", () -> adapter.query(direct, target));
        assertEquals(ChannelPaymentOutcome.Kind.SUCCEEDED, succeeded.kind());
        assertEquals(identity, succeeded.identity());
        assertEquals(AMOUNT, succeeded.amount());
        assertEquals(TRADE_NO, succeeded.channelTransactionNo());
        assertEquals(LocalDateTime.parse("2026-10-23T10:11:12"), succeeded.succeededAt());

        stubQuery(paid("TRADE_FINISHED", "2026-10-23 18:11:12", null));
        assertEquals(ChannelPaymentOutcome.Kind.SUCCEEDED,
                TenantUtil.callWithTenant("tenant", () -> adapter.query(direct, target)).kind());
    }

    @Test
    void closedUnknownAndNotFoundTradesNeverProveNoCollection() {
        stubQuery(query("TRADE_CLOSED"));
        var closed = TenantUtil.callWithTenant("tenant", () -> adapter.query(direct, target));
        assertEquals(ChannelPaymentOutcome.Kind.UNKNOWN, closed.kind());
        assertNotNull(closed.channelTransactionNo());

        stubQuery("{\"code\":\"10000\",\"out_trade_no\":\"" + ORDER_NO + "\",\"total_amount\":\"1250.00\","
                + "\"trade_no\":\"" + TRADE_NO + "\",\"trade_status\":\"SOMETHING_NEW\"}");
        assertEquals(ChannelPaymentOutcome.Kind.UNKNOWN,
                TenantUtil.callWithTenant("tenant", () -> adapter.query(direct, target)).kind());

        stubQuery("{\"code\":\"40004\",\"sub_code\":\"ACQ.TRADE_NOT_EXIST\"}");
        var missing = TenantUtil.callWithTenant("tenant", () -> adapter.query(direct, target));
        assertEquals(ChannelPaymentOutcome.Kind.UNKNOWN, missing.kind());
        assertNull(missing.channelTransactionNo());
    }

    @Test
    void rejectsMismatchedOrderAmountCurrencyAndMalformedSuccessFacts() {
        assertQueryRejected(paid("TRADE_SUCCESS", "2026-10-23 18:11:12", "CNY").replace(ORDER_NO, "other-order"));
        assertQueryRejected(paid("TRADE_SUCCESS", "2026-10-23 18:11:12", "CNY").replace("1250.00", "1249.99"));
        assertQueryRejected(paid("TRADE_SUCCESS", "2026-10-23 18:11:12", "USD"));
        assertQueryRejected(paid("TRADE_SUCCESS", null, "CNY"));
        assertQueryRejected(paid("TRADE_FINISHED", "not-a-time", "CNY"));
        assertQueryRejected("{\"code\":\"10000\",\"out_trade_no\":\"" + ORDER_NO
                + "\",\"total_amount\":\"1250.00\",\"trade_status\":\"TRADE_SUCCESS\","
                + "\"send_pay_date\":\"2026-10-23 18:11:12\"}");
        assertQueryRejected("{\"code\":\"10000\",\"out_trade_no\":\"" + ORDER_NO
                + "\",\"total_amount\":\"1250.00\",\"trade_no\":\"" + TRADE_NO
                + "\",\"trade_status\":\"TRADE_SUCCESS\",\"send_pay_date\":\"2026-10-23 18:11:12\"}");
        assertQueryRejected("{\"code\":10000,\"out_trade_no\":\"" + ORDER_NO + "\"}");
        assertQueryRejected("{\"code\":\"10000\",\"out_trade_no\":\"" + ORDER_NO
                + "\",\"total_amount\":1250.00,\"trade_no\":\"" + TRADE_NO
                + "\",\"trade_status\":\"TRADE_SUCCESS\",\"send_pay_date\":\"2026-10-23 18:11:12\"}");
    }

    @Test
    void closeDoesNotCallProviderAfterPaidUnknownOrClosedPrequery() {
        stubQuery(paid("TRADE_SUCCESS", "2026-10-23 18:11:12", "CNY"));
        assertEquals(ChannelPaymentOutcome.Kind.SUCCEEDED,
                TenantUtil.callWithTenant("tenant", () -> adapter.close(direct, target)).kind());
        verify(client, times(1)).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.QUERY), anyString(), eq(Map.of()));
        verify(client, never()).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.CLOSE), anyString(), eq(Map.of()));

        clearInvocations(client);
        stubQuery(query("TRADE_CLOSED"));
        assertEquals(ChannelPaymentOutcome.Kind.UNKNOWN,
                TenantUtil.callWithTenant("tenant", () -> adapter.close(direct, target)).kind());
        verify(client, never()).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.CLOSE), anyString(), eq(Map.of()));

        clearInvocations(client);
        stubQuery("{\"code\":\"40004\"}");
        assertEquals(ChannelPaymentOutcome.Kind.UNKNOWN,
                TenantUtil.callWithTenant("tenant", () -> adapter.close(direct, target)).kind());
        verify(client, never()).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.CLOSE), anyString(), eq(Map.of()));
    }

    @Test
    void matchingUnpaidQueryAndCloseProduceNoCollectionWithCompositeEvidence() throws Exception {
        var unpaid = query("WAIT_BUYER_PAY");
        var closePayload = "{\"code\":\"10000\",\"out_trade_no\":\"" + ORDER_NO
                + "\",\"trade_no\":\"" + TRADE_NO + "\"}";
        stubQuery(unpaid);
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.CLOSE), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.CLOSE, closePayload));

        var result = TenantUtil.callWithTenant("tenant", () -> adapter.close(direct, target));

        assertEquals(ChannelPaymentOutcome.Kind.NO_COLLECTION, result.kind());
        assertEquals(PaymentObservation.Source.CLOSE_RESPONSE, result.source());
        assertEquals(identity, result.identity());
        assertEquals(AMOUNT, result.amount());
        assertEquals(TRADE_NO, result.channelTransactionNo());
        var digest = DigestUtils.sha256("alipay-close|" + DigestUtils.sha256(unpaid) + "|" + DigestUtils.sha256(closePayload));
        assertEquals(digest, result.sourceDigest());
        assertEquals("trade-close:" + digest, result.receiptKey());
        var request = json.readValue(capturedRequest(AlipayMethod.CLOSE), Map.class);
        assertEquals(Map.of("out_trade_no", ORDER_NO, "trade_no", TRADE_NO), request);
    }

    @Test
    void closeErrorRequeriesTheSameOrderWhenPaymentWinsRace() {
        var unpaid = query("WAIT_BUYER_PAY");
        var paid = paid("TRADE_SUCCESS", "2026-10-23 18:11:12", "CNY");
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.QUERY), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.QUERY, unpaid), verified(AlipayMethod.QUERY, paid));
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.CLOSE), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.CLOSE, "{\"code\":\"40004\"}"));

        var result = TenantUtil.callWithTenant("tenant", () -> adapter.close(direct, target));

        assertEquals(ChannelPaymentOutcome.Kind.SUCCEEDED, result.kind());
        assertEquals(TRADE_NO, result.channelTransactionNo());
        assertEquals(3, mockingDetails(client).getInvocations().stream().filter(invocation -> invocation.getMethod().getName().equals("call")).count());
    }

    @Test
    void successfulCloseRejectsMismatchedOriginalTradeIdentity() {
        stubQuery(query("WAIT_BUYER_PAY"));
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.CLOSE), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.CLOSE, "{\"code\":\"10000\",\"out_trade_no\":\"other-order\","
                        + "\"trade_no\":\"" + TRADE_NO + "\"}"));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.close(direct, target)));
    }

    @Test
    void rejectsMalformedOrMismatchedCloseResponseAndInvalidBindingBeforeClientIo() {
        stubQuery(query("WAIT_BUYER_PAY"));
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.CLOSE), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.CLOSE, "{\"code\":\"10000\",\"out_trade_no\":\"other\",\"trade_no\":\""
                        + TRADE_NO + "\"}"));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.close(direct, target)));

        clearInvocations(client);
        stubQuery(query("WAIT_BUYER_PAY"));
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.CLOSE), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.CLOSE, "{\"code\":\"10000\",\"out_trade_no\":\"" + ORDER_NO
                        + "\",\"trade_no\":23}"));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.close(direct, target)));

        clearInvocations(client);
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("elsewhere", () -> adapter.query(direct, target)));
        var wrongRevision = context("tenant", "wrong-revision", HASH, AccessMode.DIRECT,
                new AlipayAccountIdentity("app", "seller", null, null, null));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.query(wrongRevision, target)));
        var wrongHash = context("tenant", "revision-1", "b".repeat(64), AccessMode.DIRECT,
                new AlipayAccountIdentity("app", "seller", null, null, null));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.close(wrongHash, target)));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.query(direct, target)));
            assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.close(direct, target)));
        } finally {
            TransactionSynchronizationManager.clear();
        }
        verifyNoInteractions(client);
    }

    @Test
    void acceptsFrozenDirectAndProviderBindings() {
        var providerContext = context("tenant", "provider-revision", HASH, AccessMode.SERVICE_PROVIDER,
                new AlipayAccountIdentity("app", "seller", "operator-app", "authorization-ref", "3"));
        var providerTarget = new ChannelPaymentTarget(identity("provider-revision", HASH), AMOUNT);
        stubQuery("{\"code\":\"40004\"}", providerContext);
        assertEquals(ChannelPaymentOutcome.Kind.UNKNOWN,
                TenantUtil.callWithTenant("tenant", () -> adapter.query(providerContext, providerTarget)).kind());
    }

    @Test
    void recoveryRestoresJsapiActionFromTheOriginalWaitingQueryWithoutResubmitting() throws Exception {
        var payableUntil = java.time.OffsetDateTime.parse("2026-10-23T19:00:00.987+08:00");
        var recovery = recovery(PaymentMethod.JSAPI, payableUntil);
        stubQuery(query("WAIT_BUYER_PAY"));

        var first = TenantUtil.callWithTenant("tenant", () -> adapter.recover(direct, recovery));
        var repeated = TenantUtil.callWithTenant("tenant", () -> adapter.recover(direct, recovery));

        assertEquals(identity, first.identity());
        assertEquals(AMOUNT, first.amount());
        assertEquals(PaymentObservation.Source.QUERY_RESPONSE, first.observation().source());
        assertEquals(ChannelPaymentOutcome.Kind.ACCEPTED, first.observation().kind());
        assertEquals(TRADE_NO, first.observation().channelTransactionNo());
        assertEquals(new AlipayJsapiChannelAction(TRADE_NO, payableUntil.withNano(0)), first.action());
        assertEquals(first, repeated);
        var request = json.readValue(capturedRequest(AlipayMethod.QUERY), Map.class);
        assertEquals(Map.of("out_trade_no", ORDER_NO), request);
        verify(client, times(2)).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.QUERY), anyString(), eq(Map.of()));
        verify(client, never()).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.CREATE), anyString(), eq(Map.of()));
        verify(client, never()).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.PAY), anyString(), eq(Map.of()));
    }

    @Test
    void recoveryOnlyRestoresAnUnexpiredJsapiWaitingAction() {
        var deadline = java.time.OffsetDateTime.parse("2026-10-23T19:00:00+08:00");
        assertNoRecoveryAction(recovery(PaymentMethod.PAYMENT_CODE, deadline), query("WAIT_BUYER_PAY"));
        assertNoRecoveryAction(recovery(PaymentMethod.JSAPI, java.time.OffsetDateTime.parse("2026-10-23T09:00:00Z")),
                query("WAIT_BUYER_PAY"));
        assertNoRecoveryAction(recovery(PaymentMethod.JSAPI, deadline), paid("TRADE_SUCCESS", "2026-10-23 18:11:12", "CNY"));
        assertNoRecoveryAction(recovery(PaymentMethod.JSAPI, deadline), "{\"code\":\"40004\",\"sub_code\":\"ACQ.TRADE_NOT_EXIST\"}");
        assertNoRecoveryAction(recovery(PaymentMethod.JSAPI, deadline), query("TRADE_CLOSED"));
    }

    @Test
    void recoveryRejectsMismatchedFrozenIdentityBeforeProtocolIo() {
        var invalidIdentity = new ChannelPaymentIdentity("payment-1", "attempt-1", Channel.ALIPAY,
                "wrong-revision", HASH, ORDER_NO);
        var invalid = new ChannelPaymentRecovery(invalidIdentity, AMOUNT, PaymentMethod.JSAPI, 1,
                java.time.OffsetDateTime.parse("2026-10-23T19:00:00+08:00"));

        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.recover(direct, invalid)));
        verifyNoInteractions(client);

        clearInvocations(client);
        var validIdentityWrongAmount = new ChannelPaymentRecovery(identity, AMOUNT, PaymentMethod.JSAPI, 1,
                java.time.OffsetDateTime.parse("2026-10-23T19:00:00+08:00"));
        stubQuery(query("WAIT_BUYER_PAY").replace("1250.00", "1249.99"));
        assertThrows(CommonException.class,
                () -> TenantUtil.callWithTenant("tenant", () -> adapter.recover(direct, validIdentityWrongAmount)));
        verify(client).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.QUERY), anyString(), eq(Map.of()));
        verify(client, never()).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.CREATE), anyString(), eq(Map.of()));
        verify(client, never()).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.PAY), anyString(), eq(Map.of()));
    }

    private void assertNoRecoveryAction(ChannelPaymentRecovery recovery, String payload) {
        clearInvocations(client);
        stubQuery(payload);
        var result = TenantUtil.callWithTenant("tenant", () -> adapter.recover(direct, recovery));
        assertEquals(recovery.identity(), result.identity());
        assertEquals(recovery.amount(), result.amount());
        assertEquals(PaymentObservation.Source.QUERY_RESPONSE, result.observation().source());
        assertNull(result.action());
        verify(client, times(1)).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.QUERY), anyString(), eq(Map.of()));
        verify(client, never()).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.CREATE), anyString(), eq(Map.of()));
        verify(client, never()).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.PAY), anyString(), eq(Map.of()));
    }

    private ChannelPaymentRecovery recovery(PaymentMethod method, java.time.OffsetDateTime payableUntil) {
        return new ChannelPaymentRecovery(identity, AMOUNT, method, 1, payableUntil);
    }

    private void assertQueryRejected(String payload) {
        stubQuery(payload);
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.query(direct, target)));
    }

    private void stubQuery(String payload) {
        stubQuery(payload, direct);
    }

    private void stubQuery(String payload, ChannelExecutionContext context) {
        when(client.call(eq(context.tenantId()), eq(context.binding()), eq(AlipayMethod.QUERY), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.QUERY, payload));
    }

    private String capturedRequest(AlipayMethod method) throws Exception {
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(client, atLeastOnce()).call(eq("tenant"), any(), eq(method), captor.capture(), eq(Map.of()));
        return captor.getValue();
    }

    private static String query(String status) {
        return "{\"code\":\"10000\",\"out_trade_no\":\"" + ORDER_NO + "\",\"total_amount\":\"1250.00\","
                + "\"trade_no\":\"" + TRADE_NO + "\",\"trade_status\":\"" + status + "\"}";
    }

    private static String paid(String status, String sendPayDate, String currency) {
        var payload = "{\"code\":\"10000\",\"out_trade_no\":\"" + ORDER_NO + "\",\"total_amount\":\"1250.00\","
                + "\"trade_no\":\"" + TRADE_NO + "\",\"trade_status\":\"" + status + "\"";
        if (currency != null) payload += ",\"trans_currency\":\"" + currency + "\"";
        if (sendPayDate != null) payload += ",\"send_pay_date\":\"" + sendPayDate + "\"";
        return payload + "}";
    }

    private static AlipayVerifiedResponse verified(AlipayMethod method, String payload) {
        return new AlipayVerifiedResponse(method, payload, DigestUtils.sha256(payload));
    }

    private static ChannelExecutionContext context(String tenant, String revision, String hash, AccessMode mode,
            AlipayAccountIdentity account) {
        var binding = new ChannelBindingRevision(revision, "merchant-1", Channel.ALIPAY, mode, hash,
                "credential-ref", "1", account, Set.of(PaymentMethod.PAYMENT_CODE));
        return new ChannelExecutionContext(tenant, binding);
    }

    private static ChannelPaymentIdentity identity(String revision, String hash) {
        return new ChannelPaymentIdentity("payment-1", "attempt-1", Channel.ALIPAY, revision, hash, ORDER_NO);
    }
}
