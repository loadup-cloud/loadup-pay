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
package io.github.loadup.pay.channel.alipay.protocol;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.model.AlipayAccountIdentity;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.channel.api.model.ChannelNotificationHint;
import io.github.loadup.pay.channel.api.model.ChannelNotificationInput;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.channel.api.model.ChannelPaymentTarget;
import io.github.loadup.pay.channel.alipay.config.AlipayProperties;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AlipayNotificationCodecTest {
    private static final String ORDER_NO = "payment-request-18";
    private static final String HASH = "a".repeat(64);
    private static final Money AMOUNT = Money.ofMinor(125_000, "CNY");
    private static final ChannelBindingRevision BINDING = new ChannelBindingRevision("revision-1", "merchant-1",
            Channel.ALIPAY, AccessMode.DIRECT, HASH, "credential-ref", "3",
            new AlipayAccountIdentity("app", "seller", null, null, null), java.util.Set.of(PaymentMethod.PAYMENT_CODE));
    private static final ChannelExecutionContext CONTEXT = new ChannelExecutionContext("tenant", BINDING);
    private static final ChannelPaymentIdentity IDENTITY = new ChannelPaymentIdentity("payment-1", "attempt-1",
            Channel.ALIPAY, "revision-1", HASH, ORDER_NO);
    private static final ChannelPaymentTarget TARGET = new ChannelPaymentTarget(IDENTITY, AMOUNT);

    private final AlipayCredentialReader credentials = mock(AlipayCredentialReader.class);
    private final AlipayNotificationCodec codec = new AlipayNotificationCodec(credentials);

    @Test
    void identifyIsUntrustedAndDoesNotReadVerificationCredentials() throws Exception {
        var keys = rsaKeyPair();
        var signed = signedForm(fields(), keys);
        var input = input(signed);

        ChannelNotificationHint hint = codec.identify(input);

        assertEquals(ORDER_NO, hint.channelRequestNo());
        verifyNoInteractions(credentials);
        assertFalse(input.toString().contains(signed));
        assertFalse(input.toString().contains(signatureFrom(signed)));
        assertFalse(hint.toString().contains(signatureFrom(signed)));
    }

    @Test
    void verifiesRsa2CanonicalFormWithUnknownSignedUnicodeAndFormEncodedValues() throws Exception {
        var keys = rsaKeyPair();
        var values = fields();
        when(credentials.verificationKey("tenant", BINDING)).thenReturn(keys.getPublic());
        var signed = signedForm(values, keys);

        var verified = TenantUtil.callWithTenant("tenant", () -> codec.verify(CONTEXT, TARGET, input(signed)));

        assertEquals(IDENTITY, verified.identity());
        assertEquals(AMOUNT, verified.amount());
        assertEquals("notify-18", verified.notificationId());
        assertEquals("ali-trade-18", verified.channelTransactionNo());
        assertEquals("TRADE_SUCCESS", verified.channelStatus());
        var canonical = new TreeMap<>(values);
        canonical.remove("sign_type");
        assertEquals(DigestUtils.sha256(AlipayProtocolCodec.canonical(canonical)), verified.sourceDigest());
        assertFalse(verified.toString().contains(signatureFrom(signed)));
        assertFalse(verified.toString().contains(signed));
        verify(credentials).verificationKey("tenant", BINDING);
    }

    @Test
    void rejectsTamperingDuplicateFieldsMalformedUtf8PercentBase64AndProtocolParameters() throws Exception {
        var keys = rsaKeyPair();
        when(credentials.verificationKey("tenant", BINDING)).thenReturn(keys.getPublic());
        var signed = signedForm(fields(), keys);
        assertInvalid(signed.replace("1250.00", "1251.00"));
        assertInvalid(signed + "&notify_id=duplicate");
        assertInvalid(signed + "&extra_utf8=%FF");
        assertInvalid(signed + "&extra_percent=%Q0");
        assertInvalid(signed.replace(URLEncoder.encode(signatureFrom(signed), StandardCharsets.UTF_8), "%25%25%25"));
        assertInvalid(signed.replace("sign_type=RSA2", "sign_type=RSA1"));
        assertInvalid(signed, "application/x-www-form-urlencoded; charset=iso-8859-1");
    }

    @Test
    void rejectsMismatchedFrozenContextOwnerOrderAndAmount() throws Exception {
        var keys = rsaKeyPair();
        when(credentials.verificationKey("tenant", BINDING)).thenReturn(keys.getPublic());
        var signed = signedForm(fields(), keys);
        var wrongContextBinding = new ChannelBindingRevision("other-revision", "merchant-1", Channel.ALIPAY,
                AccessMode.DIRECT, HASH, "credential-ref", "3", BINDING.identity(), java.util.Set.of(PaymentMethod.PAYMENT_CODE));
        var wrongContext = new ChannelExecutionContext("tenant", wrongContextBinding);
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant",
                () -> codec.verify(wrongContext, TARGET, input(signed))));

        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant",
                () -> codec.verify(CONTEXT, new ChannelPaymentTarget(new ChannelPaymentIdentity("payment-1", "attempt-1",
                        Channel.ALIPAY, "revision-1", HASH, "other-order"), AMOUNT), input(signed))));
        assertInvalid(signedForm(with(fields(), "app_id", "other-app"), keys));
        assertInvalid(signedForm(with(fields(), "seller_id", "other-seller"), keys));
        assertInvalid(signedForm(with(fields(), "notify_type", "refund_status_sync"), keys));
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant",
                () -> codec.verify(CONTEXT, new ChannelPaymentTarget(IDENTITY, Money.ofMinor(125_001, "CNY")), input(signed))));
    }

    private void assertInvalid(String form) {
        assertInvalid(form, "application/x-www-form-urlencoded; charset=UTF-8");
    }

    private void assertInvalid(String form, String contentType) {
        assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant",
                () -> codec.verify(CONTEXT, TARGET, input(form, contentType))));
    }

    private static Map<String, String> fields() {
        var values = new LinkedHashMap<String, String>();
        values.put("app_id", "app");
        values.put("seller_id", "seller");
        values.put("out_trade_no", ORDER_NO);
        values.put("total_amount", "1250.00");
        values.put("notify_type", "trade_status_sync");
        values.put("notify_time", "2026-10-23 18:30:00");
        values.put("notify_id", "notify-18");
        values.put("trade_no", "ali-trade-18");
        values.put("trade_status", "TRADE_SUCCESS");
        values.put("subject", "午餐 + tea");
        values.put("extra_field", "unknown signed value");
        values.put("sign_type", "RSA2");
        values.put("charset", "utf-8");
        return values;
    }

    private static Map<String, String> with(Map<String, String> original, String key, String value) {
        var copy = new LinkedHashMap<>(original);
        copy.put(key, value);
        return copy;
    }

    private static String signedForm(Map<String, String> fields, KeyPair keys) throws Exception {
        var canonicalFields = new TreeMap<>(fields);
        canonicalFields.remove("sign");
        canonicalFields.remove("sign_type");
        var signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keys.getPrivate());
        signer.update(AlipayProtocolCodec.canonical(canonicalFields).getBytes(StandardCharsets.UTF_8));
        var signedFields = new LinkedHashMap<>(fields);
        signedFields.put("sign", Base64.getEncoder().encodeToString(signer.sign()));
        return signedFields.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String signatureFrom(String form) {
        for (var entry : form.split("&")) {
            if (entry.startsWith("sign=")) return java.net.URLDecoder.decode(entry.substring(5), StandardCharsets.UTF_8);
        }
        return "";
    }

    private static ChannelNotificationInput input(String body) {
        return input(body, "application/x-www-form-urlencoded; charset=UTF-8");
    }

    private static ChannelNotificationInput input(String body, String contentType) {
        return new ChannelNotificationInput(contentType, body.getBytes(StandardCharsets.US_ASCII));
    }

    private static KeyPair rsaKeyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
