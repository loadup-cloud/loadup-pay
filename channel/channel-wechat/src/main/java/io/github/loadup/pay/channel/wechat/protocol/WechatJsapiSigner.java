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
import io.github.loadup.components.kms.KmsSignatureAlgorithm;
import io.github.loadup.components.kms.KmsTemplate;
import io.github.loadup.pay.channel.api.model.ChannelExecutionContext;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.channel.api.model.WechatJsapiChannelAction;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_CREDENTIAL_UNAVAILABLE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_RESPONSE_INVALID;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.DEPENDENCY_UNAVAILABLE;

/**
 * The client invocation signature uses the same frozen merchant signing key as the server request.
 */
public final class WechatJsapiSigner {
    private final WechatCredentialReader credentials;
    private final KmsTemplate kms;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public WechatJsapiSigner(WechatCredentialReader credentials, KmsTemplate kms, Clock clock) {
        this.credentials = credentials;
        this.kms = kms;
        this.clock = clock;
    }

    public WechatJsapiChannelAction sign(ChannelExecutionContext context, ChannelPaymentIdentity identity,
                                         String prepayId, OffsetDateTime expiresAt) {
        var account = WechatBindingGuard.require(context, identity);
        if (prepayId == null || !prepayId.matches("[A-Za-z0-9_-]{1,128}") || expiresAt == null
                || !expiresAt.toInstant().isAfter(clock.instant())) throw new CommonException(CHANNEL_RESPONSE_INVALID);
        String appId = account.subAppId() == null ? account.appId() : account.subAppId();
        var original = credentials.read(context.tenantId(), context.binding());
        byte[] nonceBytes = new byte[16];
        random.nextBytes(nonceBytes);
        String timestamp = Long.toString(clock.instant().getEpochSecond()), nonce = HexFormat.of().formatHex(nonceBytes);
        String packageValue = "prepay_id=" + prepayId;
        var key = original.signingKey();
        try {
            var signature = kms.sign(key, KmsSignatureAlgorithm.RSA_SHA256_PKCS1,
                    (appId + "\n" + timestamp + "\n" + nonce + "\n" + packageValue + "\n").getBytes(StandardCharsets.UTF_8));
            if (signature == null || !key.name().equals(signature.keyName()) || key.version() != signature.version()
                    || signature.algorithm() != KmsSignatureAlgorithm.RSA_SHA256_PKCS1 || signature.base64() == null
                    || signature.base64().length() > 1024) throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
            byte[] bytes = Base64.getDecoder().decode(signature.base64());
            if (bytes.length < 256 || bytes.length > 512) throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
            return new WechatJsapiChannelAction(appId, timestamp, nonce, packageValue, "RSA", signature.base64(), expiresAt);
        } catch (CommonException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
