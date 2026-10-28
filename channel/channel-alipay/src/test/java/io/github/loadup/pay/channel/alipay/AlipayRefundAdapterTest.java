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
import io.github.loadup.pay.channel.alipay.protocol.AlipayProtocolTime;
import io.github.loadup.pay.channel.alipay.protocol.AlipayVerifiedResponse;
import io.github.loadup.pay.channel.api.model.AlipayAccountIdentity;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.channel.api.model.ChannelRefundOutcome;
import io.github.loadup.pay.channel.api.model.PreparedChannelRefund;
import io.github.loadup.pay.channel.api.model.RefundObservation;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AlipayRefundAdapterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T10:11:12Z"), ZoneOffset.UTC);
    private static final String HASH = "a".repeat(64);
    private static final String TRADE_NO = "ali-trade-88";
    private static final String OUT_TRADE_NO = "pay-request-77";
    private static final String REFUND_NO = "refund-request-12";
    private static final Money REFUND_AMOUNT = Money.ofMinor(10_000, "CNY");
    private static final Money ORIGINAL_AMOUNT = Money.ofMinor(50_000, "CNY");

    private final AlipayProtocolClient client = mock(AlipayProtocolClient.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final AlipayRefundAdapter adapter = new AlipayRefundAdapter(client, json, CLOCK);
    private final ChannelExecutionContext direct = context("tenant", "revision-1", HASH, AccessMode.DIRECT,
            new AlipayAccountIdentity("app", "seller", null, null, null));
    private final PreparedChannelRefund refund = refund(REFUND_NO, identity("revision-1", HASH), TRADE_NO);

    private static String successPayload(String trade, String outTrade, String outRequest,
                                         String refundAmount, String totalAmount, String time) {
        return "{\"code\":\"10000\",\"refund_status\":\"REFUND_SUCCESS\",\"trade_no\":\"" + trade
                + "\",\"out_trade_no\":\"" + outTrade + "\",\"out_request_no\":\"" + outRequest
                + "\",\"refund_amount\":\"" + refundAmount + "\",\"total_amount\":\"" + totalAmount
                + "\",\"gmt_refund_pay\":\"" + time + "\"}";
    }

    private static String recoveryPayload(String code, String status, String trade, String outTrade, String outRequest,
                                          String refundAmount, String totalAmount) {
        var payload = new StringBuilder("{\"code\":\"").append(code).append('"');
        if (status != null) payload.append(",\"refund_status\":\"").append(status).append('"');
        if (trade != null) payload.append(",\"trade_no\":\"").append(trade).append('"');
        if (outTrade != null) payload.append(",\"out_trade_no\":\"").append(outTrade).append('"');
        if (outRequest != null) payload.append(",\"out_request_no\":\"").append(outRequest).append('"');
        if (refundAmount != null) payload.append(",\"refund_amount\":\"").append(refundAmount).append('"');
        if (totalAmount != null) payload.append(",\"total_amount\":\"").append(totalAmount).append('"');
        return payload.append('}').toString();
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
        return new ChannelPaymentIdentity("payment-1", "attempt-1", Channel.ALIPAY, revision, hash, OUT_TRADE_NO);
    }

    private static PreparedChannelRefund refund(String refundNo, ChannelPaymentIdentity identity, String tradeNo) {
        return new PreparedChannelRefund(identity, "refund-id-1", refundNo, REFUND_AMOUNT, ORIGINAL_AMOUNT, tradeNo);
    }

    @Test
    void submitsExactOriginalTradeAndRefundFieldsAndKeepsAcceptedStatusNonterminal() throws Exception {
        var payload = "{\"code\":\"10000\",\"trade_no\":\"" + TRADE_NO + "\",\"out_trade_no\":\""
                + OUT_TRADE_NO + "\",\"out_request_no\":\"" + REFUND_NO
                + "\",\"refund_fee\":\"300.00\",\"fund_change\":\"Y\"}";
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.REFUND, payload));

        var result = TenantUtil.callWithTenant("tenant", () -> adapter.submit(direct, refund));

        assertEquals(ChannelRefundOutcome.Kind.ACCEPTED, result.kind());
        assertEquals(REFUND_AMOUNT, result.amount());
        assertNull(result.channelRefundTransactionNo());
        var request = json.readValue(capturedRequest(AlipayMethod.REFUND), Map.class);
        assertEquals(TRADE_NO, request.get("trade_no"));
        assertEquals(OUT_TRADE_NO, request.get("out_trade_no"));
        assertEquals(REFUND_NO, request.get("out_request_no"));
        assertEquals("100.00", request.get("refund_amount"));
        verify(client).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND), anyString(), eq(Map.of()));
    }

    @Test
    void onlyVerifiedSuccessfulOriginalRequestQueryProducesTerminalObservation() throws Exception {
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND_QUERY), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.REFUND_QUERY, successPayload(TRADE_NO, OUT_TRADE_NO, REFUND_NO,
                        "100.00", "500.00", "2026-10-23 18:11:12")));

        var result = TenantUtil.callWithTenant("tenant", () -> adapter.query(direct, refund));

        assertEquals(ChannelRefundOutcome.Kind.SUCCEEDED, result.kind());
        assertEquals(REFUND_AMOUNT, result.amount());
        assertEquals("alipay:" + DigestUtils.sha256("12:" + TRADE_NO + REFUND_NO.length() + ":" + REFUND_NO),
                result.channelRefundTransactionNo());
        assertEquals(AlipayProtocolTime.businessTime("2026-10-23 18:11:12", CLOCK.getZone()), result.succeededAt());
        var request = json.readValue(capturedRequest(AlipayMethod.REFUND_QUERY), Map.class);
        assertEquals(TRADE_NO, request.get("trade_no"));
        assertEquals(OUT_TRADE_NO, request.get("out_trade_no"));
        assertEquals(REFUND_NO, request.get("out_request_no"));
        assertEquals(java.util.List.of("gmt_refund_pay"), request.get("query_options"));
    }

    @Test
    void missingUnknownOrNotFoundStatusRemainsUnknown() {
        assertUnknown("{\"code\":\"10000\",\"trade_no\":\"" + TRADE_NO + "\",\"out_trade_no\":\""
                + OUT_TRADE_NO + "\"}");
        assertUnknown("{\"code\":\"10000\",\"refund_status\":\"PROCESSING\"}");
        assertUnknown("{\"code\":\"40004\",\"sub_code\":\"ACQ.TRADE_NOT_EXIST\"}");
    }

    @Test
    void recoveryResubmitsOnlyEligibleOriginalRequestAndPreservesNaturalIdempotencyKey() throws Exception {
        var lookupPayload = recoveryPayload("10000", null, TRADE_NO, OUT_TRADE_NO, REFUND_NO, "0.00", "500.00");
        var query = verified(AlipayMethod.REFUND_QUERY, lookupPayload);
        var submitPayload = "{\"code\":\"10000\",\"trade_no\":\"" + TRADE_NO + "\",\"out_trade_no\":\""
                + OUT_TRADE_NO + "\",\"out_request_no\":\"" + REFUND_NO + "\"}";
        var submit = verified(AlipayMethod.REFUND, submitPayload);
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND_QUERY), anyString(), eq(Map.of())))
                .thenReturn(query);
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND), anyString(), eq(Map.of())))
                .thenReturn(submit);

        var result = TenantUtil.callWithTenant("tenant", () -> adapter.recover(direct, refund));

        assertEquals(ChannelRefundOutcome.Kind.ACCEPTED, result.kind());
        assertEquals(RefundObservation.Source.SUBMIT_RESPONSE, result.source());
        var digest = DigestUtils.sha256("alipay-refund-recovery-v1|" + query.sourceDigest() + "|" + submit.sourceDigest());
        assertEquals(digest, result.sourceDigest());
        assertEquals("refund-recovery:" + digest, result.receiptKey());
        var request = json.readValue(capturedRequest(AlipayMethod.REFUND), Map.class);
        assertEquals(Map.of("trade_no", TRADE_NO, "out_trade_no", OUT_TRADE_NO,
                "out_request_no", REFUND_NO, "refund_amount", "100.00"), request);
        var order = inOrder(client);
        order.verify(client).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND_QUERY), anyString(), eq(Map.of()));
        order.verify(client).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND), anyString(), eq(Map.of()));
        order.verifyNoMoreInteractions();
    }

    @Test
    void recoveryAcceptsOnlyZeroOrOriginalRefundAmountAndOriginalGrossTotal() {
        for (String amount : List.of("0", "0.0", "0.00", "100.00")) {
            reset(client);
            when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND_QUERY), anyString(), eq(Map.of())))
                    .thenReturn(verified(AlipayMethod.REFUND_QUERY,
                            recoveryPayload("10000", null, TRADE_NO, OUT_TRADE_NO, REFUND_NO, amount, "500.00")));
            when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND), anyString(), eq(Map.of())))
                    .thenReturn(verified(AlipayMethod.REFUND, "{\"code\":\"40004\"}"));
            assertEquals(ChannelRefundOutcome.Kind.UNKNOWN,
                    TenantUtil.callWithTenant("tenant", () -> adapter.recover(direct, refund)).kind());
            verify(client, times(1)).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND_QUERY), anyString(), eq(Map.of()));
            verify(client, times(1)).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND), anyString(), eq(Map.of()));
            verifyNoMoreInteractions(client);
        }

        var providerContext = context("tenant", "revision-provider", HASH, AccessMode.SERVICE_PROVIDER,
                new AlipayAccountIdentity("app", "seller", "operator", "auth-ref", "2"));
        var providerRefund = refund(REFUND_NO, identity("revision-provider", HASH), TRADE_NO);
        reset(client);
        when(client.call(eq("tenant"), eq(providerContext.binding()), eq(AlipayMethod.REFUND_QUERY), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.REFUND_QUERY,
                        recoveryPayload("10000", null, TRADE_NO, OUT_TRADE_NO, REFUND_NO, null, "500.00")));
        when(client.call(eq("tenant"), eq(providerContext.binding()), eq(AlipayMethod.REFUND), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.REFUND, "{\"code\":\"40004\"}"));
        assertEquals(ChannelRefundOutcome.Kind.UNKNOWN,
                TenantUtil.callWithTenant("tenant", () -> adapter.recover(providerContext, providerRefund)).kind());
        var providerOrder = inOrder(client);
        providerOrder.verify(client).call(eq("tenant"), eq(providerContext.binding()), eq(AlipayMethod.REFUND_QUERY), anyString(), eq(Map.of()));
        providerOrder.verify(client).call(eq("tenant"), eq(providerContext.binding()), eq(AlipayMethod.REFUND), anyString(), eq(Map.of()));
        providerOrder.verifyNoMoreInteractions();
    }

    @Test
    void existingSuccessMissingIdentityBusinessErrorsAndExplicitStatusesNeverResubmit() {
        var responses = List.of(
                verified(AlipayMethod.REFUND_QUERY, successPayload(TRADE_NO, OUT_TRADE_NO, REFUND_NO,
                        "100.00", "500.00", "2026-10-23 10:11:12")),
                verified(AlipayMethod.REFUND_QUERY, recoveryPayload("10000", null, TRADE_NO, OUT_TRADE_NO, null, "0.00", "500.00")),
                verified(AlipayMethod.REFUND_QUERY, recoveryPayload("40004", null, TRADE_NO, OUT_TRADE_NO, REFUND_NO, null, null)),
                verified(AlipayMethod.REFUND_QUERY, recoveryPayload("10000", "REFUND_PROCESSING", TRADE_NO, OUT_TRADE_NO, REFUND_NO, null, null)),
                verified(AlipayMethod.REFUND_QUERY, recoveryPayload("10000", "REFUND_CLOSED", TRADE_NO, OUT_TRADE_NO, REFUND_NO, null, null)));
        for (var response : responses) {
            reset(client);
            when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND_QUERY), anyString(), eq(Map.of())))
                    .thenReturn(response);
            var result = TenantUtil.callWithTenant("tenant", () -> adapter.recover(direct, refund));
            assertEquals(RefundObservation.Source.QUERY_RESPONSE, result.source());
            if (response.payload().contains("REFUND_SUCCESS"))
                assertEquals(ChannelRefundOutcome.Kind.SUCCEEDED, result.kind());
            else assertEquals(ChannelRefundOutcome.Kind.UNKNOWN, result.kind());
            verify(client, times(1)).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND_QUERY), anyString(), eq(Map.of()));
            verifyNoMoreInteractions(client);
        }
    }

    @Test
    void invalidRecoveryEvidenceAndLookupFailuresNeverResubmit() {
        for (var payload : List.of(
                recoveryPayload("10000", null, TRADE_NO, OUT_TRADE_NO, REFUND_NO, "99.99", "500.00"),
                recoveryPayload("10000", null, TRADE_NO, OUT_TRADE_NO, REFUND_NO, "0.00", "499.99"))) {
            reset(client);
            when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND_QUERY), anyString(), eq(Map.of())))
                    .thenReturn(verified(AlipayMethod.REFUND_QUERY, payload));
            assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.recover(direct, refund)));
            verify(client, times(1)).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND_QUERY), anyString(), eq(Map.of()));
            verifyNoMoreInteractions(client);
        }

        var identityMismatch = recoveryPayload("10000", null, "other-trade", OUT_TRADE_NO, REFUND_NO, "0.00", "500.00");
        reset(client);
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND_QUERY), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.REFUND_QUERY, identityMismatch));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.recover(direct, refund)));
        verify(client, times(1)).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND_QUERY), anyString(), eq(Map.of()));
        verifyNoMoreInteractions(client);

        var validPayload = recoveryPayload("10000", null, TRADE_NO, OUT_TRADE_NO, REFUND_NO, "0.00", "500.00");
        reset(client);
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND_QUERY), anyString(), eq(Map.of())))
                .thenReturn(new AlipayVerifiedResponse(AlipayMethod.REFUND_QUERY, validPayload, "b".repeat(64)));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.recover(direct, refund)));
        verify(client, times(1)).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND_QUERY), anyString(), eq(Map.of()));
        verifyNoMoreInteractions(client);

        reset(client);
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND_QUERY), anyString(), eq(Map.of())))
                .thenThrow(new CommonException(io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.recover(direct, refund)));
        verify(client, times(1)).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND_QUERY), anyString(), eq(Map.of()));
        verifyNoMoreInteractions(client);
    }

    @Test
    void plainQueryNeverResubmitsAnOtherwiseEligibleRecoveryResponse() {
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND_QUERY), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.REFUND_QUERY,
                        recoveryPayload("10000", null, TRADE_NO, OUT_TRADE_NO, REFUND_NO, "0.00", "500.00")));

        var result = TenantUtil.callWithTenant("tenant", () -> adapter.query(direct, refund));

        assertEquals(ChannelRefundOutcome.Kind.UNKNOWN, result.kind());
        assertEquals(RefundObservation.Source.QUERY_RESPONSE, result.source());
        verify(client, times(1)).call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND_QUERY), anyString(), eq(Map.of()));
        verifyNoMoreInteractions(client);
    }

    @Test
    void rejectsMismatchedIdentityAmountsTotalsInvalidTimesAndMalformedFields() {
        assertInvalidQuery(successPayload("other-trade", OUT_TRADE_NO, REFUND_NO, "100.00", "500.00", "2026-10-23 18:11:12"));
        assertInvalidQuery(successPayload(TRADE_NO, "other-order", REFUND_NO, "100.00", "500.00", "2026-10-23 18:11:12"));
        assertInvalidQuery(successPayload(TRADE_NO, OUT_TRADE_NO, "other-refund", "100.00", "500.00", "2026-10-23 18:11:12"));
        assertInvalidQuery(successPayload(TRADE_NO, OUT_TRADE_NO, REFUND_NO, "99.99", "500.00", "2026-10-23 18:11:12"));
        assertInvalidQuery(successPayload(TRADE_NO, OUT_TRADE_NO, REFUND_NO, "100.00", "499.00", "2026-10-23 18:11:12"));
        assertInvalidQuery(successPayload(TRADE_NO, OUT_TRADE_NO, REFUND_NO, "100.00", "500.00", "not-a-time"));
        assertInvalidQuery("{\"code\":\"10000\",\"refund_status\":\"REFUND_SUCCESS\",\"trade_no\":23}");
        assertInvalidQuery("{\"code\":\"10000\",\"refund_status\":\"REFUND_SUCCESS\",\"trade_no\":\""
                + TRADE_NO + "\",\"out_trade_no\":\"" + OUT_TRADE_NO + "\",\"out_request_no\":\""
                + REFUND_NO + "\",\"refund_amount\":100}");
    }

    @Test
    void refundReferenceIsStableForNaturalKeyAndDistinctAcrossRefundRequests() {
        stubSuccess(REFUND_NO);
        var first = TenantUtil.callWithTenant("tenant", () -> adapter.query(direct, refund));
        var repeatedRequest = refund(REFUND_NO, refund.original(), TRADE_NO);
        var repeated = TenantUtil.callWithTenant("tenant", () -> adapter.query(direct, repeatedRequest));
        var anotherRefundNo = "refund-request-13";
        stubSuccess(anotherRefundNo);
        var different = TenantUtil.callWithTenant("tenant", () -> adapter.query(direct,
                refund(anotherRefundNo, refund.original(), TRADE_NO)));
        assertEquals(first.channelRefundTransactionNo(), repeated.channelRefundTransactionNo());
        assertNotEquals(first.channelRefundTransactionNo(), different.channelRefundTransactionNo());
    }

    @Test
    void acceptsDirectAndServiceProviderAlipayBindingsAndRejectsInvalidContextBeforeClientIo() {
        var providerContext = context("tenant", "revision-provider", HASH, AccessMode.SERVICE_PROVIDER,
                new AlipayAccountIdentity("app", "seller", "operator", "auth-ref", "2"));
        when(client.call(eq("tenant"), eq(providerContext.binding()), eq(AlipayMethod.REFUND_QUERY), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.REFUND_QUERY, "{\"code\":\"40004\"}"));
        var providerRefund = refund(REFUND_NO, identity("revision-provider", HASH), TRADE_NO);
        assertEquals(ChannelRefundOutcome.Kind.UNKNOWN,
                TenantUtil.callWithTenant("tenant", () -> adapter.query(providerContext, providerRefund)).kind());

        clearInvocations(client);
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("other", () -> adapter.query(direct, refund)));
        var wrongRevision = context("tenant", "other-revision", HASH, AccessMode.DIRECT,
                new AlipayAccountIdentity("app", "seller", null, null, null));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.query(wrongRevision, refund)));
        var wrongHash = context("tenant", "revision-1", "b".repeat(64), AccessMode.DIRECT,
                new AlipayAccountIdentity("app", "seller", null, null, null));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.submit(wrongHash, refund)));

        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.submit(direct, refund)));
            assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.query(direct, refund)));
        } finally {
            TransactionSynchronizationManager.clear();
        }
        verifyNoInteractions(client);
    }

    private void assertUnknown(String payload) {
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND_QUERY), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.REFUND_QUERY, payload));
        var result = TenantUtil.callWithTenant("tenant", () -> adapter.query(direct, refund));
        assertEquals(ChannelRefundOutcome.Kind.UNKNOWN, result.kind());
        assertNull(result.channelRefundTransactionNo());
        assertNull(result.succeededAt());
    }

    private void assertInvalidQuery(String payload) {
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND_QUERY), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.REFUND_QUERY, payload));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.query(direct, refund)));
    }

    private void stubSuccess(String refundNo) {
        when(client.call(eq("tenant"), eq(direct.binding()), eq(AlipayMethod.REFUND_QUERY), anyString(), eq(Map.of())))
                .thenReturn(verified(AlipayMethod.REFUND_QUERY,
                        successPayload(TRADE_NO, OUT_TRADE_NO, refundNo, "100.00", "500.00", "2026-10-23 18:11:12")));
    }

    private String capturedRequest(AlipayMethod method) throws Exception {
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(client, atLeastOnce()).call(eq("tenant"), any(), eq(method), captor.capture(), eq(Map.of()));
        return captor.getValue();
    }
}
