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

import io.github.loadup.components.kms.KmsKeyRef;
import io.github.loadup.components.kms.KmsSignature;
import io.github.loadup.components.kms.KmsSignatureAlgorithm;
import io.github.loadup.components.kms.KmsTemplate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AlipayProtocolCodecTest {
    private final KmsTemplate kms = mock(KmsTemplate.class);
    private final AlipayProtocolCodec codec = new AlipayProtocolCodec(JsonMapper.builder().build(), kms,
            Clock.fixed(Instant.parse("2026-10-23T10:11:12Z"), ZoneOffset.UTC));
    private final AlipayCredentials credentials = new AlipayCredentials("tenant", "app", "seller",
            new KmsKeyRef("signer", 7), null, "secret-auth-token");

    private static KmsSignature signature(String name, int version, KmsSignatureAlgorithm algorithm) {
        return new KmsSignature(name, version, algorithm, new byte[]{1});
    }

    private static KeyPair keyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String response(AlipayMethod method, String payload, KeyPair pair) throws Exception {
        var signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(pair.getPrivate());
        signer.update(payload.getBytes(StandardCharsets.UTF_8));
        return "{\"" + method.responseNode() + "\":" + payload + ",\"sign\":\""
                + Base64.getEncoder().encodeToString(signer.sign()) + "\"}";
    }

    @Test
    void signsSortedOriginalCanonicalValuesThenFormEncodesAndHidesAuthorization() throws Exception {
        var signature = signature("signer", 7, KmsSignatureAlgorithm.RSA_SHA256_PKCS1);
        when(kms.sign(any(), eq(KmsSignatureAlgorithm.RSA_SHA256_PKCS1), any())).thenReturn(signature);
        var business = "{\"amount\":\"1.00\",\"memo\":\"a b\"}";

        var request = codec.sign(AlipayMethod.PAY, credentials, business,
                Map.of("notify_url", "https://merchant.example/notify?a=b c"));

        var bytes = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(kms).sign(any(), eq(KmsSignatureAlgorithm.RSA_SHA256_PKCS1), bytes.capture());
        assertEquals("app_auth_token=secret-auth-token&app_id=app&biz_content=" + business
                + "&charset=utf-8&format=json&method=alipay.trade.pay"
                + "&notify_url=https://merchant.example/notify?a=b c&sign_type=RSA2&timestamp=2026-10-23 18:11:12"
                + "&version=1.0", new String(bytes.getValue(), StandardCharsets.UTF_8));
        assertEquals("AQ==", request.parameters().get("sign"));
        assertTrue(request.formBody().contains("app_auth_token=secret-auth-token"));
        assertTrue(URLDecoder.decode(request.formBody(), StandardCharsets.UTF_8).contains("notify_url=https://merchant.example/notify?a=b c"));
        assertFalse(request.toString().contains("secret-auth-token"));
    }

    @Test
    void wapSigningRefusesDelegatedTokensBeforeKmsRatherThanRemovingTheAuthorization() {
        var failure = assertThrows(io.github.loadup.commons.error.CommonException.class,
                () -> codec.sign(AlipayMethod.WAP_PAY, credentials,
                        "{\"product_code\":\"QUICK_WAP_WAY\"}", Map.of()));
        assertEquals(io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.UNSUPPORTED_CAPABILITY,
                failure.getResultCode());
        verifyNoInteractions(kms);
    }

    @Test
    void verifiesExactRawNestedPayloadAndRejectsInvalidEnvelopes() throws Exception {
        var pair = keyPair();
        var credentials = new AlipayCredentials("tenant", "app", "seller", new KmsKeyRef("signer", 1), pair.getPublic(), null);
        var raw = "{ \"message\":\"hello 中文\\nthere\", \"nested\":{\"x\": [1, true]} }";
        var response = response(AlipayMethod.QUERY, raw, pair);
        var verified = codec.verify(AlipayMethod.QUERY, credentials, response.getBytes(StandardCharsets.UTF_8));
        assertEquals(raw, verified.payload());
        assertEquals(io.github.loadup.commons.util.DigestUtils.sha256(raw), verified.sourceDigest());

        assertThrows(RuntimeException.class, () -> codec.verify(AlipayMethod.QUERY, credentials,
                response.replace("hello 中文", "tampered 中文").getBytes(StandardCharsets.UTF_8)));
        assertThrows(RuntimeException.class, () -> codec.verify(AlipayMethod.PAY, credentials, response.getBytes(StandardCharsets.UTF_8)));
        assertThrows(RuntimeException.class, () -> codec.verify(AlipayMethod.QUERY, credentials,
                (response.substring(0, response.length() - 1) + ",\"extra\":1}").getBytes(StandardCharsets.UTF_8)));
        assertThrows(RuntimeException.class, () -> codec.verify(AlipayMethod.QUERY, credentials,
                (response + " {}").getBytes(StandardCharsets.UTF_8)));
        assertThrows(RuntimeException.class, () -> codec.verify(AlipayMethod.QUERY, credentials,
                response.replaceFirst(",\"sign\":\"[^\"]+\"", "").getBytes(StandardCharsets.UTF_8)));
        assertThrows(RuntimeException.class, () -> codec.verify(AlipayMethod.QUERY, credentials,
                response.replace("\"x\": [1, true]", "\"x\": [1, true],\"x\":0").getBytes(StandardCharsets.UTF_8)));
        assertThrows(RuntimeException.class, () -> codec.verify(AlipayMethod.QUERY, credentials,
                response.replace(",\"sign\":", ",\"" + AlipayMethod.QUERY.responseNode() + "\":{},\"sign\":").getBytes(StandardCharsets.UTF_8)));
        assertThrows(RuntimeException.class, () -> codec.verify(AlipayMethod.QUERY, credentials,
                response.replaceFirst("\"sign\":\"[^\"]+\"", "\"sign\":\"AQ==\"").getBytes(StandardCharsets.UTF_8)));
        assertThrows(RuntimeException.class, () -> codec.verify(AlipayMethod.QUERY, credentials,
                response.replace("\"sign\":", "\"sign\":\"bad\",\"sign\":").getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsBadKmsMetadataAndSigningInsideAnActiveTransaction() {
        when(kms.sign(any(), eq(KmsSignatureAlgorithm.RSA_SHA256_PKCS1), any()))
                .thenReturn(signature("other", 7, KmsSignatureAlgorithm.RSA_SHA256_PKCS1));
        assertThrows(RuntimeException.class, () -> codec.sign(AlipayMethod.PAY, credentials, "{\"x\":1}", Map.of()));
        when(kms.sign(any(), eq(KmsSignatureAlgorithm.RSA_SHA256_PKCS1), any()))
                .thenReturn(signature("signer", 8, KmsSignatureAlgorithm.RSA_SHA256_PKCS1));
        assertThrows(RuntimeException.class, () -> codec.sign(AlipayMethod.PAY, credentials, "{\"x\":1}", Map.of()));
        when(kms.sign(any(), eq(KmsSignatureAlgorithm.RSA_SHA256_PKCS1), any()))
                .thenReturn(signature("signer", 7, KmsSignatureAlgorithm.RSA_SHA256_PSS));
        assertThrows(RuntimeException.class, () -> codec.sign(AlipayMethod.PAY, credentials, "{\"x\":1}", Map.of()));

        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(RuntimeException.class, () -> codec.sign(AlipayMethod.PAY, credentials, "{\"x\":1}", Map.of()));
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clear();
        }
    }
}
