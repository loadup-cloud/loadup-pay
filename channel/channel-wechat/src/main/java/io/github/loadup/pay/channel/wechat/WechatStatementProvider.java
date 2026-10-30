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
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.http.HttpCall;
import io.github.loadup.components.http.HttpTemplate;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import io.github.loadup.pay.channel.api.model.ChannelStatementReceipt;
import io.github.loadup.pay.channel.api.model.ChannelStatementRequest;
import io.github.loadup.pay.channel.api.model.VerifiedChannelStatement;
import io.github.loadup.pay.channel.api.model.WechatAccountIdentity;
import io.github.loadup.pay.channel.api.spi.ChannelStatementProvider;
import io.github.loadup.pay.channel.wechat.converter.WechatStatementConverter;
import io.github.loadup.pay.channel.wechat.protocol.WechatCredentialReader;
import io.github.loadup.pay.channel.wechat.protocol.WechatMethod;
import io.github.loadup.pay.channel.wechat.protocol.WechatProtocolCodec;
import io.github.loadup.pay.channel.wechat.protocol.WechatResponseReader;
import io.github.loadup.pay.channel.wechat.protocol.WechatStatementCodec;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_BINDING_MISMATCH;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_CREDENTIAL_UNAVAILABLE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.STATEMENT_NOT_FOUND;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.STATEMENT_NOT_READY;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.UNSUPPORTED_CAPABILITY;

/**
 * Isolated official ALL statement strategy. A signed API grant plus exact file SHA1 is mandatory.
 */
public final class WechatStatementProvider implements ChannelStatementProvider {
    private final HttpTemplate http;
    private final WechatCredentialReader credentials;
    private final WechatProtocolCodec signatures;
    private final WechatResponseReader reader;
    private final WechatStatementConverter converter;
    private final Clock clock;

    public WechatStatementProvider(HttpTemplate http, WechatCredentialReader credentials, WechatProtocolCodec signatures,
                                   JsonMapper json, WechatStatementConverter converter, Clock clock) {
        this.http = http;
        this.credentials = credentials;
        this.signatures = signatures;
        this.reader = new WechatResponseReader(json);
        this.converter = converter;
        this.clock = clock;
    }

    @Override
    public Channel channel() {
        return Channel.WECHAT;
    }

