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
import io.github.loadup.components.kms.KmsKeyRef;
import io.github.loadup.components.kms.KmsSignature;
import io.github.loadup.components.kms.KmsSignatureAlgorithm;
import io.github.loadup.components.kms.KmsTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WechatProtocolCodecTest {
    private static final Instant NOW = Instant.parse("2026-10-24T03:00:00Z");
    private static final String SERIAL = "ABCD1234ABCD1234";
    private static final String PLATFORM = "PUB_KEY_ID_synthetic";
    private final KmsTemplate kms = mock(KmsTemplate.class);
    private final WechatProtocolCodec codec = new WechatProtocolCodec(JsonMapper.builder().build(), kms,
            Clock.fixed(NOW, ZoneId.of("Asia/Shanghai")));

    @Test
    void signsExactEncodedPathQueryAndUtf8BodyWithFrozenKmsVersion() {
        when(kms.sign(any(), eq(KmsSignatureAlgorithm.RSA_SHA256_PKCS1), any()))
                .thenReturn(new KmsSignature("merchant", 4, KmsSignatureAlgorithm.RSA_SHA256_PKCS1, new byte[256]));
        var uri = URI.create("https://api.mch.weixin.qq.com/v3/pay/partner/transactions/out-trade-no/original_01?sub_mchid=2&sp_mchid=1");
        var headers = codec.sign(WechatMethod.PARTNER_QUERY, credentials(null), HttpMethod.GET, uri, new byte[0]);
        var nonce = Pattern.compile("nonce_str=\"([a-z0-9]{32})\"").matcher(headers.get("Authorization"));
        assertTrue(nonce.find());
        var canonical = ArgumentCaptor.forClass(byte[].class);
        verify(kms).sign(eq(new KmsKeyRef("merchant", 4)), eq(KmsSignatureAlgorithm.RSA_SHA256_PKCS1), canonical.capture());
        assertEquals("GET\n" + uri.getRawPath() + "?" + uri.getRawQuery() + "\n" + NOW.getEpochSecond() + "\n" + nonce.group(1) + "\n\n",
                new String(canonical.getValue(), StandardCharsets.UTF_8));
        assertEquals(PLATFORM, headers.get("Wechatpay-Serial"));
        clearInvocations(kms);
        byte[] body = "{\"description\":\"中文 & exact\"}".getBytes(StandardCharsets.UTF_8);
        var post = codec.sign(WechatMethod.DIRECT_JSAPI, credentials(null), HttpMethod.POST,
                URI.create("https://api.mch.weixin.qq.com/v3/pay/transactions/jsapi"), body);
        var postNonce = Pattern.compile("nonce_str=\"([a-z0-9]{32})\"").matcher(post.get("Authorization"));
        assertTrue(postNonce.find());
        verify(kms).sign(any(), any(), canonical.capture());
        assertEquals("POST\n/v3/pay/transactions/jsapi\n" + NOW.getEpochSecond() + "\n" + postNonce.group(1) + "\n"
                + new String(body, StandardCharsets.UTF_8) + "\n", new String(canonical.getValue(), StandardCharsets.UTF_8));
    }

    @Test
    void rejectsWrongOriginOperationMethodGetBodyTransactionAndKmsVersion() {
        var original = credentials(null);
        assertThrows(CommonException.class, () -> codec.sign(WechatMethod.DIRECT_QUERY, original, HttpMethod.GET,
                URI.create("https://other.example/v3/pay/transactions/out-trade-no/original"), new byte[0]));
        assertThrows(CommonException.class, () -> codec.sign(WechatMethod.DIRECT_JSAPI, original, HttpMethod.POST,
                URI.create("https://api.mch.weixin.qq.com/v3/pay/partner/transactions/jsapi"), new byte[0]));
        assertThrows(CommonException.class, () -> codec.sign(WechatMethod.DIRECT_QUERY, original, HttpMethod.POST,
                URI.create("https://api.mch.weixin.qq.com/v3/pay/transactions/out-trade-no/original"), new byte[0]));
        assertThrows(CommonException.class, () -> codec.sign(WechatMethod.DIRECT_QUERY, original, HttpMethod.GET,
                URI.create("https://api.mch.weixin.qq.com/v3/pay/transactions/out-trade-no/original"), new byte[] {1}));
        verifyNoInteractions(kms);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(CommonException.class, () -> codec.sign(WechatMethod.DIRECT_JSAPI, original, HttpMethod.POST,
                    URI.create("https://api.mch.weixin.qq.com/v3/pay/transactions/jsapi"), new byte[0]));
        } finally { TransactionSynchronizationManager.clear(); }
        when(kms.sign(any(), any(), any())).thenReturn(new KmsSignature("merchant", 5,
                KmsSignatureAlgorithm.RSA_SHA256_PKCS1, new byte[256]));
        assertThrows(CommonException.class, () -> codec.sign(WechatMethod.DIRECT_JSAPI, original, HttpMethod.POST,
                URI.create("https://api.mch.weixin.qq.com/v3/pay/transactions/jsapi"), new byte[0]));
    }

    @Test
    void verifiesRawBytesPinnedKeyAndFreshHeadersIncludingSignedEmptyClose() throws Exception {
        var pair = keyPair(); var original = credentials(pair);
        var raw = "{ \"prepay_id\":\"synthetic\", \"memo\":\"中文\" }".getBytes(StandardCharsets.UTF_8);
        var response = response(pair, raw, NOW.getEpochSecond(), 200);
        assertEquals(new String(raw, StandardCharsets.UTF_8), codec.verify(WechatMethod.DIRECT_JSAPI, original, response).payload());
        assertEquals("", codec.verify(WechatMethod.DIRECT_CLOSE, original, response(pair, new byte[0], NOW.getEpochSecond(), 204)).payload());
        assertThrows(CommonException.class, () -> codec.verify(WechatMethod.DIRECT_JSAPI, original,
                ResponseEntity.ok().headers(response.getHeaders()).body("{}".getBytes(StandardCharsets.UTF_8))));
        var wrongSerial = new HttpHeaders(); wrongSerial.addAll(response.getHeaders()); wrongSerial.set("Wechatpay-Serial", "PUB_KEY_ID_other");
        assertThrows(CommonException.class, () -> codec.verify(WechatMethod.DIRECT_JSAPI, original, ResponseEntity.ok().headers(wrongSerial).body(raw)));
        var duplicates = new HttpHeaders(); duplicates.addAll(response.getHeaders()); duplicates.add("Wechatpay-Nonce", "duplicate");
        assertThrows(CommonException.class, () -> codec.verify(WechatMethod.DIRECT_JSAPI, original, ResponseEntity.ok().headers(duplicates).body(raw)));
        assertThrows(CommonException.class, () -> codec.verify(WechatMethod.DIRECT_JSAPI, original,
                response(pair, raw, NOW.minusSeconds(300).getEpochSecond(), 200)));
        assertThrows(CommonException.class, () -> codec.verify(WechatMethod.DIRECT_JSAPI, original,
                response(pair, raw, NOW.plusSeconds(300).getEpochSecond(), 200)));
        assertThrows(CommonException.class, () -> codec.verify(WechatMethod.DIRECT_CLOSE, original, ResponseEntity.noContent().build()));
    }

    @Test
    void evenValidSignaturesCannotAdmitDuplicateJsonTrailingContentOrMalformedUtf8() throws Exception {
        var pair = keyPair(); var original = credentials(pair);
        for (String payload : new String[] {"{\"x\":1,\"x\":2}", "{} {}", "null", "[]"}) {
            var response = response(pair, payload.getBytes(StandardCharsets.UTF_8), NOW.getEpochSecond(), 200);
            assertThrows(CommonException.class, () -> codec.verify(WechatMethod.DIRECT_JSAPI, original, response));
        }
        var malformed = response(pair, new byte[] {(byte) 0xC3, (byte) 0x28}, NOW.getEpochSecond(), 200);
        assertThrows(CommonException.class, () -> codec.verify(WechatMethod.DIRECT_JSAPI, original, malformed));
    }

    private WechatCredentials credentials(KeyPair pair) {
        return new WechatCredentials("tenant", "wx-app", "1900000001", SERIAL, new KmsKeyRef("merchant", 4), PLATFORM,
                pair == null ? null : pair.getPublic());
    }
    private static KeyPair keyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); return generator.generateKeyPair();
    }
    private static ResponseEntity<byte[]> response(KeyPair pair, byte[] body, long timestamp, int status) throws Exception {
        var signer = Signature.getInstance("SHA256withRSA"); signer.initSign(pair.getPrivate());
        signer.update((timestamp + "\nnonce\n").getBytes(StandardCharsets.UTF_8)); signer.update(body); signer.update((byte) '\n');
        return ResponseEntity.status(status).header("Wechatpay-Serial", PLATFORM).header("Wechatpay-Timestamp", Long.toString(timestamp))
                .header("Wechatpay-Nonce", "nonce").header("Wechatpay-Signature", Base64.getEncoder().encodeToString(signer.sign())).body(body);
    }
}
