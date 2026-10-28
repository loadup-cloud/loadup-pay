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
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.http.HttpCall;
import io.github.loadup.components.http.HttpRequestSigner;
import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.components.kms.KmsKeyRef;
import io.github.loadup.components.kms.KmsSignature;
import io.github.loadup.components.kms.KmsSignatureAlgorithm;
import io.github.loadup.components.kms.KmsTemplate;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import io.github.loadup.pay.channel.api.model.ChannelStatementRequest;
import io.github.loadup.pay.channel.api.model.ChannelStatementScope;
import io.github.loadup.pay.channel.api.model.VerifiedChannelStatement;
import io.github.loadup.pay.channel.api.model.WechatAccountIdentity;
import io.github.loadup.pay.channel.wechat.converter.WechatStatementConverter;
import io.github.loadup.pay.channel.wechat.protocol.WechatCredentialReader;
import io.github.loadup.pay.channel.wechat.protocol.WechatCredentials;
import io.github.loadup.pay.channel.wechat.protocol.WechatProtocolCodec;
import io.github.loadup.pay.channel.wechat.protocol.WechatStatementCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_SIGNATURE_INVALID;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.STATEMENT_NOT_FOUND;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.STATEMENT_NOT_READY;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.UNSUPPORTED_CAPABILITY;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Real platform RSA verification and canonical request signing; HTTP and KMS endpoints are controlled doubles.
 */
