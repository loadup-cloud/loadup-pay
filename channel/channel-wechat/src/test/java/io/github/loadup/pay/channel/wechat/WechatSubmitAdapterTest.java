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
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import io.github.loadup.pay.channel.api.model.ChannelH5Input;
import io.github.loadup.pay.channel.api.model.ChannelPayerIdentityInput;
import io.github.loadup.pay.channel.api.model.ChannelPaymentCodeInput;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.channel.api.model.ChannelPaymentInput;
import io.github.loadup.pay.channel.api.model.ChannelPaymentOutcome;
import io.github.loadup.pay.channel.api.model.ChannelPaymentRequest;
import io.github.loadup.pay.channel.api.model.ChannelPaymentValidation;
import io.github.loadup.pay.channel.api.model.H5SceneType;
import io.github.loadup.pay.channel.api.model.NoChannelInput;
import io.github.loadup.pay.channel.api.model.PayerIdentityType;
import io.github.loadup.pay.channel.api.model.PreparedChannelPayment;
import io.github.loadup.pay.channel.api.model.WechatAccountIdentity;
import io.github.loadup.pay.channel.api.model.WechatH5ChannelAction;
import io.github.loadup.pay.channel.api.model.WechatJsapiChannelAction;
import io.github.loadup.pay.channel.wechat.config.WechatNotificationProperties;
import io.github.loadup.pay.channel.wechat.protocol.WechatJsapiSigner;
import io.github.loadup.pay.channel.wechat.protocol.WechatMethod;
import io.github.loadup.pay.channel.wechat.protocol.WechatProtocolClient;
import io.github.loadup.pay.channel.wechat.protocol.WechatVerifiedResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
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

class WechatSubmitAdapterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-24T03:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final OffsetDateTime UNTIL = OffsetDateTime.now(CLOCK).plusHours(3);
    private static final Money AMOUNT = Money.ofMinor(1250, "CNY");
    private final JsonMapper json = JsonMapper.builder().build();
    private final WechatProtocolClient client = mock(WechatProtocolClient.class);
    private final WechatJsapiSigner signer = mock(WechatJsapiSigner.class);
    private final WechatNotificationProperties notifications = new WechatNotificationProperties(Map.of("tenant",
            Map.of("binding", URI.create("https://payments.example/wechat/notify"))), Map.of());
    private final WechatSubmitAdapter adapter = new WechatSubmitAdapter(client, signer, notifications, json, CLOCK);

    @Test
    void directPrepayFreezesOriginalGrossAmountAndProducesOnlyAnAcceptedSignedAction() {
        var context = context(new WechatAccountIdentity("wx-app", "1900000001", null, null));
        var request = request("wx-app", UNTIL);
        var expiry = OffsetDateTime.now(CLOCK).plusHours(2);
        var expected = action("wx-app", expiry);
        when(client.call(eq("tenant"), eq(context.binding()), eq(WechatMethod.DIRECT_JSAPI), anyMap(), anyMap(), anyString()))
                .thenReturn(response(WechatMethod.DIRECT_JSAPI, 200, "{\"prepay_id\":\"synthetic\"}"));
        when(signer.sign(context, identity(), "synthetic", expiry)).thenReturn(expected);
        TenantUtil.runWithTenant("tenant", () -> {
            var result = adapter.submit(context, request);
            assertEquals(ChannelPaymentOutcome.Kind.ACCEPTED, result.observation().kind());
            assertNull(result.observation().channelTransactionNo());
            assertEquals(expected, result.action());
        });
        var body = ArgumentCaptor.forClass(String.class);
        verify(client).call(eq("tenant"), eq(context.binding()), eq(WechatMethod.DIRECT_JSAPI), eq(Map.of()), eq(Map.of()), body.capture());
        var data = json.readValue(body.getValue(), Map.class);
        assertEquals("wx-app", data.get("appid"));
        assertEquals("1900000001", data.get("mchid"));
        assertEquals("original_01", data.get("out_trade_no"));
        assertEquals(Map.of("openid", "openid-original"), data.get("payer"));
        assertEquals(1250, ((Map<?, ?>) data.get("amount")).get("total"));
        assertEquals("CNY", ((Map<?, ?>) data.get("amount")).get("currency"));
        assertEquals(UNTIL.toInstant(), OffsetDateTime.parse((String) data.get("time_expire")).toInstant());
        assertEquals("https://payments.example/wechat/notify", data.get("notify_url"));
        assertFalse(data.containsKey("sp_mchid"));
    }

    @Test
    void partnerUsesFrozenSubMerchantAndMatchingSpOrSubOpenIdWithOriginalDeadline() {
        for (String subAppId : new String[]{null, "wx-sub-app"}) {
            clearInvocations(client, signer);
            var context = context(new WechatAccountIdentity("wx-provider", "1900000001", subAppId, "1900000002"));
            String appId = subAppId == null ? "wx-provider" : subAppId;
            var until = OffsetDateTime.now(CLOCK).plusMinutes(30);
            var expected = action(appId, until);
            when(client.call(eq("tenant"), eq(context.binding()), eq(WechatMethod.PARTNER_JSAPI), anyMap(), anyMap(), anyString()))
                    .thenReturn(response(WechatMethod.PARTNER_JSAPI, 200, "{\"prepay_id\":\"synthetic\"}"));
            when(signer.sign(context, identity(), "synthetic", until)).thenReturn(expected);
            TenantUtil.runWithTenant("tenant", () -> assertEquals(expected, adapter.submit(context, request(appId, until)).action()));
            var body = ArgumentCaptor.forClass(String.class);
            verify(client).call(eq("tenant"), eq(context.binding()), eq(WechatMethod.PARTNER_JSAPI), anyMap(), anyMap(), body.capture());
            var data = json.readValue(body.getValue(), Map.class);
            assertEquals("wx-provider", data.get("sp_appid"));
            assertEquals("1900000001", data.get("sp_mchid"));
            assertEquals("1900000002", data.get("sub_mchid"));
            assertEquals(subAppId, data.get("sub_appid"));
            assertEquals(Map.of(subAppId == null ? "sp_openid" : "sub_openid", "openid-original"), data.get("payer"));
            assertFalse(data.containsKey("mchid"));
        }
    }

    @Test
    void directAndPartnerH5UseSceneFieldsAndReturnUntouchedAcceptedUrl() {
        var providerUrl = "https://wx.tenpay.com/cgi-bin/mmpayweb-bin/checkmweb?prepay_id=synthetic&package=abc%2Fdef";
        for (var account : List.of(
                new WechatAccountIdentity("wx-app", "1900000001", null, null),
                new WechatAccountIdentity("wx-provider", "1900000001", null, "1900000002"))) {
            for (var sceneType : H5SceneType.values()) {
                clearInvocations(client, signer);
                var context = context(account);
                var operation = account.subMerchantId() == null ? WechatMethod.DIRECT_H5 : WechatMethod.PARTNER_H5;
                when(client.call(eq("tenant"), eq(context.binding()), eq(operation), anyMap(), anyMap(), anyString()))
                        .thenReturn(response(operation, 200, "{\"h5_url\":\"" + providerUrl + "\"}"));

                var result = TenantUtil.callWithTenant("tenant", () -> adapter.submit(context,
                        h5Request(UNTIL, new ChannelH5Input("8.8.8.8", sceneType))));

                assertEquals(ChannelPaymentOutcome.Kind.ACCEPTED, result.observation().kind());
                assertInstanceOf(WechatH5ChannelAction.class, result.action());
                var action = (WechatH5ChannelAction) result.action();
                assertEquals(providerUrl, action.redirectUrl());
                assertEquals(OffsetDateTime.now(CLOCK).plusMinutes(5), action.expiresAt());

                var body = ArgumentCaptor.forClass(String.class);
                verify(client).call(eq("tenant"), eq(context.binding()), eq(operation), eq(Map.of()), eq(Map.of()), body.capture());
                var data = json.readValue(body.getValue(), Map.class);
                var expectedType = switch (sceneType) {
                    case WAP -> "Wap";
                    case IOS -> "iOS";
                    case ANDROID -> "Android";
                };
                assertEquals(Map.of("payer_client_ip", "8.8.8.8", "h5_info", Map.of("type", expectedType)), data.get("scene_info"));
                assertFalse(data.containsKey("payer"));
                assertFalse(data.containsKey("openid"));
                if (account.subMerchantId() == null) {
                    assertEquals("wx-app", data.get("appid"));
                    assertEquals("1900000001", data.get("mchid"));
                    assertFalse(data.containsKey("sub_mchid"));
                } else {
                    assertEquals("wx-provider", data.get("sp_appid"));
                    assertEquals("1900000001", data.get("sp_mchid"));
                    assertEquals("1900000002", data.get("sub_mchid"));
                }
                verifyNoInteractions(signer);
            }
        }
    }

    @Test
    void h5ExpiryUsesEarlierDeadlineOrFiveMinutesFromCallStartAndNeverExtendsForLatency() {
        var start = Instant.parse("2026-10-24T03:00:00Z");
        var clock = new AdvancingClock(start, ZoneId.of("Asia/Shanghai"));
        var adapterWithClock = new WechatSubmitAdapter(client, signer, notifications, json, clock);
        var context = context(new WechatAccountIdentity("wx-app", "1900000001", null, null));
        var shortDeadline = OffsetDateTime.ofInstant(start, clock.getZone()).plusMinutes(3).plusNanos(900_000_000);
        var providerUrl = "https://wx.tenpay.com/cgi-bin/mmpayweb-bin/checkmweb?prepay_id=synthetic";
        when(client.call(anyString(), any(), eq(WechatMethod.DIRECT_H5), anyMap(), anyMap(), anyString()))
                .thenAnswer(invocation -> {
                    clock.advance(Duration.ofMinutes(2));
                    return response(WechatMethod.DIRECT_H5, 200, "{\"h5_url\":\"" + providerUrl + "\"}");
                });

        var result = TenantUtil.callWithTenant("tenant", () -> adapterWithClock.submit(context,
                h5Request(shortDeadline, new ChannelH5Input("8.8.8.8", H5SceneType.WAP))));

        assertEquals(OffsetDateTime.ofInstant(start, clock.getZone()).plusMinutes(3),
                ((WechatH5ChannelAction) result.action()).expiresAt());
        clearInvocations(client, signer);

        var longDeadline = OffsetDateTime.ofInstant(clock.instant(), clock.getZone()).plusHours(1);
        when(client.call(anyString(), any(), eq(WechatMethod.DIRECT_H5), anyMap(), anyMap(), anyString()))
                .thenAnswer(invocation -> {
                    clock.advance(Duration.ofMinutes(2));
                    return response(WechatMethod.DIRECT_H5, 200, "{\"h5_url\":\"" + providerUrl + "\"}");
                });
        var secondStart = clock.instant();
        var secondResult = TenantUtil.callWithTenant("tenant", () -> adapterWithClock.submit(context,
                h5Request(longDeadline, new ChannelH5Input("8.8.8.8", H5SceneType.WAP))));
        assertEquals(OffsetDateTime.ofInstant(secondStart, clock.getZone()).plusMinutes(5),
                ((WechatH5ChannelAction) secondResult.action()).expiresAt());
        verifyNoInteractions(signer);
    }

    @Test
    void expiredH5ActionIsOmittedAndInvalidProviderUrlIsRejected() {
        var start = Instant.parse("2026-10-24T03:00:00Z");
        var clock = new AdvancingClock(start, ZoneId.of("Asia/Shanghai"));
        var adapterWithClock = new WechatSubmitAdapter(client, signer, notifications, json, clock);
        var context = context(new WechatAccountIdentity("wx-app", "1900000001", null, null));
        var deadline = OffsetDateTime.ofInstant(start, clock.getZone()).plusHours(1);
        when(client.call(anyString(), any(), eq(WechatMethod.DIRECT_H5), anyMap(), anyMap(), anyString()))
                .thenAnswer(invocation -> {
                    clock.advance(Duration.ofMinutes(6));
                    return response(WechatMethod.DIRECT_H5, 200,
                            "{\"h5_url\":\"https://wx.tenpay.com/cgi-bin/mmpayweb-bin/checkmweb?prepay_id=synthetic\"}");
                });
        var result = TenantUtil.callWithTenant("tenant", () -> adapterWithClock.submit(context,
                h5Request(deadline, new ChannelH5Input("8.8.8.8", H5SceneType.WAP))));
        assertEquals(ChannelPaymentOutcome.Kind.ACCEPTED, result.observation().kind());
        assertNull(result.action());

        clearInvocations(client, signer);
        when(client.call(anyString(), any(), eq(WechatMethod.DIRECT_H5), anyMap(), anyMap(), anyString()))
                .thenReturn(response(WechatMethod.DIRECT_H5, 200,
                        "{\"h5_url\":\"https://evil.example/cgi-bin/mmpayweb-bin/checkmweb?prepay_id=synthetic\"}"));
        assertThrows(CommonException.class, () -> TenantUtil.runWithTenant("tenant", () -> adapter.submit(context,
                h5Request(UNTIL, new ChannelH5Input("8.8.8.8", H5SceneType.WAP)))));
        verifyNoInteractions(signer);
    }

    @Test
    void h5MissingInputCapabilityAndSignedErrorFailSafelyBeforeActionCreation() {
        var account = new WechatAccountIdentity("wx-app", "1900000001", null, null);
        var context = context(account);
        var noH5Context = context(account, Set.of(PaymentMethod.JSAPI));
        TenantUtil.runWithTenant("tenant", () -> {
            assertThrows(CommonException.class, () -> adapter.submit(noH5Context,
                    h5Request(UNTIL, new ChannelH5Input("8.8.8.8", H5SceneType.WAP))));
            assertThrows(CommonException.class, () -> adapter.submit(context, h5Request(UNTIL, new NoChannelInput())));
        });
        verifyNoInteractions(client, signer);

        when(client.call(anyString(), any(), eq(WechatMethod.DIRECT_H5), anyMap(), anyMap(), anyString()))
                .thenReturn(response(WechatMethod.DIRECT_H5, 400, "{\"code\":\"OUT_TRADE_NO_USED\"}"));
        var result = TenantUtil.callWithTenant("tenant", () -> adapter.submit(context,
                h5Request(UNTIL, new ChannelH5Input("8.8.8.8", H5SceneType.WAP))));
        assertEquals(ChannelPaymentOutcome.Kind.UNKNOWN, result.observation().kind());
        assertNull(result.action());
        verifyNoInteractions(signer);
    }

    @Test
    void rejectsBadIdentityScopeUnsupportedMethodsAndMissingNotifyUrlWithoutExternalCalls() {
        var context = context(new WechatAccountIdentity("wx-app", "1900000001", null, null));
        TenantUtil.runWithTenant("tenant", () -> {
            assertThrows(CommonException.class, () -> adapter.validate(context, validation(PaymentMethod.JSAPI,
                    new ChannelPayerIdentityInput(Channel.WECHAT, PayerIdentityType.OPEN_ID, "openid", "wrong-app"))));
            assertThrows(CommonException.class, () -> adapter.validate(context, validation(PaymentMethod.JSAPI,
                    new ChannelPayerIdentityInput(Channel.WECHAT, PayerIdentityType.USER_ID, "userid", "wx-app"))));
            assertThrows(CommonException.class, () -> adapter.validate(context, validation(PaymentMethod.H5, new NoChannelInput())));
            assertThrows(CommonException.class, () -> adapter.validate(context, validation(PaymentMethod.PAYMENT_CODE, new ChannelPaymentCodeInput("synthetic-code"))));
            var missingUrl = new WechatSubmitAdapter(client, signer, new WechatNotificationProperties(Map.of(), Map.of()), json, CLOCK);
            assertThrows(CommonException.class, () -> missingUrl.validate(context, validation(PaymentMethod.JSAPI,
                    new ChannelPayerIdentityInput(Channel.WECHAT, PayerIdentityType.OPEN_ID, "openid", "wx-app"))));
        });
        verifyNoInteractions(client, signer);
    }

    @Test
    void signedErrorIsUnknownAndCannotProduceAnInvocationAction() {
        var context = context(new WechatAccountIdentity("wx-app", "1900000001", null, null));
        when(client.call(anyString(), any(), eq(WechatMethod.DIRECT_JSAPI), anyMap(), anyMap(), anyString()))
                .thenReturn(response(WechatMethod.DIRECT_JSAPI, 400, "{\"code\":\"OUT_TRADE_NO_USED\"}"));
        TenantUtil.runWithTenant("tenant", () -> {
            var result = adapter.submit(context, request("wx-app", UNTIL));
            assertEquals(ChannelPaymentOutcome.Kind.UNKNOWN, result.observation().kind());
            assertNull(result.action());
        });
        verifyNoInteractions(signer);
    }

    private ChannelExecutionContext context(WechatAccountIdentity account) {
        return context(account, Set.of(PaymentMethod.JSAPI, PaymentMethod.H5));
    }

    private ChannelExecutionContext context(WechatAccountIdentity account, Set<PaymentMethod> capabilities) {
        return new ChannelExecutionContext("tenant", new ChannelBindingRevision("binding", "merchant", Channel.WECHAT,
                account.accessMode(), "a".repeat(64), "credential", "v1", account, capabilities));
    }

    private ChannelPaymentIdentity identity() {
        return new ChannelPaymentIdentity("payment", "attempt", Channel.WECHAT, "binding", "a".repeat(64), "original_01");
    }

    private ChannelPaymentRequest request(String appId, OffsetDateTime until) {
        return new ChannelPaymentRequest(new PreparedChannelPayment(identity(), AMOUNT, PaymentMethod.JSAPI, 1, "prepared-input"),
                "original subject", until, new ChannelPayerIdentityInput(Channel.WECHAT, PayerIdentityType.OPEN_ID, "openid-original", appId));
    }

    private ChannelPaymentRequest h5Request(OffsetDateTime until, ChannelPaymentInput input) {
        return new ChannelPaymentRequest(new PreparedChannelPayment(identity(), AMOUNT, PaymentMethod.H5, 1, "prepared-input"),
                "original subject", until, input);
    }

    private ChannelPaymentValidation validation(PaymentMethod method, ChannelPaymentInput input) {
        return new ChannelPaymentValidation(identity(), AMOUNT, method, "subject", UNTIL, input);
    }

    private WechatJsapiChannelAction action(String appId, OffsetDateTime expiry) {
        return new WechatJsapiChannelAction(appId, Long.toString(CLOCK.instant().getEpochSecond()), "nonce", "prepay_id=synthetic",
                "RSA", Base64.getEncoder().encodeToString(new byte[256]), expiry);
    }

    private WechatVerifiedResponse response(WechatMethod method, int status, String body) {
        return new WechatVerifiedResponse(method, status, body, DigestUtils.sha256(body));
    }

    private static final class AdvancingClock extends Clock {
        private final ZoneId zone;
        private Instant instant;

        private AdvancingClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new AdvancingClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
