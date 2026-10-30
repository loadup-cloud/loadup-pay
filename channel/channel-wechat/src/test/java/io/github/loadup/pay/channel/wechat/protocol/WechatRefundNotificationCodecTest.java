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
package io.github.loadup.pay.channel.wechat.protocol;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.kms.KmsTemplate;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import io.github.loadup.pay.channel.api.model.ChannelNotificationInput;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.channel.api.model.ChannelRefundNotificationHint;
import io.github.loadup.pay.channel.api.model.PreparedChannelRefund;
import io.github.loadup.pay.channel.api.model.VerifiedChannelRefundNotification;
import io.github.loadup.pay.channel.api.model.WechatAccountIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatRefundNotificationCodecTest {
    private static final String TENANT = "tenant";
    private static final String HASH = "a".repeat(64);
    private static final String API_V3_KEY = "0123456789abcdef0123456789abcdef";
    private static final String TIMESTAMP = Long.toString(Instant.parse("2026-10-24T04:00:00Z").getEpochSecond());
    private static final String NONCE = "signing-nonce-rf";
    private static final String RESOURCE_NONCE = "refundnonce12";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-24T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final String REFUND_JSON = "\"out_trade_no\":\"payment-request-18\",\"transaction_id\":\"wx-transaction-18\","
            + "\"out_refund_no\":\"refund-request-18\",\"refund_id\":\"wx-refund-18\","
            + "\"refund_status\":\"SUCCESS\",\"amount\":{\"total\":125000,\"refund\":35000},"
            + "\"success_time\":\"2026-10-24T12:00:00+08:00\"";
    private static final WechatAccountIdentity DIRECT = new WechatAccountIdentity("wx-app", "123456789", null, null);
    private static final WechatAccountIdentity PARTNER = new WechatAccountIdentity("wx-sp-app", "987654321", "wx-sub-app", "555555555");
    private static final ChannelBindingRevision DIRECT_BINDING = new ChannelBindingRevision("revision-1", "merchant-1",
            Channel.WECHAT, AccessMode.DIRECT, HASH, "credential-ref", "3", DIRECT, Set.of(PaymentMethod.PAYMENT_CODE));
    private static final ChannelBindingRevision PARTNER_BINDING = new ChannelBindingRevision("partner-revision", "merchant-2",
            Channel.WECHAT, AccessMode.SERVICE_PROVIDER, HASH, "credential-ref", "3", PARTNER, Set.of(PaymentMethod.PAYMENT_CODE));
    private static final ChannelExecutionContext DIRECT_CONTEXT = new ChannelExecutionContext(TENANT, DIRECT_BINDING);
    private static final ChannelExecutionContext PARTNER_CONTEXT = new ChannelExecutionContext(TENANT, PARTNER_BINDING);
    private static final Money ORIGINAL_AMOUNT = Money.ofMinor(125_000, "CNY");
    private static final Money REFUND_AMOUNT = Money.ofMinor(35_000, "CNY");
    private static final ChannelPaymentIdentity IDENTITY = new ChannelPaymentIdentity("payment-1", "attempt-1", Channel.WECHAT,
            "revision-1", HASH, "payment-request-18");
    private static final ChannelPaymentIdentity PARTNER_IDENTITY = new ChannelPaymentIdentity("payment-2", "attempt-2", Channel.WECHAT,
            "partner-revision", HASH, "payment-request-18");
    private static final PreparedChannelRefund REFUND = new PreparedChannelRefund(IDENTITY, "refund-1", "refund-request-18",
            REFUND_AMOUNT, ORIGINAL_AMOUNT, "wx-transaction-18");
    private static final PreparedChannelRefund PARTNER_REFUND = new PreparedChannelRefund(PARTNER_IDENTITY, "refund-2", "refund-request-18",
            REFUND_AMOUNT, ORIGINAL_AMOUNT, "wx-transaction-18");
    private KeyPair signingKeys;
    private WechatCredentialReader credentials;
    private WechatNotificationCodec codec;
    private AtomicReference<byte[]> lastApiKey;
    private List<byte[]> apiKeys;

    private static String refund(WechatAccountIdentity identity, String fields) {
        String account = identity.subMerchantId() == null ? "\"mchid\":\"" + identity.merchantId() + "\","
                : "\"sp_mchid\":\"" + identity.merchantId() + "\",\"sub_mchid\":\"" + identity.subMerchantId() + "\",";
        return "{" + account + fields + "}";
    }

    private static String replace(String json, String field, String value) {
        return json.replaceFirst("\\\"" + field + "\\\"\\s*:\\s*([^,}]+)", "\\\"" + field + "\\\":" + value);
    }

    private static Map<String, List<String>> withHeader(Map<String, List<String>> headers, String name, List<String> values) {
        var copy = new HashMap<String, List<String>>();
        headers.forEach((key, value) -> {
            if (!key.equalsIgnoreCase(name)) {
                copy.put(key, value);
            }
        });
        copy.put(name, values);
        return copy;
    }

    @BeforeEach
    void setUp() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        signingKeys = generator.generateKeyPair();
        credentials = mock(WechatCredentialReader.class);
        var signatures = new WechatProtocolCodec(JsonMapper.builder().build(), mock(KmsTemplate.class), CLOCK);
        codec = new WechatNotificationCodec(credentials, signatures, JsonMapper.builder().build());
        apiKeys = new ArrayList<>();
        lastApiKey = new AtomicReference<>();
        when(credentials.read(TENANT, DIRECT_BINDING)).thenReturn(credentials(DIRECT));
        when(credentials.read(TENANT, PARTNER_BINDING)).thenReturn(credentials(PARTNER));
        when(credentials.apiV3Key(eq(TENANT), any())).thenAnswer(invocation -> {
            byte[] key = API_V3_KEY.getBytes(StandardCharsets.US_ASCII);
            apiKeys.add(key);
            lastApiKey.set(key);
            return key;
        });
    }

    @Test
    void identifiesAndVerifiesRefundReturningThePreparedTarget() throws Exception {
        var body = notification(refund(DIRECT, REFUND_JSON), "REFUND.SUCCESS", "refund", "refund-notify-18");
        var input = signedInput(body);
        ChannelRefundNotificationHint hint = TenantUtil.callWithTenant(TENANT, () -> codec.identifyRefund(DIRECT_CONTEXT, input));
        VerifiedChannelRefundNotification verified = TenantUtil.callWithTenant(TENANT,
                () -> codec.verifyRefund(DIRECT_CONTEXT, REFUND, input));

        assertEquals("payment-request-18", hint.channelRequestNo());
        assertEquals("refund-request-18", hint.channelRefundNo());
        assertEquals(REFUND, verified.refund());
        assertEquals("refund-notify-18", verified.notificationId());
        assertEquals("wx-refund-18", verified.channelRefundTransactionNo());
        assertEquals("SUCCESS", verified.channelStatus());
        assertTrue(verified.sourceDigest().matches("[0-9a-f]{64}"));
        assertEveryKeyWasZeroed();
        verify(credentials, times(2)).apiV3Key(TENANT, DIRECT_BINDING);
    }

    @Test
    void verifiesServiceProviderMerchantFieldsWithoutApplicationIds() throws Exception {
        var body = notification(refund(PARTNER, REFUND_JSON), "REFUND.SUCCESS", "refund", "partner-refund");
        var verified = TenantUtil.callWithTenant(TENANT,
                () -> codec.verifyRefund(PARTNER_CONTEXT, PARTNER_REFUND, signedInput(body)));
        assertEquals(PARTNER_REFUND, verified.refund());
        assertEquals("SUCCESS", verified.channelStatus());
        assertKeyWasZeroed();
        verify(credentials).apiV3Key(TENANT, PARTNER_BINDING);
    }

    @Test
    void acceptsAllRefundEventStatusesAsAuthenticatedHintsAndMetadata() throws Exception {
        for (String status : List.of("SUCCESS", "CLOSED", "ABNORMAL")) {
            String rawData = replace(REFUND_JSON, "refund_status", "\"" + status + "\"");
            String data = status.equals("SUCCESS") ? rawData
                    : rawData.replace(",\"success_time\":\"2026-10-24T12:00:00+08:00\"", "");
            String event = "REFUND." + status;
            var verified = TenantUtil.callWithTenant(TENANT, () -> codec.verifyRefund(DIRECT_CONTEXT, REFUND,
                    signedInput(notification(refund(DIRECT, data), event, "refund", "notify-" + status))));
            assertEquals(REFUND, verified.refund());
            assertEquals(status, verified.channelStatus());
        }
        assertEveryKeyWasZeroed();
    }

    @Test
    void rejectsOriginalFieldAmountAndMerchantMismatches() throws Exception {
        assertRejected(signedInput(notification(refund(DIRECT, replace(REFUND_JSON, "out_trade_no", "\"wrong\"")),
                "REFUND.SUCCESS", "refund", "n1")));
        assertRejected(signedInput(notification(refund(DIRECT, replace(REFUND_JSON, "transaction_id", "\"wrong\"")),
                "REFUND.SUCCESS", "refund", "n2")));
        assertRejected(signedInput(notification(refund(DIRECT, replace(REFUND_JSON, "out_refund_no", "\"wrong\"")),
                "REFUND.SUCCESS", "refund", "n3")));
        assertRejected(signedInput(notification(refund(DIRECT, REFUND_JSON).replace("\"mchid\":\"123456789\"", "\"mchid\":\"other\""),
                "REFUND.SUCCESS", "refund", "n4")));
        assertRejected(signedInput(notification(refund(DIRECT, REFUND_JSON.replace("\"total\":125000", "\"total\":125001")),
                "REFUND.SUCCESS", "refund", "n5")));
        assertRejected(signedInput(notification(refund(DIRECT, REFUND_JSON.replace("\"refund\":35000", "\"refund\":35001")),
                "REFUND.SUCCESS", "refund", "n6")));
        assertEveryKeyWasZeroed();
    }

    @Test
    void rejectsEventMismatchWrongEventAndPaymentEvent() throws Exception {
        assertRejected(signedInput(notification(refund(DIRECT, REFUND_JSON), "REFUND.CLOSED", "refund", "status-mismatch")));
        assertRejected(signedInput(notification(refund(DIRECT, REFUND_JSON), "REFUND.PROCESSING", "refund", "unknown-event")));
        assertRejected(signedInput(notification(refund(DIRECT, REFUND_JSON), "TRANSACTION.SUCCESS", "refund", "payment-event")));
    }

    @Test
    void rejectsFractionalOrStringMinorUnitsAndClosedSuccessTime() throws Exception {
        assertRejected(signedInput(notification(refund(DIRECT, REFUND_JSON.replace("\"total\":125000", "\"total\":125000.5")),
                "REFUND.SUCCESS", "refund", "fraction")));
        assertRejected(signedInput(notification(refund(DIRECT, REFUND_JSON.replace("\"refund\":35000", "\"refund\":\"35000\"")),
                "REFUND.SUCCESS", "refund", "string-amount")));
        assertRejected(signedInput(notification(refund(DIRECT, replace(REFUND_JSON, "refund_status", "\"CLOSED\"")),
                "REFUND.CLOSED", "refund", "closed-time")));
        assertRejected(signedInput(notification(refund(DIRECT, "\"out_trade_no\":\"payment-request-18\",\"transaction_id\":\"wx-transaction-18\","
                + "\"out_refund_no\":\"refund-request-18\",\"refund_id\":\"wx-refund-18\",\"refund_status\":\"SUCCESS\","
                + "\"amount\":{\"total\":125000,\"refund\":35000,\"currency\":\"USD\"},"
                + "\"success_time\":\"2026-10-24T12:00:00+08:00\""), "REFUND.SUCCESS", "refund", "currency")));
        assertEveryKeyWasZeroed();
    }

    @Test
    void ignoresDiscountedPayerAmountsAndRejectsInvalidSignatureBeforeKeyRead() throws Exception {
        String data = REFUND_JSON.replace("\"total\":125000,\"refund\":35000", "\"total\":125000,\"payer_total\":1,\"refund\":35000,\"payer_refund\":2");
        var body = notification(refund(DIRECT, data), "REFUND.SUCCESS", "refund", "discounted");
        var goodHeaders = signedHeaders(body, false);
        var invalid = input(body, withHeader(goodHeaders, "Wechatpay-Signature", List.of("bad-signature")));
        assertRejected(invalid);
        verify(credentials, never()).apiV3Key(anyString(), any());
        var verified = TenantUtil.callWithTenant(TENANT, () -> codec.verifyRefund(DIRECT_CONTEXT, REFUND, signedInput(body)));
        assertEquals(REFUND, verified.refund());
        assertKeyWasZeroed();
    }

    @Test
    void rejectsStaleAndDuplicateAuthenticationHeaders() throws Exception {
        byte[] body = notification(refund(DIRECT, REFUND_JSON), "REFUND.SUCCESS", "refund", "header-check");
        var headers = signedHeaders(body, false);
        assertRejected(input(body, withHeader(headers, "Wechatpay-Timestamp", List.of("1"))));
        assertRejected(input(body, withHeader(headers, "Wechatpay-Nonce", List.of(NONCE, "duplicate"))));
        assertRejected(input(body, withHeader(headers, "Wechatpay-Signature", List.of("one", "two"))));
        verify(credentials, never()).apiV3Key(anyString(), any());
    }

    private void assertRejected(ChannelNotificationInput input) {
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant(TENANT,
                () -> codec.verifyRefund(DIRECT_CONTEXT, REFUND, input)));
    }

    private void assertEveryKeyWasZeroed() {
        assertFalse(apiKeys.isEmpty());
        for (byte[] key : apiKeys) {
            assertArrayEquals(new byte[key.length], key);
        }
    }

    private void assertKeyWasZeroed() {
        byte[] key = lastApiKey.get();
        assertNotNull(key);
        assertArrayEquals(new byte[key.length], key);
    }

    private ChannelNotificationInput signedInput(byte[] body) throws Exception {
        return input(body, signedHeaders(body, false));
    }

    private Map<String, List<String>> signedHeaders(byte[] body, boolean mutateSignature) throws Exception {
        var signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(signingKeys.getPrivate());
        signer.update((TIMESTAMP + "\n" + NONCE + "\n").getBytes(StandardCharsets.UTF_8));
        signer.update(body);
        signer.update((byte) '\n');
        byte[] signature = signer.sign();
        if (mutateSignature) {
            signature[0] ^= 1;
        }
        return Map.of("Wechatpay-Serial", List.of("PUB_KEY_ID_test"), "Wechatpay-Timestamp", List.of(TIMESTAMP),
                "Wechatpay-Nonce", List.of(NONCE), "Wechatpay-Signature", List.of(Base64.getEncoder().encodeToString(signature)));
    }

    private ChannelNotificationInput input(byte[] body, Map<String, List<String>> headers) {
        return new ChannelNotificationInput("application/json; charset=UTF-8", headers, body);
    }

    private byte[] notification(String data, String event, String originalType, String id) throws Exception {
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(API_V3_KEY.getBytes(StandardCharsets.US_ASCII), "AES"),
                new GCMParameterSpec(128, RESOURCE_NONCE.getBytes(StandardCharsets.UTF_8)));
        byte[] ciphertext = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        String resource = "{\"algorithm\":\"AEAD_AES_256_GCM\",\"original_type\":\"" + originalType
                + "\",\"nonce\":\"" + RESOURCE_NONCE + "\",\"ciphertext\":\""
                + Base64.getEncoder().encodeToString(ciphertext) + "\"}";
        return ("{\"id\":\"" + id + "\",\"create_time\":\"2026-10-24T12:00:00+08:00\",\"event_type\":\""
                + event + "\",\"resource_type\":\"encrypt-resource\",\"resource\":" + resource + "}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private WechatCredentials credentials(WechatAccountIdentity identity) {
        return new WechatCredentials(TENANT, identity.appId(), identity.merchantId(), "1234ABCD", null,
                "PUB_KEY_ID_test", signingKeys.getPublic());
    }
}
