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
package io.github.loadup.pay.channel.wechat;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.channel.api.model.ChannelRefundOutcome;
import io.github.loadup.pay.channel.api.model.PreparedChannelRefund;
import io.github.loadup.pay.channel.api.model.RefundObservation;
import io.github.loadup.pay.channel.api.model.WechatAccountIdentity;
import io.github.loadup.pay.channel.wechat.protocol.WechatMethod;
import io.github.loadup.pay.channel.wechat.protocol.WechatProtocolClient;
import io.github.loadup.pay.channel.wechat.protocol.WechatVerifiedResponse;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class WechatRefundAdapterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T10:11:12Z"), ZoneId.of("Asia/Shanghai"));
    private static final String HASH = "a".repeat(64);
    private static final String TRANSACTION = "wechat-tx-88";
    private static final String OUT_TRADE = "pay-request-77";
    private static final String REFUND_NO = "refund|request*@12";
    private static final Money REFUND_AMOUNT = Money.ofMinor(10_000, "CNY");
    private static final Money ORIGINAL_AMOUNT = Money.ofMinor(50_000, "CNY");

    private final WechatProtocolClient client = mock(WechatProtocolClient.class);
    private final JsonMapper json = JsonMapper.builder().build();
    private final WechatRefundAdapter adapter = new WechatRefundAdapter(client, json, CLOCK, new io.github.loadup.pay.channel.wechat.config.WechatNotificationProperties(Map.of(), Map.of()));
    private final ChannelExecutionContext direct = context("tenant", "revision-1", HASH, new WechatAccountIdentity("app", "merchant", null, null));
    private final PreparedChannelRefund refund = refund(REFUND_NO, identity("revision-1", HASH), TRANSACTION);

    private static String submitPayload(String status) {
        return "{\"transaction_id\":\"" + TRANSACTION + "\",\"out_trade_no\":\"" + OUT_TRADE
                + "\",\"out_refund_no\":\"" + REFUND_NO + "\",\"refund_id\":\"wechat-refund-88\","
                + "\"amount\":{\"refund\":10000,\"total\":50000,\"currency\":\"CNY\"},"
                + "\"create_time\":\"2026-10-22T23:59:00+08:00\",\"status\":\"" + status + "\"}";
    }

    private static String queryPayload(String status) {
        if (!"SUCCESS".equals(status)) {
            return submitPayload(status);
        }
        return submitPayload(status).replace("\"status\":\"" + status + "\"", "\"status\":\"" + status
                + "\",\"success_time\":\"2026-10-23T10:11:12+00:00\"");
    }

    private static WechatVerifiedResponse verified(WechatMethod method, int status, String payload) {
        return new WechatVerifiedResponse(method, status, payload, "a".repeat(64));
    }

    private static ChannelExecutionContext context(String tenant, String revision, String hash, WechatAccountIdentity account) {
        var binding = new ChannelBindingRevision(revision, "merchant-1", Channel.WECHAT, account.accessMode(), hash,
                "credential-ref", "1", account, Set.of(PaymentMethod.PAYMENT_CODE));
        return new ChannelExecutionContext(tenant, binding);
    }

    private static ChannelPaymentIdentity identity(String revision, String hash) {
        return new ChannelPaymentIdentity("payment-1", "attempt-1", Channel.WECHAT, revision, hash, OUT_TRADE);
    }

    private static PreparedChannelRefund refund(String refundNo, ChannelPaymentIdentity identity, String transaction) {
        return new PreparedChannelRefund(identity, "refund-id-1", refundNo, REFUND_AMOUNT, ORIGINAL_AMOUNT, transaction);
    }

    @Test
    void submitsDirectRefundWithOriginalTransactionAndExactAmountFields() throws Exception {
        when(client.call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND), eq(Map.of()), eq(Map.of()), anyString()))
                .thenReturn(verified(WechatMethod.REFUND, 200, submitPayload("SUCCESS")));

        var result = TenantUtil.callWithTenant("tenant", () -> adapter.submit(direct, refund));

        assertEquals(ChannelRefundOutcome.Kind.ACCEPTED, result.kind());
        assertNull(result.channelRefundTransactionNo());
        var body = json.readValue(capturedBody(WechatMethod.REFUND), Map.class);
        assertEquals(Map.of("transaction_id", TRANSACTION, "out_refund_no", REFUND_NO,
                "amount", Map.of("refund", 10_000, "total", 50_000, "currency", "CNY")), body);
    }

    @Test
    void suppliesOnlyTheTrustedOriginalRevisionRefundUrl() throws Exception {
        var properties = new io.github.loadup.pay.channel.wechat.config.WechatNotificationProperties(Map.of(),
                Map.of("tenant", Map.of("revision-1", java.net.URI.create("https://payments.example/api/payment/channel-callbacks/wx-v1/refund"))));
        var configured = new WechatRefundAdapter(client, json, CLOCK, properties);
        when(client.call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND), eq(Map.of()), eq(Map.of()), anyString()))
                .thenReturn(verified(WechatMethod.REFUND, 200, submitPayload("PROCESSING")));
        TenantUtil.callWithTenant("tenant", () -> configured.submit(direct, refund));
        assertEquals("https://payments.example/api/payment/channel-callbacks/wx-v1/refund",
                json.readValue(capturedBody(WechatMethod.REFUND), Map.class).get("notify_url"));
    }

    @Test
    void submitsAndQueriesPartnerRefundWithSubMerchantScope() throws Exception {
        var partner = context("tenant", "revision-sp", HASH, new WechatAccountIdentity("sp-app", "sp-merchant", "sub-app", "sub-merchant"));
        var partnerRefund = refund(REFUND_NO, identity("revision-sp", HASH), TRANSACTION);
        when(client.call(eq("tenant"), eq(partner.binding()), eq(WechatMethod.REFUND), eq(Map.of()), eq(Map.of()), anyString()))
                .thenReturn(verified(WechatMethod.REFUND, 200, submitPayload("PROCESSING")));
        when(client.call(eq("tenant"), eq(partner.binding()), eq(WechatMethod.REFUND_QUERY), eq(Map.of("out_refund_no", REFUND_NO)),
                eq(Map.of("sub_mchid", List.of("sub-merchant"))), isNull()))
                .thenReturn(verified(WechatMethod.REFUND_QUERY, 200, queryPayload("PROCESSING")));

        assertEquals(ChannelRefundOutcome.Kind.ACCEPTED, TenantUtil.callWithTenant("tenant", () -> adapter.submit(partner, partnerRefund)).kind());
        assertEquals(ChannelRefundOutcome.Kind.ACCEPTED, TenantUtil.callWithTenant("tenant", () -> adapter.query(partner, partnerRefund)).kind());
        var body = json.readValue(capturedBody(WechatMethod.REFUND), Map.class);
        assertEquals("sub-merchant", body.get("sub_mchid"));
        verify(client).call(eq("tenant"), eq(partner.binding()), eq(WechatMethod.REFUND_QUERY),
                eq(Map.of("out_refund_no", REFUND_NO)), eq(Map.of("sub_mchid", List.of("sub-merchant"))), isNull());
    }

    @Test
    void successfulQueryRequiresExactOriginalAmountsAndConvertsTimeToClockZone() {
        when(client.call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND_QUERY), anyMap(), anyMap(), isNull()))
                .thenReturn(verified(WechatMethod.REFUND_QUERY, 200, queryPayload("SUCCESS")));

        var result = TenantUtil.callWithTenant("tenant", () -> adapter.query(direct, refund));

        assertEquals(ChannelRefundOutcome.Kind.SUCCEEDED, result.kind());
        assertEquals("wechat-refund-88", result.channelRefundTransactionNo());
        assertEquals("2026-10-23T18:11:12", result.succeededAt().toString());
        assertEquals("2026-10-22T23:59", result.acceptedAt().toString());
    }

    @Test
    void submissionAndQueryStatusSemanticsKeepNonterminalAndAmbiguousResultsOpen() {
        for (String status : List.of("SUCCESS", "PROCESSING")) {
            when(client.call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND), eq(Map.of()), eq(Map.of()), anyString()))
                    .thenReturn(verified(WechatMethod.REFUND, 200, submitPayload(status)));
            assertEquals(ChannelRefundOutcome.Kind.ACCEPTED,
                    TenantUtil.callWithTenant("tenant", () -> adapter.submit(direct, refund)).kind());
        }
        for (String status : List.of("PROCESSING", "ABNORMAL", "UNKNOWN")) {
            when(client.call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND_QUERY), anyMap(), anyMap(), isNull()))
                    .thenReturn(verified(WechatMethod.REFUND_QUERY, 200, queryPayload(status)));
            var result = TenantUtil.callWithTenant("tenant", () -> adapter.query(direct, refund));
            assertEquals(status.equals("PROCESSING") ? ChannelRefundOutcome.Kind.ACCEPTED : ChannelRefundOutcome.Kind.UNKNOWN, result.kind());
        }
        when(client.call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND_QUERY), anyMap(), anyMap(), isNull()))
                .thenReturn(verified(WechatMethod.REFUND_QUERY, 200, queryPayload("CLOSED")));
        assertEquals(ChannelRefundOutcome.Kind.FAILED_WITH_NO_REFUND,
                TenantUtil.callWithTenant("tenant", () -> adapter.query(direct, refund)).kind());
        when(client.call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND_QUERY), anyMap(), anyMap(), isNull()))
                .thenReturn(verified(WechatMethod.REFUND_QUERY, 404, "{\"code\":\"RESOURCE_NOT_EXISTS\"}"));
        assertEquals(ChannelRefundOutcome.Kind.UNKNOWN,
                TenantUtil.callWithTenant("tenant", () -> adapter.query(direct, refund)).kind());
    }

    @Test
    void recoversDirectAndPartnerRefundOnlyAfterExactVerifiedNotFound() throws Exception {
        var partner = context("tenant", "revision-sp", HASH,
                new WechatAccountIdentity("sp-app", "sp-merchant", "sub-app", "sub-merchant"));
        var partnerRefund = refund(REFUND_NO, identity("revision-sp", HASH), TRANSACTION);
        var properties = new io.github.loadup.pay.channel.wechat.config.WechatNotificationProperties(Map.of(),
                Map.of("tenant", Map.of("revision-1", java.net.URI.create("https://trusted.example/refund"))));
        var configured = new WechatRefundAdapter(client, json, CLOCK, properties);
        var query404 = verified(WechatMethod.REFUND_QUERY, 404, "{\"code\":\"RESOURCE_NOT_EXISTS\"}");
        var submit = verified(WechatMethod.REFUND, 200, submitPayload("PROCESSING"));
        when(client.call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND_QUERY),
                eq(Map.of("out_refund_no", REFUND_NO)), eq(Map.of()), isNull())).thenReturn(query404);
        when(client.call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND), eq(Map.of()), eq(Map.of()), anyString()))
                .thenReturn(submit);

        var directResult = TenantUtil.callWithTenant("tenant", () -> configured.recover(direct, refund));

        assertEquals(ChannelRefundOutcome.Kind.ACCEPTED, directResult.kind());
        assertEquals(RefundObservation.Source.SUBMIT_RESPONSE, directResult.source());
        assertEquals("refund-recovery:" + io.github.loadup.commons.util.DigestUtils.sha256(
                "wechat-refund-recovery-v1|" + query404.sourceDigest() + "|" + submit.sourceDigest()), directResult.receiptKey());
        assertEquals(io.github.loadup.commons.util.DigestUtils.sha256(
                "wechat-refund-recovery-v1|" + query404.sourceDigest() + "|" + submit.sourceDigest()), directResult.sourceDigest());
        var body = json.readValue(capturedBody(WechatMethod.REFUND), Map.class);
        assertEquals(Map.of("transaction_id", TRANSACTION, "out_refund_no", REFUND_NO,
                "amount", Map.of("refund", 10_000, "total", 50_000, "currency", "CNY"),
                "notify_url", "https://trusted.example/refund"), body);
        var order = inOrder(client);
        order.verify(client).call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND_QUERY),
                eq(Map.of("out_refund_no", REFUND_NO)), eq(Map.of()), isNull());
        order.verify(client).call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND), eq(Map.of()), eq(Map.of()), anyString());
        order.verifyNoMoreInteractions();

        reset(client);
        when(client.call(eq("tenant"), eq(partner.binding()), eq(WechatMethod.REFUND_QUERY),
                eq(Map.of("out_refund_no", REFUND_NO)), eq(Map.of("sub_mchid", List.of("sub-merchant"))), isNull()))
                .thenReturn(query404);
        when(client.call(eq("tenant"), eq(partner.binding()), eq(WechatMethod.REFUND), eq(Map.of()), eq(Map.of()), anyString()))
                .thenReturn(submit);
        var partnerResult = TenantUtil.callWithTenant("tenant", () -> configured.recover(partner, partnerRefund));
        assertEquals(ChannelRefundOutcome.Kind.ACCEPTED, partnerResult.kind());
        var partnerBody = json.readValue(capturedBody(WechatMethod.REFUND), Map.class);
        assertEquals("sub-merchant", partnerBody.get("sub_mchid"));
        assertEquals(Map.of("refund", 10_000, "total", 50_000, "currency", "CNY"), partnerBody.get("amount"));
        var partnerOrder = inOrder(client);
        partnerOrder.verify(client).call(eq("tenant"), eq(partner.binding()), eq(WechatMethod.REFUND_QUERY),
                eq(Map.of("out_refund_no", REFUND_NO)), eq(Map.of("sub_mchid", List.of("sub-merchant"))), isNull());
        partnerOrder.verify(client).call(eq("tenant"), eq(partner.binding()), eq(WechatMethod.REFUND), eq(Map.of()), eq(Map.of()), anyString());
        partnerOrder.verifyNoMoreInteractions();
    }

    @Test
    void recoveryNeverResubmitsAnExistingRefundOrUnverifiedNotFound() {
        for (String status : List.of("SUCCESS", "CLOSED", "PROCESSING", "ABNORMAL")) {
            reset(client);
            when(client.call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND_QUERY), anyMap(), anyMap(), isNull()))
                    .thenReturn(verified(WechatMethod.REFUND_QUERY, 200, queryPayload(status)));
            var result = TenantUtil.callWithTenant("tenant", () -> adapter.recover(direct, refund));
            assertEquals(RefundObservation.Source.QUERY_RESPONSE, result.source());
            verify(client, times(1)).call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND_QUERY), anyMap(), anyMap(), isNull());
            verifyNoMoreInteractions(client);
        }
        for (var response : List.of(
                verified(WechatMethod.REFUND_QUERY, 500, "{\"code\":\"RESOURCE_NOT_EXISTS\"}"),
                verified(WechatMethod.REFUND_QUERY, 404, "{\"code\":\"OTHER_ERROR\"}"),
                verified(WechatMethod.REFUND_QUERY, 200, "{\"code\":\"RESOURCE_NOT_EXISTS\"}"))) {
            reset(client);
            when(client.call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND_QUERY), anyMap(), anyMap(), isNull()))
                    .thenReturn(response);
            var result = TenantUtil.callWithTenant("tenant", () -> adapter.recover(direct, refund));
            assertEquals(RefundObservation.Source.QUERY_RESPONSE, result.source());
            verify(client, times(1)).call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND_QUERY), anyMap(), anyMap(), isNull());
            verifyNoMoreInteractions(client);
        }
        reset(client);
        when(client.call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND_QUERY), anyMap(), anyMap(), isNull()))
                .thenReturn(verified(WechatMethod.REFUND_QUERY, 404, "not-json"));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.recover(direct, refund)));
        verify(client, times(1)).call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND_QUERY), anyMap(), anyMap(), isNull());
        verifyNoMoreInteractions(client);
    }

    @Test
    void recoveryDoesNotSubmitWhenMetadataBindingSignatureOrQueryCallIsInvalid() {
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.recover(direct, null)));
        var wrongBinding = context("tenant", "other-revision", HASH, new WechatAccountIdentity("app", "merchant", null, null));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.recover(wrongBinding, refund)));
        verifyNoInteractions(client);

        when(client.call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND_QUERY), anyMap(), anyMap(), isNull()))
                .thenReturn(new WechatVerifiedResponse(WechatMethod.REFUND, 404, "{\"code\":\"RESOURCE_NOT_EXISTS\"}", HASH));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.recover(direct, refund)));
        verify(client, times(1)).call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND_QUERY), anyMap(), anyMap(), isNull());
        verifyNoMoreInteractions(client);

        reset(client);
        when(client.call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND_QUERY), anyMap(), anyMap(), isNull()))
                .thenThrow(new CommonException(io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.recover(direct, refund)));
        verify(client, times(1)).call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND_QUERY), anyMap(), anyMap(), isNull());
        verifyNoMoreInteractions(client);
    }

    @Test
    void plainQueryNeverSubmitsAfterVerifiedNotFound() {
        when(client.call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND_QUERY), anyMap(), anyMap(), isNull()))
                .thenReturn(verified(WechatMethod.REFUND_QUERY, 404, "{\"code\":\"RESOURCE_NOT_EXISTS\"}"));

        var result = TenantUtil.callWithTenant("tenant", () -> adapter.query(direct, refund));

        assertEquals(ChannelRefundOutcome.Kind.UNKNOWN, result.kind());
        assertEquals(RefundObservation.Source.QUERY_RESPONSE, result.source());
        verify(client, times(1)).call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND_QUERY), anyMap(), anyMap(), isNull());
        verifyNoMoreInteractions(client);
    }

    @Test
    void rejectsWrongIdentityAndNonIntegerOrWrongGrossAmounts() {
        assertInvalidQuery(queryPayload("SUCCESS").replace(TRANSACTION, "other-tx"));
        assertInvalidQuery(queryPayload("SUCCESS").replace(OUT_TRADE, "other-order"));
        assertInvalidQuery(queryPayload("SUCCESS").replace(REFUND_NO, "other-refund"));
        assertInvalidQuery(queryPayload("SUCCESS").replace("\"total\":50000", "\"total\":49999"));
        assertInvalidQuery(queryPayload("SUCCESS").replace("\"total\":50000", "\"total\":50000.0"));
        assertInvalidQuery(queryPayload("SUCCESS").replace("\"total\":50000", "\"total\":\"50000\""));
        assertInvalidQuery(queryPayload("SUCCESS").replace("\"refund\":10000", "\"refund\":9999,\"payer_refund\":10000"));
        assertInvalidQuery(queryPayload("SUCCESS").replace("\"refund\":10000", "\"refund\":10000.0"));
        assertInvalidQuery(queryPayload("SUCCESS").replace("\"currency\":\"CNY\"", "\"currency\":\"USD\""));
        assertInvalidQuery(submitPayload("SUCCESS"));
        assertInvalidQuery(queryPayload("CLOSED").replace("\"status\":\"CLOSED\"", "\"status\":\"CLOSED\",\"success_time\":\"2026-10-23T10:11:12Z\""));
    }

    @Test
    void handlesInt64AmountsAndIgnoresDiscountedPayerAmountsForGrossEvidence() {
        var large = new PreparedChannelRefund(refund.original(), "refund-large", REFUND_NO,
                Money.ofMinor(2_500_000_000L, "CNY"), Money.ofMinor(3_000_000_000L, "CNY"), TRANSACTION);
        var payload = queryPayload("SUCCESS").replace("\"refund\":10000", "\"refund\":2500000000,\"payer_refund\":2000000000")
                .replace("\"total\":50000", "\"total\":3000000000,\"payer_total\":2200000000");
        when(client.call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND_QUERY), anyMap(), anyMap(), isNull()))
                .thenReturn(verified(WechatMethod.REFUND_QUERY, 200, payload));
        assertTrue(adapter.canRefund(large));
        var result = TenantUtil.callWithTenant("tenant", () -> adapter.query(direct, large));
        assertEquals(ChannelRefundOutcome.Kind.SUCCEEDED, result.kind());
        assertEquals(2_500_000_000L, result.amount().getCent());
    }

    @Test
    void rejectsWrongTenantBindingAndCallsMadeInsideTransactionBeforeNetwork() {
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("other", () -> adapter.query(direct, refund)));
        var wrongBinding = context("tenant", "other-revision", HASH, new WechatAccountIdentity("app", "merchant", null, null));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.submit(wrongBinding, refund)));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.submit(direct, refund)));
            assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.query(direct, refund)));
        } finally {
            TransactionSynchronizationManager.clear();
        }
        verifyNoInteractions(client);
    }

    @Test
    void canRefundIsPureMetadataAndSafeInsideLocalTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertTrue(adapter.canRefund(refund));
            assertFalse(adapter.canRefund(null));
        } finally {
            TransactionSynchronizationManager.clear();
        }
        verifyNoInteractions(client);
    }

    @Test
    void acceptanceMetadataComesFromVerifiedCreateTimeAndCannotReplaceSuccessTime() {
        when(client.call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND), anyMap(), anyMap(), anyString()))
                .thenReturn(verified(WechatMethod.REFUND, 200, submitPayload("PROCESSING")));
        var accepted = TenantUtil.callWithTenant("tenant", () -> adapter.submit(direct, refund));
        assertEquals(ChannelRefundOutcome.Kind.ACCEPTED, accepted.kind());
        assertEquals("2026-10-22T23:59", accepted.acceptedAt().toString());
        org.junit.jupiter.api.Assertions.assertNull(accepted.succeededAt());
        assertInvalidQuery(queryPayload("SUCCESS").replace(",\"create_time\":\"2026-10-22T23:59:00+08:00\"", ""));
        assertInvalidQuery(queryPayload("SUCCESS").replace("2026-10-22T23:59:00+08:00", "2026-10-24T10:00:00+08:00"));
        assertInvalidQuery(queryPayload("SUCCESS").replace("2026-10-22T23:59:00+08:00", "not-a-time"));
    }

    private void assertInvalidQuery(String payload) {
        when(client.call(eq("tenant"), eq(direct.binding()), eq(WechatMethod.REFUND_QUERY), anyMap(), anyMap(), isNull()))
                .thenReturn(verified(WechatMethod.REFUND_QUERY, 200, payload));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.query(direct, refund)));
    }

    private String capturedBody(WechatMethod method) {
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(client, atLeastOnce()).call(eq("tenant"), any(), eq(method), anyMap(), anyMap(), captor.capture());
        return captor.getValue();
    }
}
