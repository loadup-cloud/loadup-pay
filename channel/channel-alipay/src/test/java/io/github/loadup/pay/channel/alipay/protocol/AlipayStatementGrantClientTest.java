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
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.http.HttpCall;
import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.components.kms.KmsKeyRef;
import io.github.loadup.components.kms.KmsSignature;
import io.github.loadup.components.kms.KmsSignatureAlgorithm;
import io.github.loadup.components.kms.KmsTemplate;
import io.github.loadup.pay.channel.api.model.AlipayAccountIdentity;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_SIGNATURE_INVALID;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.STATEMENT_NOT_FOUND;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.STATEMENT_RATE_LIMITED;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.UNSUPPORTED_CAPABILITY;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Uses synthetic responses signed by a real RSA key; no network, credential files or bill downloads.
 */
class AlipayStatementGrantClientTest {
    static final Instant NOW = Instant.parse("2026-10-28T04:00:00Z");
    static final LocalDate BILL_DATE = LocalDate.of(2026, 10, 27);
    static final String TOKEN = "synthetic-private-delegation-token";
    static final String URL = "http://dwbillcenter.alipay.com/downloadBillFile.resource?fileId=private-file-grant";
    static KeyPair platform;
    final AtomicReference<Instant> current = new AtomicReference<>(NOW);
    final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Shanghai");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    };
    final JsonMapper json = JsonMapper.builder().build();
    final HttpTemplate http = mock(HttpTemplate.class);
    final KmsTemplate kms = mock(KmsTemplate.class);
    final AlipayCredentialReader credentials = mock(AlipayCredentialReader.class);
    final AlipayProtocolCodec codec = new AlipayProtocolCodec(json, kms, clock);
    final AlipayProtocolClient protocol = new AlipayProtocolClient(http, credentials, codec);
    final AlipayStatementGrantClient client = new AlipayStatementGrantClient(protocol, json, clock);
    final AtomicReference<Map<String, Object>> payload = new AtomicReference<>();
    final AtomicReference<String> sent = new AtomicReference<>();
    final AtomicReference<byte[]> received = new AtomicReference<>();
    Duration responseDelay = Duration.ZERO;
    boolean corruptSignature;

