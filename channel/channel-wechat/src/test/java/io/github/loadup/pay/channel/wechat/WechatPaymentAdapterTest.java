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
import io.github.loadup.commons.payment.*;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.model.*;
import io.github.loadup.pay.channel.wechat.config.WechatNotificationProperties;
import io.github.loadup.pay.channel.wechat.protocol.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WechatPaymentAdapterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-24T03:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final Money AMOUNT = Money.ofMinor(1250, "CNY");
    private final WechatProtocolClient client = mock(WechatProtocolClient.class);
    private final WechatPaymentAdapter adapter = new WechatPaymentAdapter(client, JsonMapper.builder().build(), CLOCK);
    private final ChannelPaymentIdentity identity = new ChannelPaymentIdentity("payment", "attempt", Channel.WECHAT, "binding", "a".repeat(64), "original_01");
    private final ChannelPaymentTarget target = new ChannelPaymentTarget(identity, AMOUNT);
    private final ChannelExecutionContext context = new ChannelExecutionContext("tenant", new ChannelBindingRevision("binding", "merchant",
            Channel.WECHAT, AccessMode.SERVICE_PROVIDER, "a".repeat(64), "credential", "v1",
            new WechatAccountIdentity("wx-provider", "1900000001", "wx-sub-app", "1900000002"), Set.of(PaymentMethod.JSAPI)));

    @Test
    void successAndRefundPreserveGrossAmountAndOriginalSuccessTime() {
        for (String state : List.of("SUCCESS", "REFUND")) {
            when(client.call(anyString(), any(), eq(WechatMethod.PARTNER_QUERY), anyMap(), anyMap(), isNull()))
                    .thenReturn(query(200, body(state)));
            TenantUtil.runWithTenant("tenant", () -> {
                var result = adapter.query(context, target);
                assertEquals(ChannelPaymentOutcome.Kind.SUCCEEDED, result.kind()); assertEquals(AMOUNT, result.amount());
                assertEquals("wx-transaction", result.channelTransactionNo());
                assertEquals(LocalDateTime.of(2026, 10, 24, 11, 0), result.succeededAt());
            });
        }
        verify(client, times(2)).call(eq("tenant"), eq(context.binding()), eq(WechatMethod.PARTNER_QUERY),
                eq(Map.of("out_trade_no", "original_01")), eq(Map.of("sp_mchid", List.of("1900000001"), "sub_mchid", List.of("1900000002"))), isNull());
    }

    @Test
    void rejectsAccountOrderOrGrossAmountMismatchAndFloatingPointTotals() {
        var original = body("SUCCESS");
        for (String changed : List.of(original.replace("1900000002", "1900000003"), original.replace("wx-sub-app", "wrong-app"),
                original.replace("original_01", "wrong-order"), original.replace("1250", "1000"), original.replace("1250", "1250.0"))) {
            when(client.call(anyString(), any(), eq(WechatMethod.PARTNER_QUERY), anyMap(), anyMap(), isNull()))
                    .thenReturn(query(200, changed));
            TenantUtil.runWithTenant("tenant", () -> assertThrows(CommonException.class, () -> adapter.query(context, target)));
        }
    }

    @Test
    void closedAndNotFoundRemainUnknownAndRecoveryOnlyQueriesOriginalOrder() {
        when(client.call(anyString(), any(), eq(WechatMethod.PARTNER_QUERY), anyMap(), anyMap(), isNull()))
                .thenReturn(query(200, body("CLOSED")), query(404, "{\"code\":\"ORDER_NOT_EXIST\"}"));
        TenantUtil.runWithTenant("tenant", () -> {
            assertEquals(ChannelPaymentOutcome.Kind.UNKNOWN, adapter.query(context, target).kind());
            var result = adapter.recover(context, new ChannelPaymentRecovery(identity, AMOUNT, PaymentMethod.JSAPI, 1,
                    OffsetDateTime.now(CLOCK).plusHours(1)));
            assertEquals(ChannelPaymentOutcome.Kind.UNKNOWN, result.observation().kind()); assertNull(result.action());
        });
        verify(client, times(2)).call(anyString(), any(), eq(WechatMethod.PARTNER_QUERY), anyMap(), anyMap(), isNull());
        verifyNoMoreInteractions(client);
    }

    @Test
    void onlySignedCloseAcknowledgementAfterOriginalPendingQueryProvesNoCollection() {
        when(client.call(anyString(), any(), eq(WechatMethod.PARTNER_QUERY), anyMap(), anyMap(), isNull())).thenReturn(query(200, body("NOTPAY")));
        when(client.call(anyString(), any(), eq(WechatMethod.PARTNER_CLOSE), anyMap(), anyMap(), anyString()))
                .thenReturn(new WechatVerifiedResponse(WechatMethod.PARTNER_CLOSE, 204, "", "b".repeat(64)));
        TenantUtil.runWithTenant("tenant", () -> {
            var result = adapter.close(context, target);
            assertEquals(ChannelPaymentOutcome.Kind.NO_COLLECTION, result.kind());
            assertEquals(PaymentObservation.Source.CLOSE_RESPONSE, result.source());
            assertNotEquals("b".repeat(64), result.sourceDigest());
        });
        var sent = ArgumentCaptor.forClass(String.class);
        verify(client).call(eq("tenant"), eq(context.binding()), eq(WechatMethod.PARTNER_CLOSE),
                eq(Map.of("out_trade_no", "original_01")), eq(Map.of()), sent.capture());
        assertEquals(Map.of("sp_mchid", "1900000001", "sub_mchid", "1900000002"), JsonMapper.builder().build().readValue(sent.getValue(), Map.class));
    }

    @Test
    void micropayOrMissingProductNeverUsesTheUnpaidCloseOrInventsAnUncollectedResult() {
        for (String changed : List.of(body("USERPAYING").replace("JSAPI", "MICROPAY"),
                body("NOTPAY").replace("\"trade_type\":\"JSAPI\",", ""))) {
            when(client.call(anyString(), any(), eq(WechatMethod.PARTNER_QUERY), anyMap(), anyMap(), isNull()))
                    .thenReturn(query(200, changed));
            TenantUtil.runWithTenant("tenant", () -> {
                var result = adapter.close(context, target);
                assertEquals(ChannelPaymentOutcome.Kind.ACCEPTED, result.kind());
                assertEquals(PaymentObservation.Source.QUERY_RESPONSE, result.source());
            });
        }
        verify(client, never()).call(anyString(), any(), eq(WechatMethod.PARTNER_CLOSE), anyMap(), anyMap(), anyString());
    }

    @Test
    void closeRaceRequeriesInsteadOfTreatingErrorAsAnUnpaidProof() {
        when(client.call(anyString(), any(), eq(WechatMethod.PARTNER_QUERY), anyMap(), anyMap(), isNull()))
                .thenReturn(query(200, body("USERPAYING")), query(200, body("SUCCESS")));
        when(client.call(anyString(), any(), eq(WechatMethod.PARTNER_CLOSE), anyMap(), anyMap(), anyString()))
                .thenReturn(new WechatVerifiedResponse(WechatMethod.PARTNER_CLOSE, 400, "{\"code\":\"ORDERPAID\"}", "b".repeat(64)));
        TenantUtil.runWithTenant("tenant", () -> assertEquals(ChannelPaymentOutcome.Kind.SUCCEEDED, adapter.close(context, target).kind()));
        verify(client, times(2)).call(anyString(), any(), eq(WechatMethod.PARTNER_QUERY), anyMap(), anyMap(), isNull());
    }
    private String body(String state) {
        return "{\"sp_appid\":\"wx-provider\",\"sp_mchid\":\"1900000001\",\"sub_appid\":\"wx-sub-app\",\"sub_mchid\":\"1900000002\","
                + "\"trade_type\":\"JSAPI\",\"out_trade_no\":\"original_01\",\"trade_state\":\"" + state + "\",\"transaction_id\":\"wx-transaction\","
                + "\"success_time\":\"2026-10-24T03:00:00Z\",\"amount\":{\"total\":1250,\"payer_total\":1000,\"currency\":\"CNY\"}}";
    }
    private WechatVerifiedResponse query(int status, String body) {
        return new WechatVerifiedResponse(WechatMethod.PARTNER_QUERY, status, body, DigestUtils.sha256(body));
    }
}
