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
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import io.github.loadup.pay.channel.api.model.ChannelStatementScope;
import org.springframework.http.HttpHeaders;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID;

/**
 * Strict source-protocol handling. Download URLs come exclusively from a verified APIv3 grant.
 */
public final class WechatStatementCodec {
    public static final String PROFILE = "wechat-trade-all-20260116";
    public static final String FORMAT = "wechat-tradebill-grant-v1";
    public static final String PRIMARY = "api.mch.weixin.qq.com", BACKUP = "api2.mch.weixin.qq.com";

    private WechatStatementCodec() {
    }

    public static WechatStatementGrant grant(Map<?, ?> response, WechatResponseReader reader) {
        if (!"SHA1".equals(reader.text(response, "hash_type", 16))) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        var hash = reader.text(response, "hash_value", 1024);
        if (!hash.matches("[0-9a-fA-F]{40}")) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        final URI uri;
        try {
            uri = URI.create(reader.text(response, "download_url", 2048));
        } catch (IllegalArgumentException invalid) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || !Set.of(PRIMARY, BACKUP).contains(uri.getHost()) || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null || uri.getPort() != -1 && uri.getPort() != 443 || !"/v3/billdownload/file".equals(uri.getRawPath())) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        var query = query(uri);
        if (!query.keySet().equals(Set.of("token"))) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        var token = query.get("token");
        if (token.isEmpty() || token.length() > 2000 || token.chars().anyMatch(c -> c < 33 || c > 126)) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        return new WechatStatementGrant(uri, token, hash.toLowerCase(Locale.ROOT));
    }

    public static Map<String, String> query(URI uri) {
        if (uri == null || uri.getRawQuery() == null || uri.getRawQuery().isEmpty()) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        var result = new HashMap<String, String>();
        try {
            for (var pair : uri.getRawQuery().split("&", -1)) {
                int separator = pair.indexOf('=');
                if (separator < 1) {
                    throw new CommonException(CHANNEL_RESPONSE_INVALID);
                }
                String key = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
                if (result.put(key, value) != null || key.chars().anyMatch(Character::isISOControl) || value.chars().anyMatch(Character::isISOControl)) {
                    throw new CommonException(CHANNEL_RESPONSE_INVALID);
                }
            }
            return Map.copyOf(result);
        } catch (IllegalArgumentException invalid) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
    }

    public static void downloadTarget(WechatStatementGrant grant, URI actual) {
        if (grant == null || actual == null || !grant.uri().getHost().equals(actual.getHost()) || !"https".equals(actual.getScheme())
                || actual.getRawUserInfo() != null || actual.getRawFragment() != null || actual.getPort() != -1 && actual.getPort() != 443
                || !"/v3/billdownload/file".equals(actual.getRawPath()) || !Map.of("token", grant.token()).equals(query(actual))) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
    }

    public static byte[] evidence(ChannelExecutionContext context, ChannelStatementScope scope, String requestTarget, Map<String, String> signedRequest,
                                  HttpHeaders responseHeaders, byte[] responseBody, OffsetDateTime receivedAt) {
        if (requestTarget == null || signedRequest == null || responseHeaders == null || responseBody == null || responseBody.length > 8192) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        String authorization = signedRequest.get("Authorization");
        if (authorization == null || authorization.isBlank() || authorization.length() > 4096
                || authorization.chars().anyMatch(Character::isISOControl) || requestTarget.length() > 4096) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        var out = new ByteArrayOutputStream();
        for (String field : List.of(FORMAT, context.tenantId(), context.binding().id(), scope.channel().name(), scope.accountScopeHash(), scope.billDate().toString(), scope.statementProfile(),
                context.binding().credentialReference(), context.binding().credentialVersion(), requestTarget, authorization, single(responseHeaders, "Wechatpay-Serial", 128),
                single(responseHeaders, "Wechatpay-Timestamp", 20), single(responseHeaders, "Wechatpay-Nonce", 256), single(responseHeaders, "Wechatpay-Signature", 1024), receivedAt.toInstant().toString())) {
            field(out, field.getBytes(StandardCharsets.UTF_8));
        }
        field(out, responseBody);
        if (out.size() > 32768) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        return out.toByteArray();
    }

    private static String single(HttpHeaders headers, String name, int max) {
        var values = headers.get(name);
        if (values == null || values.size() != 1 || values.getFirst() == null || values.getFirst().isBlank()
                || values.getFirst().length() > max || values.getFirst().chars().anyMatch(Character::isISOControl)) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        return values.getFirst();
    }

    private static void field(ByteArrayOutputStream out, byte[] bytes) {
        out.writeBytes(ByteBuffer.allocate(4).putInt(bytes.length).array());
        out.writeBytes(bytes);
    }

    public static String digest(String algorithm, byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new AssertionError(unavailable);
        }
    }
}