    @BeforeAll
    static void key() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        platform = generator.generateKeyPair();
    }

    private static Map<String, String> form(String body) {
        var result = new HashMap<String, String>();
        for (String field : body.split("&")) {
            var pair = field.split("=", 2);
            result.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return result;
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @BeforeEach
    void setup() {
        payload.set(Map.of("code", "10000", "bill_download_url", URL));
        when(kms.sign(any(), eq(KmsSignatureAlgorithm.RSA_SHA256_PKCS1), any()))
                .thenReturn(new KmsSignature("signer", 7, KmsSignatureAlgorithm.RSA_SHA256_PKCS1, new byte[256]));
        when(credentials.read(eq("tenant"), any())).thenAnswer(call -> {
            ChannelBindingRevision binding = call.getArgument(1);
            return new AlipayCredentials("tenant", "app", "seller", new KmsKeyRef("signer", 7),
                    platform.getPublic(), binding.accessMode() == AccessMode.SERVICE_PROVIDER ? TOKEN : null);
        });
        when(http.exchange(eq("alipay"), eq("bill-download-url"), any(HttpCall.class))).thenAnswer(call -> {
            HttpCall request = call.getArgument(2);
            assertTrue(request.pathVariables().isEmpty());
            assertTrue(request.query().isEmpty());
            assertEquals("application/x-www-form-urlencoded;charset=UTF-8", request.headers().get("Content-Type"));
            sent.set((String) request.body());
            String raw = json.writeValueAsString(payload.get());
            var signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(platform.getPrivate());
            signer.update(raw.getBytes(StandardCharsets.UTF_8));
            String signature = corruptSignature ? "AQ==" : Base64.getEncoder().encodeToString(signer.sign());
            byte[] response = ("{\"" + AlipayMethod.BILL_DOWNLOAD_URL.responseNode() + "\":" + raw
                    + ",\"sign\":\"" + signature + "\"}").getBytes(StandardCharsets.UTF_8);
            received.set(response);
            current.set(NOW.plus(responseDelay));
            return ResponseEntity.ok(response);
        });
    }

    @AfterEach
    void clear() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void originalDelegationAndRequestDigestBindThePrivateVerifiedEnvelope() throws Exception {
        var context = context(true);
        var grant = request(context);
        var form = form(sent.get());
        assertEquals("alipay.data.dataservice.bill.downloadurl.query", form.get("method"));
        assertEquals(TOKEN, form.get("app_auth_token"));
        assertEquals("app", form.get("app_id"));
        assertEquals(Map.of("bill_type", "trade", "bill_date", "2026-10-27"), json.readValue(form.get("biz_content"), Map.class));
        assertFalse(form.containsKey("smid"));
        assertFalse(form.containsKey("seller_id"));
        assertEquals(DigestUtils.sha256(sent.get().getBytes(StandardCharsets.UTF_8)), grant.requestSha256());
        assertEquals(DigestUtils.sha256(grant.evidence()), grant.evidenceSha256());
        assertEquals("revision", grant.bindingRevisionId());
        assertEquals("a".repeat(64), grant.accountScopeHash());
        assertEquals(BILL_DATE, grant.billDate());
        assertEquals(NOW.plusSeconds(30), grant.expiresAt().toInstant());
        assertEquals(URL, grant.downloadUri().toASCIIString());

        var frame = new DataInputStream(new ByteArrayInputStream(grant.evidence()));
        var fields = new ArrayList<byte[]>();
        while (frame.available() > 0) {
            int length = frame.readInt();
            assertTrue(length == -1 || length >= 0 && length <= 16384);
            byte[] bytes = length == -1 ? null : frame.readNBytes(length);
            if (length >= 0) assertEquals(length, bytes.length);
            fields.add(bytes);
        }
        assertEquals(18, fields.size());
        assertEquals(AlipayStatementGrantClient.FORMAT, text(fields.get(0)));
        assertEquals("tenant", text(fields.get(1)));
        assertEquals("seller", text(fields.get(7)));
        assertEquals("operator", text(fields.get(8)));
        assertEquals("credential", text(fields.get(9)));
        assertEquals("v1", text(fields.get(10)));
        assertEquals("authorization", text(fields.get(11)));
        assertEquals("v2", text(fields.get(12)));
        assertEquals(grant.requestSha256(), text(fields.get(14)));
        assertArrayEquals(received.get(), fields.get(17));
        assertFalse(new String(grant.evidence(), StandardCharsets.UTF_8).contains(TOKEN));
        assertFalse(grant.toString().contains("private-file-grant"));
        byte[] changed = grant.evidence();
        changed[0] ^= 1;
        assertEquals(DigestUtils.sha256(grant.evidence()), grant.evidenceSha256());
        verify(credentials).read("tenant", context.binding());
        verify(http).exchange(eq("alipay"), eq("bill-download-url"), any(HttpCall.class));
        verifyNoMoreInteractions(http);
    }

    @Test
    void directMerchantNeverAddsDelegationOrPlatformWideBillSelection() throws Exception {
        var grant = request(context(false));
        var form = form(sent.get());
        assertFalse(form.containsKey("app_auth_token"));
        assertEquals(Map.of("bill_type", "trade", "bill_date", BILL_DATE.toString()), json.readValue(form.get("biz_content"), Map.class));
        assertNull(grant.billFileCode());
    }

    @Test
    void emptyDataMarkerRemainsOnlyAGrantAndStillRequiresADownloadUrl() {
        payload.set(Map.of("code", "10000", "bill_file_code", "EMPTY_DATA_WITH_BILL_FILE", "bill_download_url", URL));
        assertEquals("EMPTY_DATA_WITH_BILL_FILE", request(context(false)).billFileCode());
        payload.set(Map.of("code", "10000", "bill_file_code", "EMPTY_DATA_WITH_BILL_FILE"));
        assertFailure(CHANNEL_RESPONSE_INVALID);
        payload.set(Map.of("code", "10000", "bill_file_code", "UNRECOGNIZED", "bill_download_url", URL));
        assertFailure(CHANNEL_RESPONSE_INVALID);
    }

    @Test
    void signedBusinessFailuresRetainTheirMeaningAndNeverReturnZeroRows() {
        for (var code : List.of("BILL_NOT_EXIST", "NO_BILL_DATA")) {
            payload.set(Map.of("code", "40004", "sub_code", code));
            assertFailure(STATEMENT_NOT_FOUND);
        }
        for (var code : List.of("SYSTEM_RATE_LIMIT", "USER_RATE_LIMIT")) {
            payload.set(Map.of("code", "40004", "sub_code", code));
            assertFailure(STATEMENT_RATE_LIMITED);
        }
        payload.set(Map.of("code", "40004", "sub_code", "TYPE_NOT_SUPPORTED"));
        assertFailure(UNSUPPORTED_CAPABILITY);
        for (var code : List.of("INVAILID_ARGUMENTS", "BILL_DATE_BEFORE_REGISTRATION")) {
            payload.set(Map.of("code", "40004", "sub_code", code));
            assertFailure(INVALID_ARGUMENT);
        }
        payload.set(Map.of("code", "20000", "sub_code", "UNKNOWN_ERROR"));
        assertFailure(DEPENDENCY_UNAVAILABLE);
    }

    @Test
    void signatureFailurePrecedesGrantCreationEvenForMissingBillErrors() {
        corruptSignature = true;
        payload.set(Map.of("code", "40004", "sub_code", "NO_BILL_DATA"));
        assertFailure(CHANNEL_SIGNATURE_INVALID);
    }

    @Test
    void apiLatencyConsumesTheOriginalThirtySecondWindow() {
        responseDelay = Duration.ofSeconds(29);
        var grant = request(context(false));
        assertEquals(NOW.plusSeconds(30), grant.expiresAt().toInstant());
        assertEquals(NOW.plusSeconds(29), grant.receivedAt().toInstant());
        current.set(NOW);
        responseDelay = Duration.ofSeconds(30);
        assertFailure(DEPENDENCY_UNAVAILABLE);
    }

    @Test
    void malformedOrNonWebTargetsAreRejectedWithoutAnyDownload() {
        for (var url : List.of("/relative?token=x", "file:///tmp/private", "ftp://example.test/bill", "https:///missing-host",
                "https://user:secret@example.test/bill", "https://example.test/bill#secret", "http://[bad", URL + "\nsecret")) {
            payload.set(Map.of("code", "10000", "bill_download_url", url));
            assertFailure(CHANNEL_RESPONSE_INVALID);
        }
    }

    @Test
    void currentDayWrongTenantAndActiveTransactionFailBeforeCredentialResolution() {
        assertEquals(INVALID_ARGUMENT, assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("tenant",
                () -> client.request(context(false), BILL_DATE.plusDays(1)))).getResultCode());
        assertEquals(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> TenantUtil.callWithTenant("other",
                () -> client.request(context(false), BILL_DATE))).getResultCode());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertFailure(INVALID_STATE);
        verifyNoInteractions(credentials, http, kms);
    }

    @Test
    void privateExchangeDefensivelyCopiesTheExactVerifiedResponse() {
        var context = context(false);
        var exchange = TenantUtil.callWithTenant("tenant", () -> protocol.exchangeVerified("tenant", context.binding(),
                AlipayMethod.BILL_DOWNLOAD_URL, "{\"bill_type\":\"trade\",\"bill_date\":\"2026-10-27\"}", Map.of()));
        assertArrayEquals(received.get(), exchange.responseBytes());
        received.get()[0] ^= 1;
        assertNotEquals(received.get()[0], exchange.responseBytes()[0]);
        byte[] changed = exchange.responseBytes();
        changed[0] ^= 1;
        assertNotEquals(changed[0], exchange.responseBytes()[0]);
        assertFalse(exchange.toString().contains("private-file-grant"));
    }

    private AlipayStatementGrant request(ChannelExecutionContext context) {
        return TenantUtil.callWithTenant("tenant", () -> client.request(context, BILL_DATE));
    }

    private void assertFailure(io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum code) {
        assertEquals(code, assertThrows(CommonException.class, () -> request(context(false))).getResultCode());
    }

    private ChannelExecutionContext context(boolean partner) {
        var identity = new AlipayAccountIdentity("app", "seller", partner ? "operator" : null,
                partner ? "authorization" : null, partner ? "v2" : null);
        return new ChannelExecutionContext("tenant", new ChannelBindingRevision("revision", "merchant", Channel.ALIPAY,
                identity.accessMode(), "a".repeat(64), "credential", "v1", identity, Set.of(PaymentMethod.JSAPI)));
    }
}
