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
import io.github.loadup.pay.channel.api.model.ChannelNotificationHint;
import io.github.loadup.pay.channel.api.model.ChannelNotificationInput;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.channel.api.model.ChannelPaymentTarget;
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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatNotificationCodecTest {
    private static final String TENANT = "tenant";
    private static final String HASH = "a".repeat(64);
    private static final String ORDER_NO = "payment-request-18";
    private static final String API_V3_KEY = "0123456789abcdef0123456789abcdef";
    private static final String TIMESTAMP = Long.toString(Instant.parse("2026-10-24T04:00:00Z").getEpochSecond());
    private static final String NONCE = "signing-nonce-18";
    private static final String RESOURCE_NONCE = "nonce12345678";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-24T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final WechatAccountIdentity DIRECT = new WechatAccountIdentity("wx-app", "123456789", null, null);
    private static final WechatAccountIdentity PARTNER = new WechatAccountIdentity("wx-sp-app", "987654321", "wx-sub-app", "555555555");
    private static final ChannelBindingRevision DIRECT_BINDING = new ChannelBindingRevision("revision-1", "merchant-1",
            Channel.WECHAT, AccessMode.DIRECT, HASH, "credential-ref", "3", DIRECT, Set.of(PaymentMethod.PAYMENT_CODE));
    private static final ChannelBindingRevision PARTNER_BINDING = new ChannelBindingRevision("partner-revision", "merchant-2",
            Channel.WECHAT, AccessMode.SERVICE_PROVIDER, HASH, "credential-ref", "3", PARTNER, Set.of(PaymentMethod.PAYMENT_CODE));
    private static final ChannelExecutionContext DIRECT_CONTEXT = new ChannelExecutionContext(TENANT, DIRECT_BINDING);
    private static final ChannelExecutionContext PARTNER_CONTEXT = new ChannelExecutionContext(TENANT, PARTNER_BINDING);
    private static final Money AMOUNT = Money.ofMinor(125_000, "CNY");
    private static final ChannelPaymentIdentity IDENTITY = new ChannelPaymentIdentity("payment-1", "attempt-1", Channel.WECHAT,
            "revision-1", HASH, ORDER_NO);
    private static final ChannelPaymentTarget TARGET = new ChannelPaymentTarget(IDENTITY, AMOUNT);
    private KeyPair signingKeys;
    private WechatCredentialReader credentials;
    private WechatNotificationCodec codec;
    private AtomicReference<byte[]> lastApiKey;

    private static String payment(WechatAccountIdentity identity) {
        var account = identity.subMerchantId() == null
                ? "\"appid\":\"" + identity.appId() + "\",\"mchid\":\"" + identity.merchantId() + "\""
                : "\"sp_appid\":\"" + identity.appId() + "\",\"sp_mchid\":\"" + identity.merchantId()
                + "\",\"sub_mchid\":\"" + identity.subMerchantId() + "\",\"sub_appid\":\"" + identity.subAppId() + "\"";
        return "{" + account + ",\"trade_state\":\"SUCCESS\",\"transaction_id\":\"wx-transaction-18\","
                + "\"out_trade_no\":\"" + ORDER_NO + "\",\"amount\":{\"total\":125000,\"currency\":\"CNY\"},"
                + "\"success_time\":\"2026-10-24T12:00:00+08:00\"}";
    }

    private static String with(String json, String field, Object value) {
        return json.replaceFirst("\\\"" + field + "\\\"\\s*:\\s*([^,}]+)", "\\\"" + field + "\\\":" + value);
    }

    private static String with(String json, String field, Map<String, Object> value) {
        return json.replaceFirst("\\\"" + field + "\\\"\\s*:\\s*\\{[^}]*}", "\\\"" + field + "\\\":" +
                "{\\\"total\\\":" + value.get("total") + ",\\\"currency\\\":\\\"" + value.get("currency") + "\\\"}");
    }

    private static byte[] withByteChanged(byte[] original) {
        byte[] changed = original.clone();
        changed[changed.length - 2] ^= 1;
        return changed;
    }

    private static byte[] corruptTag(byte[] original) {
        String value = new String(original, StandardCharsets.UTF_8);
        int start = value.indexOf("\"ciphertext\":\"") + "\"ciphertext\":\"".length();
        int end = value.indexOf('"', start);
        byte[] decoded = Base64.getDecoder().decode(value.substring(start, end));
        decoded[decoded.length - 1] ^= 1;
        return (value.substring(0, start) + Base64.getEncoder().encodeToString(decoded) + value.substring(end))
                .getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, List<String>> withHeader(Map<String, List<String>> headers, String name, List<String> values) {
        var copy = new java.util.HashMap<String, List<String>>();
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
        var kms = mock(KmsTemplate.class);
        var signatures = new WechatProtocolCodec(JsonMapper.builder().build(), kms, CLOCK);
        codec = new WechatNotificationCodec(credentials, signatures, JsonMapper.builder().build());
        lastApiKey = new AtomicReference<>();
        when(credentials.read(TENANT, DIRECT_BINDING)).thenReturn(credentials(DIRECT));
        when(credentials.read(TENANT, PARTNER_BINDING)).thenReturn(credentials(PARTNER));
        when(credentials.apiV3Key(eq(TENANT), any())).thenAnswer(invocation -> {
            byte[] copy = API_V3_KEY.getBytes(StandardCharsets.US_ASCII);
            lastApiKey.set(copy);
            return copy;
        });
    }

    @Test
    void identifiesAndVerifiesDirectPaymentWithAssociatedData() throws Exception {
        var body = notification(payment(DIRECT), "TRANSACTION.SUCCESS", "transaction", "wx-notify-18", "trade-table", false);
        var input = signedInput(body);

        ChannelNotificationHint hint = TenantUtil.callWithTenant(TENANT, () -> codec.identify(DIRECT_CONTEXT, input));
        var verified = TenantUtil.callWithTenant(TENANT, () -> codec.verify(DIRECT_CONTEXT, TARGET, input));

        assertEquals(ORDER_NO, hint.channelRequestNo());
        assertEquals(IDENTITY, verified.identity());
        assertEquals(AMOUNT, verified.amount());
        assertEquals("wx-notify-18", verified.notificationId());
        assertEquals("wx-transaction-18", verified.channelTransactionNo());
        assertEquals("SUCCESS", verified.channelStatus());
        assertKeyWasZeroed();
        verify(credentials, times(2)).apiV3Key(TENANT, DIRECT_BINDING);
    }

    @Test
    void verifiesServiceProviderPaymentAndAllowsEmptyOrAbsentAssociatedData() throws Exception {
        var target = new ChannelPaymentTarget(new ChannelPaymentIdentity("payment-2", "attempt-2", Channel.WECHAT,
                "partner-revision", HASH, ORDER_NO), AMOUNT);
        for (boolean omitAssociatedData : List.of(false, true)) {
            var body = notification(payment(PARTNER), "TRANSACTION.SUCCESS", "transaction", "partner-notify", "", omitAssociatedData);
            var verified = TenantUtil.callWithTenant(TENANT,
                    () -> codec.verify(PARTNER_CONTEXT, target, signedInput(body)));
            assertEquals("partner-notify", verified.notificationId());
        }
        assertKeyWasZeroed();
        verify(credentials, times(2)).apiV3Key(TENANT, PARTNER_BINDING);
    }

    @Test
    void rejectsSignatureBodyAndHeaderTamperingBeforeReadingApiV3Key() throws Exception {
        byte[] body = notification(payment(DIRECT), "transaction", "transaction", "notify", "trade-table", false);
        var valid = signedInput(body);
        assertRejected(input(withByteChanged(body), signedHeaders(body, false)));
        assertRejected(input(body, signedHeaders(body, true)));
        assertRejected(input(body, Map.of("wechatpay-serial", List.of("wrong-serial"))));
        assertRejected(input(body, signedHeaders(body, true)));
        assertRejected(input(body, withHeader(signedHeaders(body, false), "wechatpay-signature-type", List.of("WECHATPAY2-RSA-SHA256"))));
        assertNotNull(valid);
        verify(credentials, never()).apiV3Key(anyString(), any());
    }

    @Test
    void rejectsUnknownSerialStaleTimestampAndDuplicateSignatureHeader() throws Exception {
        byte[] body = notification(payment(DIRECT), "TRANSACTION.SUCCESS", "transaction", "notify", "trade-table", false);
        var headers = signedHeaders(body, false);
        assertRejected(input(body, withHeader(headers, "wechatpay-serial", List.of("AAAAAAAA"))));
        assertRejected(input(body, withHeader(headers, "wechatpay-timestamp", List.of("1"))));
        assertRejected(input(body, withHeader(headers, "wechatpay-nonce", List.of(NONCE, "duplicate"))));
        verify(credentials, never()).apiV3Key(anyString(), any());
    }

    @Test
    void rejectsMalformedJsonUtf8WrongAccountAmountOrderAndRefundEvent() throws Exception {
        assertRejected(signedInput("{".getBytes(StandardCharsets.UTF_8)));
        assertRejected(signedInput(new byte[]{(byte) 0xc3, (byte) 0x28}));
        assertRejected(signedInput(notification(with(payment(DIRECT), "mchid", "\"other\""), "TRANSACTION.SUCCESS", "transaction", "n", "trade-table", false)));
        assertRejected(signedInput(notification(with(payment(DIRECT), "amount", Map.of("total", 125001, "currency", "CNY")), "TRANSACTION.SUCCESS", "transaction", "n", "trade-table", false)));
        assertRejected(signedInput(notification(with(payment(DIRECT), "out_trade_no", "\"another-order\""), "TRANSACTION.SUCCESS", "transaction", "n", "trade-table", false)));
        assertRejected(signedInput(notification(payment(DIRECT), "REFUND.SUCCESS", "refund", "n", "refund-table", false)));
    }

    @Test
    void rejectsBadAeadTagAndWrongAlgorithm() throws Exception {
        var valid = notification(payment(DIRECT), "TRANSACTION.SUCCESS", "transaction", "notify", "trade-table", false);
        assertRejected(signedInput(corruptTag(valid)));
        assertRejected(signedInput(new String(valid, StandardCharsets.UTF_8).replace("AEAD_AES_256_GCM", "AES_256_GCM")
                .getBytes(StandardCharsets.UTF_8)));
        verify(credentials, atLeastOnce()).apiV3Key(TENANT, DIRECT_BINDING);
        assertKeyWasZeroed();
    }

    private void assertRejected(ChannelNotificationInput input) {
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant(TENANT,
                () -> codec.verify(DIRECT_CONTEXT, TARGET, input)));
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
        return Map.of("Wechatpay-Serial", List.of("PUB_KEY_ID_test"),
                "Wechatpay-Timestamp", List.of(TIMESTAMP), "Wechatpay-Nonce", List.of(NONCE),
                "Wechatpay-Signature", List.of(Base64.getEncoder().encodeToString(signature)));
    }

    private ChannelNotificationInput input(byte[] body, Map<String, List<String>> headers) {
        return new ChannelNotificationInput("application/json; charset=UTF-8", headers, body);
    }

    private byte[] notification(String payment, String event, String originalType, String id, String associated,
                                boolean omitAssociatedData) throws Exception {
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] key = API_V3_KEY.getBytes(StandardCharsets.US_ASCII);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(128, RESOURCE_NONCE.getBytes(StandardCharsets.UTF_8)));
        if (!omitAssociatedData) {
            cipher.updateAAD(associated.getBytes(StandardCharsets.UTF_8));
        }
        String ciphertext = Base64.getEncoder().encodeToString(cipher.doFinal(payment.getBytes(StandardCharsets.UTF_8)));
        String resource = "{\"algorithm\":\"AEAD_AES_256_GCM\",\"original_type\":\"" + originalType
                + "\",\"nonce\":\"" + RESOURCE_NONCE + "\",\"ciphertext\":\"" + ciphertext + "\""
                + (omitAssociatedData ? "" : ",\"associated_data\":\"" + associated + "\"") + "}";
        return ("{\"id\":\"" + id + "\",\"create_time\":\"2026-10-24T12:00:00+08:00\",\"event_type\":\""
                + event + "\",\"resource_type\":\"encrypt-resource\",\"resource\":" + resource + "}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private WechatCredentials credentials(WechatAccountIdentity identity) {
        return new WechatCredentials(TENANT, identity.appId(), identity.merchantId(), "1234ABCD", null,
                "PUB_KEY_ID_test", signingKeys.getPublic());
    }
}