class WechatStatementProviderTest {
    static final Instant NOW = Instant.parse("2026-10-28T04:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Shanghai"));
    static KeyPair platform;
    final JsonMapper json = JsonMapper.builder().build();
    final HttpTemplate http = mock(HttpTemplate.class);
    final KmsTemplate kms = mock(KmsTemplate.class);
    final WechatCredentialReader credentials = mock(WechatCredentialReader.class);
    final AnnotationConfigApplicationContext spring = new AnnotationConfigApplicationContext(WechatStatementConverter.class.getPackageName());
    final WechatProtocolCodec signatures = new WechatProtocolCodec(json, kms, CLOCK);
    final WechatStatementProvider provider = new WechatStatementProvider(http, credentials, signatures, json, spring.getBean(WechatStatementConverter.class), CLOCK);
    final byte[] content = "synthetic-official-source\n".getBytes(StandardCharsets.UTF_8);
    final AtomicReference<Map<String, Object>> grant = new AtomicReference<>();
    final AtomicInteger status = new AtomicInteger(200);
    final AtomicBoolean validSignature = new AtomicBoolean(true);
    final AtomicReference<HttpCall> applied = new AtomicReference<>();
    final AtomicReference<String> downloadedClient = new AtomicReference<>();
    final ChannelStatementScope scope = new ChannelStatementScope(Channel.WECHAT, "a".repeat(64), LocalDate.of(2026, 10, 27), WechatStatementCodec.PROFILE);

    @BeforeAll
    static void key() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        platform = generator.generateKeyPair();
    }

    @BeforeEach
    void setup() {
        grant.set(Map.of("hash_type", "SHA1", "hash_value", WechatStatementCodec.digest("SHA-1", content), "download_url", "https://api.mch.weixin.qq.com/v3/billdownload/file?token=private%2Btoken%2Fvalue%3D"));
        when(kms.sign(any(), eq(KmsSignatureAlgorithm.RSA_SHA256_PKCS1), any())).thenReturn(new KmsSignature("signer", 1, KmsSignatureAlgorithm.RSA_SHA256_PKCS1, new byte[256]));
        when(credentials.read(eq("tenant"), any())).thenReturn(new WechatCredentials("tenant", "app", "190000001", "ABCD1234ABCD1234", new KmsKeyRef("signer", 1), "PUB_KEY_ID_statement", platform.getPublic()));
        when(http.exchange(eq("wechat"), eq("trade-bill"), any(HttpCall.class), any(HttpRequestSigner.class))).thenAnswer(call -> {
            HttpCall request = call.getArgument(2);
            applied.set(request);
            HttpRequestSigner signer = call.getArgument(3);
            signer.sign(HttpMethod.GET, uri("api.mch.weixin.qq.com", "/v3/bill/tradebill", request), new byte[0]);
            byte[] body = json.writeValueAsBytes(grant.get());
            return response(body);
        });
        when(http.download(anyString(), eq("bill-download"), any(HttpCall.class), any(OutputStream.class), any(HttpRequestSigner.class))).thenAnswer(call -> {
            String client = call.getArgument(0);
            downloadedClient.set(client);
            HttpCall request = call.getArgument(2);
            assertEquals(Map.of("token", List.of("private+token/value=")), request.query());
            assertEquals("identity", request.headers().get("Accept-Encoding"));
            HttpRequestSigner signer = call.getArgument(4);
            signer.sign(HttpMethod.GET, uri(client.equals("wechat-bill-download") ? "api.mch.weixin.qq.com" : "api2.mch.weixin.qq.com", "/v3/billdownload/file", request), new byte[0]);
            ((OutputStream) call.getArgument(3)).write(content);
            // Official file downloads have no Wechatpay-Signature. Their signed grant SHA1 is the integrity check.
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body((long) content.length);
        });
    }

    @AfterEach
    void close() {
        spring.close();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void signedGrantAndUnsignedFileRetainExactBytesPrivateProofAndOriginalSubMerchant() throws Exception {
        var context = context(true);
        var result = fetch(context, 1024);
        assertArrayEquals(content, result.content());
        assertEquals(scope, result.scope());
        assertEquals("revision", result.bindingRevisionId());
        assertEquals("wechat-bill-download", downloadedClient.get());
        assertEquals("SHA1", result.receipt().officialHashType());
        assertEquals(WechatStatementCodec.digest("SHA-1", content), result.receipt().officialHash());
        assertEquals(WechatStatementCodec.digest("SHA-256", content), result.receipt().contentSha256());
        assertEquals(WechatStatementCodec.digest("SHA-256", result.receipt().evidence()), result.receipt().evidenceSha256());
        assertEquals(Map.of("bill_date", List.of("2026-10-27"), "bill_type", List.of("ALL"), "sub_mchid", List.of("190000002")), applied.get().query());
        assertFalse(applied.get().query().containsKey("tar_type"));
        var frame = new DataInputStream(new ByteArrayInputStream(result.receipt().evidence()));
        var fields = new ArrayList<byte[]>();
        while (frame.available() > 0) {
            int length = frame.readInt();
            assertTrue(length >= 0 && length <= 8192);
            var bytes = frame.readNBytes(length);
            assertEquals(length, bytes.length);
            fields.add(bytes);
        }
        assertEquals(17, fields.size());
        assertEquals("credential", new String(fields.get(7), StandardCharsets.UTF_8));
        assertEquals("v1", new String(fields.get(8), StandardCharsets.UTF_8));
        assertTrue(new String(fields.get(10), StandardCharsets.UTF_8).startsWith("WECHATPAY2-SHA256-RSA2048 "));
        assertArrayEquals(json.writeValueAsBytes(grant.get()), fields.get(16));
        assertFalse(result.toString().contains("private+token"));
        assertFalse(result.receipt().toString().contains("private%2Btoken"));
        verify(credentials, times(1)).read("tenant", context.binding());
        verify(http, times(1)).download(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void directMerchantDoesNotRequestProviderWideOrSubMerchantDataAndBackupOriginIsExplicit() {
        var value = new HashMap<>(grant.get());
        value.put("download_url", "https://api2.mch.weixin.qq.com/v3/billdownload/file?token=private%2Btoken%2Fvalue%3D");
        grant.set(value);
        fetch(context(false), 1024);
        assertFalse(applied.get().query().containsKey("sub_mchid"));
        assertEquals("wechat-bill-download-backup", downloadedClient.get());
    }

    @Test
    void invalidSignatureStopsBeforeAnyDownload() {
        validSignature.set(false);
        assertEquals(CHANNEL_SIGNATURE_INVALID, assertThrows(CommonException.class, () -> fetch(context(false), 1024)).getResultCode());
        verify(http, never()).download(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void signedUnavailableOrGeneratingResponsesNeverBecomeEmptySuccessfulStatements() {
        status.set(400);
        grant.set(Map.of("code", "NO_STATEMENT_EXIST"));
        assertEquals(STATEMENT_NOT_FOUND, assertThrows(CommonException.class, () -> fetch(context(false), 1024)).getResultCode());
        grant.set(Map.of("code", "STATEMENT_CREATING"));
        assertEquals(STATEMENT_NOT_READY, assertThrows(CommonException.class, () -> fetch(context(false), 1024)).getResultCode());
        verify(http, never()).download(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void fileHashMismatchAndStreamOverflowRejectTheWholeSource() {
        var changed = new HashMap<>(grant.get());
        changed.put("hash_value", "0".repeat(40));
        grant.set(changed);
        assertEquals(CHANNEL_RESPONSE_INVALID, assertThrows(CommonException.class, () -> fetch(context(false), 1024)).getResultCode());
        changed.put("hash_value", WechatStatementCodec.digest("SHA-1", content));
        grant.set(changed);
        assertEquals(CHANNEL_RESPONSE_INVALID, assertThrows(CommonException.class, () -> fetch(context(false), 2)).getResultCode());
    }

    @Test
    void signedButInvalidDownloadTargetsAndUnsupportedCompressionDoNotReachTransport() {
        for (var url : List.of("https:///v3/billdownload/file?token=x", "http://api.mch.weixin.qq.com/v3/billdownload/file?token=x", "https://127.0.0.1/v3/billdownload/file?token=x",
                "https://api.mch.weixin.qq.com.evil.test/v3/billdownload/file?token=x", "https://user@api.mch.weixin.qq.com/v3/billdownload/file?token=x",
                "https://api.mch.weixin.qq.com:8443/v3/billdownload/file?token=x", "https://api.mch.weixin.qq.com/v3/billdownload/../file?token=x",
                "https://api.mch.weixin.qq.com/v3/billdownload/file?token=x&token=y", "https://api.mch.weixin.qq.com/v3/billdownload/file?token=x&tartype=gzip",
                "https://api.mch.weixin.qq.com/v3/billdownload/file?token=x#secret", "https://api.mch.weixin.qq.com/v3/billdownload/file?token=%0d%0a")) {
            var changed = new HashMap<>(grant.get());
            changed.put("download_url", url);
            grant.set(changed);
            assertEquals(CHANNEL_RESPONSE_INVALID, assertThrows(CommonException.class, () -> fetch(context(false), 1024)).getResultCode());
        }
        verify(http, never()).download(anyString(), anyString(), any(), any(), any());
    }

    @Test
    void wrongTenantActiveTransactionAndUnsupportedProfileFailBeforeCredentialsOrHttp() {
        assertEquals(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("other", () -> provider.download(context(false), new ChannelStatementRequest(scope, 1024)))).getResultCode());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertEquals(INVALID_STATE, assertThrows(CommonException.class, () -> fetch(context(false), 1024)).getResultCode());
        } finally {
            TransactionSynchronizationManager.clear();
        }
        var invalid = new ChannelStatementScope(scope.channel(), scope.accountScopeHash(), scope.billDate(), "unsupported");
        assertEquals(UNSUPPORTED_CAPABILITY, assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant", () -> provider.download(context(false), new ChannelStatementRequest(invalid, 1024)))).getResultCode());
        verifyNoInteractions(credentials, http, kms);
    }

    private VerifiedChannelStatement fetch(ChannelExecutionContext context, int max) {
        return TenantUtil.callWithTenant("tenant", () -> provider.download(context, new ChannelStatementRequest(scope, max)));
    }

    private ChannelExecutionContext context(boolean partner) {
        var identity = new WechatAccountIdentity("app", "190000001", null, partner ? "190000002" : null);
        return new ChannelExecutionContext("tenant", new ChannelBindingRevision("revision", "merchant", Channel.WECHAT, identity.accessMode(), scope.accountScopeHash(), "credential", "v1", identity, Set.of(PaymentMethod.H5)));
    }

    private URI uri(String host, String path, HttpCall request) {
        var query = request.query().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue().getFirst(), StandardCharsets.UTF_8).replace("+", "%20")).collect(java.util.stream.Collectors.joining("&"));
        return URI.create("https://" + host + path + "?" + query);
    }

    private ResponseEntity<byte[]> response(byte[] body) throws Exception {
        String timestamp = Long.toString(NOW.getEpochSecond()), nonce = "synthetic-nonce";
        var signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(platform.getPrivate());
        signer.update((timestamp + "\n" + nonce + "\n").getBytes(StandardCharsets.UTF_8));
        signer.update(validSignature.get() ? body : "tampered".getBytes(StandardCharsets.UTF_8));
        signer.update((byte) '\n');
        var headers = new HttpHeaders();
        headers.set("Wechatpay-Serial", "PUB_KEY_ID_statement");
        headers.set("Wechatpay-Timestamp", timestamp);
        headers.set("Wechatpay-Nonce", nonce);
        headers.set("Wechatpay-Signature", Base64.getEncoder().encodeToString(signer.sign()));
        return new ResponseEntity<>(body, headers, HttpStatusCode.valueOf(status.get()));
    }
}
