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
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import io.github.loadup.pay.channel.api.model.ChannelPaymentCodeInput;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.channel.api.model.ChannelPaymentOutcome;
import io.github.loadup.pay.channel.api.model.ChannelPaymentRequest;
import io.github.loadup.pay.channel.api.model.ChannelPaymentTarget;
import io.github.loadup.pay.channel.api.model.ChannelPaymentValidation;
import io.github.loadup.pay.channel.api.model.PaymentObservation;
import io.github.loadup.pay.channel.api.model.PreparedChannelPayment;
import io.github.loadup.pay.channel.api.model.WechatAccountIdentity;
import io.github.loadup.pay.channel.wechat.config.WechatCodepayProperties;
import io.github.loadup.pay.channel.wechat.protocol.WechatV2Client;
import io.github.loadup.pay.channel.wechat.protocol.WechatV2Method;
import io.github.loadup.pay.channel.wechat.protocol.WechatV2VerifiedResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WechatCodepayAdapterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T03:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final WechatV2Client client = mock(WechatV2Client.class);
    private final WechatPaymentAdapter queries = mock(WechatPaymentAdapter.class);
    private final WechatCodepayProperties properties = new WechatCodepayProperties(Map.of("tenant", Map.of("binding", "203.0.113.10")));
    private final WechatCodepayAdapter adapter = new WechatCodepayAdapter(client, queries, properties, CLOCK);

    @Test
    void directAndPartnerUseFrozenV2IdentitiesAndOriginalGrossAmountThenCanonicalQuery() {
        for (boolean partner : List.of(false, true)) {
            var context = context(partner);
            var request = request(context, "123456789012345678", 1250, "Original meal", OffsetDateTime.now(CLOCK).plusHours(1));
            var fields = response(context, "SUCCESS");
            fields.put("out_trade_no", "original_01");
            fields.put("transaction_id", "4200000000001");
            fields.put("total_fee", "1250");
            fields.put("fee_type", "CNY");
            fields.put("trade_type", "MICROPAY");
            var verified = new WechatV2VerifiedResponse(WechatV2Method.MICROPAY, fields, "a".repeat(64));
            when(client.call(eq("tenant"), eq(context.binding()), eq(WechatV2Method.MICROPAY), anyMap())).thenReturn(verified);
            var observed = new PaymentObservation(request.payment().identity(), ChannelPaymentOutcome.Kind.SUCCEEDED,
                    request.payment().amount(), "4200000000001", LocalDateTime.of(2026, 10, 27, 11, 0),
                    PaymentObservation.Source.QUERY_RESPONSE, "query", "b".repeat(64));
            when(queries.query(context, new ChannelPaymentTarget(request.payment().identity(), request.payment().amount()))).thenReturn(observed);

            TenantUtil.runWithTenant("tenant", () -> {
                adapter.validate(context, validation(request));
                var result = adapter.submit(context, request);
                assertSame(observed, result.observation());
                assertNull(result.action());
            });
            var body = ArgumentCaptor.forClass(Map.class);
            verify(client).call(eq("tenant"), eq(context.binding()), eq(WechatV2Method.MICROPAY), body.capture());
            assertEquals("wx-main", body.getValue().get("appid"));
            assertEquals("1900000001", body.getValue().get("mch_id"));
            assertEquals(partner ? "1900000002" : null, body.getValue().get("sub_mch_id"));
            assertEquals(partner ? "wx-sub" : null, body.getValue().get("sub_appid"));
            assertEquals("original_01", body.getValue().get("out_trade_no"));
            assertEquals("1250", body.getValue().get("total_fee"));
            assertEquals("123456789012345678", body.getValue().get("auth_code"));
            assertEquals("203.0.113.10", body.getValue().get("spbill_create_ip"));
            assertEquals("20261027120000", body.getValue().get("time_expire"));
            assertEquals("HMAC-SHA256", body.getValue().get("sign_type"));
            assertFalse(body.getValue().containsKey("notify_url"));
            clearInvocations(client, queries);
        }
    }

    @Test
    void signedUserPayingIsPendingAndOtherBusinessErrorsNeverReleaseCapacity() {
        var context = context(false);
        var request = request(context, "123456789012345678", 1250, "Meal", OffsetDateTime.now(CLOCK).plusHours(1));
        for (String error : List.of("USERPAYING", "SYSTEMERROR", "AUTHCODEEXPIRE", "ORDERPAID")) {
            var fields = response(context, "FAIL");
            fields.put("err_code", error);
            when(client.call(anyString(), any(), any(), anyMap()))
                    .thenReturn(new WechatV2VerifiedResponse(WechatV2Method.MICROPAY, fields, "a".repeat(64)));
            var result = TenantUtil.callWithTenant("tenant", () -> adapter.submit(context, request));
            assertEquals(error.equals("USERPAYING") ? ChannelPaymentOutcome.Kind.ACCEPTED : ChannelPaymentOutcome.Kind.UNKNOWN,
                    result.observation().kind());
            assertEquals(PaymentObservation.Source.SUBMIT_RESPONSE, result.observation().source());
            assertNull(result.observation().succeededAt());
            assertNull(result.action());
        }
        verifyNoInteractions(queries);
    }

    @Test
    void validationRejectsInvalidCodeV2TextAmountAndMissingServerIpBeforeAnyIo() {
        var context = context(false);
        var until = OffsetDateTime.now(CLOCK).plusHours(1);
        for (var invalid : List.of(request(context, "163456789012345678", 1250, "Meal", until),
                request(context, "12345678901234567", 1250, "Meal", until),
                request(context, "123456789012345678", (long) Integer.MAX_VALUE + 1, "Meal", until),
                request(context, "123456789012345678", 1250, "Meal \uD83D\uDE00", until),
                request(context, "123456789012345678", 1250, "Meal", OffsetDateTime.now(CLOCK).plusSeconds(59)))) {
            assertThrows(CommonException.class, () -> TenantUtil.runWithTenant("tenant", () -> adapter.validate(context, validation(invalid))));
        }
        var missing = new WechatCodepayAdapter(client, queries, new WechatCodepayProperties(Map.of()), CLOCK);
        assertThrows(CommonException.class, () -> TenantUtil.runWithTenant("tenant", () -> missing.validate(context,
                validation(request(context, "123456789012345678", 1250, "Meal", until)))));
        verifyNoInteractions(client, queries);
    }

    @Test
    void wrongSubMerchantAndSuccessfulSubmitQueryTransactionMismatchAreRejected() {
        var context = context(true);
        var request = request(context, "123456789012345678", 1250, "Meal", OffsetDateTime.now(CLOCK).plusHours(1));
        var fields = response(context, "FAIL");
        fields.put("sub_mch_id", "1900000003");
        when(client.call(anyString(), any(), any(), anyMap())).thenReturn(new WechatV2VerifiedResponse(WechatV2Method.MICROPAY, fields, "a".repeat(64)));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.submit(context, request)));
        verifyNoInteractions(queries);
        fields = response(context, "SUCCESS");
        fields.put("out_trade_no", "original_01");
        fields.put("transaction_id", "4200000000001");
        fields.put("total_fee", "1250");
        fields.put("trade_type", "MICROPAY");
        when(client.call(anyString(), any(), any(), anyMap())).thenReturn(new WechatV2VerifiedResponse(WechatV2Method.MICROPAY, fields, "a".repeat(64)));
        when(queries.query(any(), any())).thenReturn(new PaymentObservation(request.payment().identity(), ChannelPaymentOutcome.Kind.ACCEPTED,
                request.payment().amount(), "another-transaction", null, PaymentObservation.Source.QUERY_RESPONSE, "query", "b".repeat(64)));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> adapter.submit(context, request)));
    }

    private ChannelExecutionContext context(boolean partner) {
        return new ChannelExecutionContext("tenant", new ChannelBindingRevision("binding", "merchant", Channel.WECHAT,
                partner ? AccessMode.SERVICE_PROVIDER : AccessMode.DIRECT, "a".repeat(64), "credential", "v1",
                new WechatAccountIdentity("wx-main", "1900000001", partner ? "wx-sub" : null, partner ? "1900000002" : null),
                Set.of(PaymentMethod.PAYMENT_CODE)));
    }

    private ChannelPaymentRequest request(ChannelExecutionContext context, String code, long amount, String subject, OffsetDateTime until) {
        var identity = new ChannelPaymentIdentity("payment", "attempt", Channel.WECHAT, context.binding().id(), context.binding().accountScopeHash(), "original_01");
        return new ChannelPaymentRequest(new PreparedChannelPayment(identity, Money.ofMinor(amount, "CNY"), PaymentMethod.PAYMENT_CODE, 1, "input"),
                subject, until, new ChannelPaymentCodeInput(code));
    }

    private ChannelPaymentValidation validation(ChannelPaymentRequest request) {
        return new ChannelPaymentValidation(request.payment().identity(), request.payment().amount(), request.payment().method(), request.subject(), request.payableUntil(), request.input());
    }

    private TreeMap<String, String> response(ChannelExecutionContext context, String result) {
        var account = (WechatAccountIdentity) context.binding().identity();
        var fields = new TreeMap<>(Map.of("return_code", "SUCCESS", "result_code", result, "appid", account.appId(), "mch_id", account.merchantId()));
        if (account.subMerchantId() != null) {
            fields.put("sub_mch_id", account.subMerchantId());
        }
        if (account.subAppId() != null) {
            fields.put("sub_appid", account.subAppId());
        }
        return fields;
    }
}
