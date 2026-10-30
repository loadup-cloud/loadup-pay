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
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.components.kms.KmsSignatureAlgorithm;
import io.github.loadup.components.kms.KmsTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JsonToken;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_CREDENTIAL_UNAVAILABLE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_SIGNATURE_INVALID;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.UNSUPPORTED_CAPABILITY;

/**
 * OpenAPI v2 public-key mode, UTF-8 and RSA2. Does not turn protocol success into financial success.
 */
public class AlipayProtocolCodec {
    private final JsonMapper json;
    private final KmsTemplate kms;
    private final Clock clock;

    public AlipayProtocolCodec(JsonMapper json, KmsTemplate kms, Clock clock) {
        this.json = json.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
        this.kms = kms;
        this.clock = clock;
    }

    static String canonical(Map<String, String> parameters) {
        return parameters.entrySet().stream().filter(e -> !"sign".equals(e.getKey()) && e.getValue() != null && !e.getValue().isEmpty())
                .sorted(Map.Entry.comparingByKey()).map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("&"));
    }

    public AlipaySignedRequest sign(AlipayMethod method, AlipayCredentials credentials, String businessJson, Map<String, String> additional) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (method == null || credentials == null || businessJson == null || businessJson.getBytes(StandardCharsets.UTF_8).length > 65536) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        if (method == AlipayMethod.WAP_PAY && credentials.appAuthToken() != null) {
            throw new CommonException(UNSUPPORTED_CAPABILITY);
        }
        try {
            var business = json.readValue(businessJson, Map.class);
            if (business == null || business.isEmpty() || business.containsKey("seller_id")
                    && !credentials.sellerId().equals(business.get("seller_id"))) {
                throw new CommonException(INVALID_ARGUMENT);
            }
            var parameters = new TreeMap<String, String>();
            parameters.put("app_id", credentials.appId());
            parameters.put("method", method.api());
            parameters.put("format", "json");
            parameters.put("charset", "utf-8");
            parameters.put("sign_type", "RSA2");
            parameters.put("timestamp", AlipayProtocolTime.requestTimestamp(clock));
            parameters.put("version", "1.0");
            parameters.put("biz_content", businessJson);
            if (credentials.appAuthToken() != null) {
                parameters.put("app_auth_token", credentials.appAuthToken());
            }
            if (additional != null) {
                for (var entry : additional.entrySet()) {
                    if (!Set.of("notify_url", "return_url").contains(entry.getKey()) || entry.getValue() == null || entry.getValue().isBlank()) {
                        throw new CommonException(INVALID_ARGUMENT);
                    }
                    var uri = java.net.URI.create(entry.getValue());
                    if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
                        throw new CommonException(INVALID_ARGUMENT);
                    }
                    parameters.put(entry.getKey(), entry.getValue());
                }
            }
            var key = credentials.signingKey();
            if (key == null || key.version() <= 0) {
                throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
            }
            var signed = kms.sign(key, KmsSignatureAlgorithm.RSA_SHA256_PKCS1, canonical(parameters).getBytes(StandardCharsets.UTF_8));
            if (signed == null || !key.name().equals(signed.keyName()) || key.version() != signed.version()
                    || signed.algorithm() != KmsSignatureAlgorithm.RSA_SHA256_PKCS1) {
                throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
            }
            parameters.put("sign", signed.base64());
            return new AlipaySignedRequest(method, parameters);
        } catch (CommonException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }

    public AlipayVerifiedResponse verify(AlipayMethod method, AlipayCredentials credentials, byte[] response) {
        if (method == null || credentials == null || response == null || response.length == 0 || response.length > 1048576) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        byte[] payload = null;
        String signature = null;
        var seen = new HashSet<String>();
        try (var parser = json.createParser(response)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new CommonException(CHANNEL_RESPONSE_INVALID);
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.PROPERTY_NAME) {
                    throw new CommonException(CHANNEL_RESPONSE_INVALID);
                }
                String name = parser.currentName();
                if (!seen.add(name)) {
                    throw new CommonException(CHANNEL_RESPONSE_INVALID);
                }
                var token = parser.nextToken();
                if (method.responseNode().equals(name)) {
                    if (token != JsonToken.START_OBJECT) {
                        throw new CommonException(CHANNEL_RESPONSE_INVALID);
                    }
                    long start = parser.currentTokenLocation().getByteOffset();
                    parser.skipChildren();
                    long end = parser.currentLocation().getByteOffset();
                    if (start < 0 || end <= start || end > response.length) {
                        throw new CommonException(CHANNEL_RESPONSE_INVALID);
                    }
                    payload = Arrays.copyOfRange(response, Math.toIntExact(start), Math.toIntExact(end));
                } else if ("sign".equals(name)) {
                    if (token != JsonToken.VALUE_STRING) {
                        throw new CommonException(CHANNEL_RESPONSE_INVALID);
                    }
                    signature = parser.getString();
                } else if ("alipay_cert_sn".equals(name)) {
                    if (token != JsonToken.VALUE_STRING || parser.getString().length() > 128) {
                        throw new CommonException(CHANNEL_RESPONSE_INVALID);
                    }
                } else {
                    throw new CommonException(CHANNEL_RESPONSE_INVALID);
                }
            }
            if (parser.nextToken() != null || payload == null || signature == null || signature.isEmpty() || signature.length() > 1024) {
                throw new CommonException(CHANNEL_RESPONSE_INVALID);
            }
        } catch (CommonException invalid) {
            throw invalid;
        } catch (RuntimeException invalid) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        try {
            if (!(credentials.platformPublicKey() instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() < 2048) {
                throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
            }
            var verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(rsa);
            verifier.update(payload);
            if (!verifier.verify(Base64.getDecoder().decode(signature))) {
                throw new CommonException(CHANNEL_SIGNATURE_INVALID);
            }
        } catch (CommonException invalid) {
            throw invalid;
        } catch (Exception invalid) {
            throw new CommonException(CHANNEL_SIGNATURE_INVALID);
        }
        String raw = new String(payload, StandardCharsets.UTF_8);
        return new AlipayVerifiedResponse(method, raw, DigestUtils.sha256(raw));
    }
}