    @Override
    public VerifiedChannelStatement download(ChannelExecutionContext context, ChannelStatementRequest request) {
        guard(context, request);
        var account = (WechatAccountIdentity) context.binding().identity();
        var original = credentials.read(context.tenantId(), context.binding()); // One immutable credential/pin for the whole grant/download exchange.
        if (original == null || !context.tenantId().equals(original.tenantId()) || !account.appId().equals(original.appId()) || !account.merchantId().equals(original.merchantId())) {
            throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
        }
        var expected = new HashMap<String, String>();
        expected.put("bill_date", request.scope().billDate().toString());
        expected.put("bill_type", "ALL");
        if (account.subMerchantId() != null) {
            expected.put("sub_mchid", account.subMerchantId());
        }
        var query = new HashMap<String, List<String>>();
        expected.forEach((key, value) -> query.put(key, List.of(value)));
        var target = new AtomicReference<String>();
        var signed = new AtomicReference<Map<String, String>>();
        try {
            var applied = http.exchange("wechat", WechatMethod.TRADE_BILL.operation(), new HttpCall(Map.of(), query, Map.of("Accept", "application/json", "Accept-Encoding", "identity"), null),
                    (method, uri, body) -> {
                        if (!expected.equals(WechatStatementCodec.query(uri))) {
                            throw new CommonException(INVALID_ARGUMENT);
                        }
                        var headers = signatures.sign(WechatMethod.TRADE_BILL, original, method, uri, body);
                        target.set(uri.getRawPath() + "?" + uri.getRawQuery());
                        signed.set(headers);
                        return headers;
                    });
            var verified = signatures.verify(WechatMethod.TRADE_BILL, original, applied);
            var payload = reader.read(verified, WechatMethod.TRADE_BILL);
            if (verified.status() != 200) {
                var code = reader.text(payload, "code", 64);
                if (verified.status() == 400 && "STATEMENT_CREATING".equals(code)) {
                    throw new CommonException(STATEMENT_NOT_READY);
                }
                if (verified.status() == 400 && "NO_STATEMENT_EXIST".equals(code)) {
                    throw new CommonException(STATEMENT_NOT_FOUND);
                }
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            }
            var receivedAt = now();
            var grant = WechatStatementCodec.grant(payload, reader);
            byte[] evidence = WechatStatementCodec.evidence(context, request.scope(), target.get(), signed.get(), applied.getHeaders(), applied.getBody(), receivedAt);
            if (!now().isBefore(receivedAt.plusMinutes(5))) {
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            }
            String client = WechatStatementCodec.PRIMARY.equals(grant.uri().getHost()) ? "wechat-bill-download" : "wechat-bill-download-backup";
            var output = new BoundedOutput(request.maxBytes());
            var downloaded = http.download(client, WechatMethod.BILL_DOWNLOAD.operation(),
                    new HttpCall(Map.of(), Map.of("token", List.of(grant.token())), Map.of("Accept", "application/octet-stream", "Accept-Encoding", "identity"), null), output,
                    (method, uri, body) -> {
                        WechatStatementCodec.downloadTarget(grant, uri);
                        return signatures.sign(WechatMethod.BILL_DOWNLOAD, original, method, uri, body);
                    });
            if (downloaded == null || downloaded.getStatusCode().value() != 200) {
                throw new CommonException(DEPENDENCY_UNAVAILABLE);
            }
            var encodings = downloaded.getHeaders().get(HttpHeaders.CONTENT_ENCODING);
            if (encodings != null && (encodings.size() != 1 || !"identity".equalsIgnoreCase(encodings.getFirst()))) {
                throw new CommonException(CHANNEL_RESPONSE_INVALID);
            }
            byte[] bytes = output.bytes();
            if (bytes.length == 0 || downloaded.getBody() == null || downloaded.getBody() != bytes.length
                    || !grant.sha1().equals(WechatStatementCodec.digest("SHA-1", bytes))) {
                throw new CommonException(CHANNEL_RESPONSE_INVALID);
            }
            var proof = new ChannelStatementReceipt(WechatStatementCodec.FORMAT,
                    WechatStatementCodec.digest("SHA-256", target.get().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    WechatStatementCodec.digest("SHA-256", evidence), "SHA1", grant.sha1(), WechatStatementCodec.digest("SHA-256", bytes), receivedAt, now(), evidence);
            return converter.toResult(context, request.scope(), proof, bytes);
        } catch (CommonException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }

    private void guard(ChannelExecutionContext context, ChannelStatementRequest request) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (context == null || request == null) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        if (!context.tenantId().equals(TenantUtil.getTenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        var binding = context.binding();
        if (binding.channel() != Channel.WECHAT || request.scope().channel() != Channel.WECHAT
                || !binding.accountScopeHash().equals(request.scope().accountScopeHash()) || !(binding.identity() instanceof WechatAccountIdentity account)) {
            throw new CommonException(CHANNEL_BINDING_MISMATCH);
        }
        if (!WechatStatementCodec.PROFILE.equals(request.scope().statementProfile())) {
            throw new CommonException(UNSUPPORTED_CAPABILITY);
        }
        if (!request.scope().billDate().isBefore(OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.ofHours(8)).toLocalDate())) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        if (binding.accessMode() == AccessMode.SERVICE_PROVIDER && (account.subMerchantId() == null || account.subMerchantId().isBlank())) {
            throw new CommonException(CHANNEL_BINDING_MISMATCH);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static final class BoundedOutput extends OutputStream {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final int limit;

        BoundedOutput(int limit) {
            this.limit = limit;
        }

        @Override
        public void write(int value) {
            ensure(1);
            bytes.write(value);
        }

        @Override
        public void write(byte[] values, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, values.length);
            ensure(length);
            bytes.write(values, offset, length);
        }

        private void ensure(int additional) {
            if (additional > limit - bytes.size()) {
                throw new CommonException(CHANNEL_RESPONSE_INVALID);
            }
        }

        byte[] bytes() {
            return bytes.toByteArray();
        }
    }
}
