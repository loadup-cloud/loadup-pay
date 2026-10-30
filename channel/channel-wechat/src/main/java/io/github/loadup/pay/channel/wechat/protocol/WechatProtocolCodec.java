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
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.components.kms.KmsSignatureAlgorithm;
import io.github.loadup.components.kms.KmsTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_CREDENTIAL_UNAVAILABLE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_SIGNATURE_INVALID;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_STATE;

/**
 * APIv3 canonical signing and pinned response verification; never creates a financial outcome.
 */
public class WechatProtocolCodec {
    private final JsonMapper json;
    private final KmsTemplate kms;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public WechatProtocolCodec(JsonMapper json, KmsTemplate kms, Clock clock) {
        this.json = json.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
        this.kms = kms;
        this.clock = clock;
    }

    public Map<String, String> sign(WechatMethod operation, WechatCredentials credentials, HttpMethod method, URI uri, byte[] body) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (operation == null || credentials == null || method == null || uri == null || body == null || body.length > 65536
                || !operation.method().equals(method) || !operation.matches(uri.getRawPath())
                || !"https".equals(uri.getScheme()) || !Set.of("api.mch.weixin.qq.com", "api2.mch.weixin.qq.com").contains(uri.getHost())
                || uri.getRawUserInfo() != null || uri.getRawFragment() != null || (uri.getPort() != -1 && uri.getPort() != 443)
                || HttpMethod.GET.equals(method) && body.length != 0) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        if (credentials.merchantId() == null || !credentials.merchantId().matches("[0-9]{1,32}")
                || credentials.merchantSerial() == null || !credentials.merchantSerial().matches("[0-9A-F]{8,64}")
                || credentials.platformKeyId() == null
                || !credentials.platformKeyId().matches("(?:PUB_KEY_ID_[A-Za-z0-9]{1,116}|[0-9A-F]{8,64})")) {
            throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
        }
        byte[] nonceBytes = new byte[16];
        random.nextBytes(nonceBytes);
        String nonce = HexFormat.of().formatHex(nonceBytes), timestamp = Long.toString(clock.instant().getEpochSecond());
        String target = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        var message = new ByteArrayOutputStream(body.length + target.length() + 256);
        message.writeBytes((method.name() + "\n" + target + "\n" + timestamp + "\n" + nonce + "\n").getBytes(StandardCharsets.UTF_8));
        message.writeBytes(body);
        message.write('\n');
        var key = credentials.signingKey();
        if (key == null || key.version() <= 0) {
            throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
        }
        final String signature;
        try {
            var signed = kms.sign(key, KmsSignatureAlgorithm.RSA_SHA256_PKCS1, message.toByteArray());
            if (signed == null || !key.name().equals(signed.keyName()) || key.version() != signed.version()
                    || signed.algorithm() != KmsSignatureAlgorithm.RSA_SHA256_PKCS1 || signed.base64() == null
                    || signed.base64().length() > 1024) {
                throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
            }
            byte[] bytes = Base64.getDecoder().decode(signed.base64());
            if (bytes.length < 256 || bytes.length > 512) {
                throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
            }
            signature = signed.base64();
        } catch (CommonException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
        String authorization = "WECHATPAY2-SHA256-RSA2048 mchid=\"" + credentials.merchantId() + "\",nonce_str=\"" + nonce
                + "\",timestamp=\"" + timestamp + "\",serial_no=\"" + credentials.merchantSerial() + "\",signature=\"" + signature + "\"";
        return Map.of("Authorization", authorization, "Wechatpay-Serial", credentials.platformKeyId());
    }

    public WechatVerifiedResponse verify(WechatMethod operation, WechatCredentials credentials, ResponseEntity<byte[]> response) {
        if (operation == null || credentials == null || response == null) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        byte[] body = response.getBody() == null ? new byte[0] : response.getBody();
        int status = response.getStatusCode().value();
        if (body.length > 1048576 || body.length == 0 && status != 204) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
        verifySignature(credentials, response.getHeaders(), body);
        try {
            String payload = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString();
            if (body.length != 0 && json.readValue(payload, Map.class) == null) {
                throw new CommonException(CHANNEL_RESPONSE_INVALID);
            }
            return new WechatVerifiedResponse(operation, status, payload,
                    DigestUtils.sha256("wechat-response-v1|" + operation.name() + "|" + status + "|" + payload));
        } catch (Exception invalid) {
            throw new CommonException(CHANNEL_RESPONSE_INVALID);
        }
    }

    /**
     * Shared signature verification for original HTTP response or notification bytes.
     */
    public void verifySignature(WechatCredentials credentials, HttpHeaders headers, byte[] body) {
        if (credentials == null || headers == null || body == null || body.length > 1048576) {
            throw new CommonException(CHANNEL_SIGNATURE_INVALID);
        }
        String serial = header(headers, "Wechatpay-Serial", 128);
        String timestamp = header(headers, "Wechatpay-Timestamp", 20);
        String nonce = header(headers, "Wechatpay-Nonce", 256);
        String encoded = header(headers, "Wechatpay-Signature", 1024);
        if (!serial.equals(credentials.platformKeyId()) || !timestamp.matches("[0-9]{1,20}")
                || nonce.chars().anyMatch(c -> c < 33 || c > 126)) {
            throw new CommonException(CHANNEL_SIGNATURE_INVALID);
        }
        try {
            if (Duration.between(Instant.ofEpochSecond(Long.parseLong(timestamp)), clock.instant()).abs().getSeconds() >= 300) {
                throw new CommonException(CHANNEL_SIGNATURE_INVALID);
            }
            if (!(credentials.platformPublicKey() instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() < 2048
                    || rsa.getModulus().bitLength() > 4096) {
                throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
            }
            var verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(rsa);
            verifier.update((timestamp + "\n" + nonce + "\n").getBytes(StandardCharsets.UTF_8));
            verifier.update(body);
            verifier.update((byte) '\n');
            if (!verifier.verify(Base64.getDecoder().decode(encoded))) {
                throw new CommonException(CHANNEL_SIGNATURE_INVALID);
            }
        } catch (CommonException invalid) {
            throw invalid;
        } catch (Exception invalid) {
            throw new CommonException(CHANNEL_SIGNATURE_INVALID);
        }
    }

    private String header(HttpHeaders headers, String name, int maximum) {
        var values = headers.get(name);
        if (values == null || values.size() != 1 || values.getFirst() == null || values.getFirst().isBlank()
                || values.getFirst().length() > maximum || values.getFirst().chars().anyMatch(Character::isISOControl)) {
            throw new CommonException(CHANNEL_SIGNATURE_INVALID);
        }
        return values.getFirst();
    }
}
