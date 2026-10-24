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
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.kms.KmsKeyRef;
import io.github.loadup.components.kms.KmsTemplate;
import java.util.Arrays;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.channel.api.model.WechatAccountIdentity;
import io.github.loadup.pay.channel.wechat.config.WechatProperties;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.*;

/** Only trusted catalog paths may supply a frozen descriptor; no path is constructed from request input. */
public class WechatCredentialReader {
    private final WechatProperties properties;
    private final JsonMapper json;
    private final KmsTemplate kms;
    public WechatCredentialReader(WechatProperties properties, JsonMapper json, KmsTemplate kms) {
        this.properties = properties; this.kms = kms;
        this.json = json.rebuild().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    }
    public WechatCredentials read(String tenant, ChannelBindingRevision binding) {
        var document = document(tenant, binding);
        try {
            return new WechatCredentials(tenant, document.appId(), document.merchantId(), document.merchantSerial(),
                    new KmsKeyRef(document.signingKeyName(), document.signingKeyVersion()), document.platformKeyId(), publicKey(document.platformPublicKey()));
        } catch (Exception invalid) { throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE); }
    }

    /** Caller owns and must zero the returned APIv3 key. No fallback to a current key version. */
    public byte[] apiV3Key(String tenant, ChannelBindingRevision binding) {
        var encrypted = document(tenant, binding).apiV3Key();
        if (encrypted == null) throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
        final byte[] value;
        try { value = kms.decrypt(new KmsKeyRef(encrypted.keyName(), encrypted.version()), encrypted); }
        catch (RuntimeException invalid) { throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE); }
        if (value == null || value.length != 32) {
            if (value != null) Arrays.fill(value, (byte) 0);
            throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
        }
        return value;
    }

    private WechatCredentialDocument document(String tenant, ChannelBindingRevision binding) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!properties.enabled() || binding == null || binding.channel() != Channel.WECHAT
                || !(binding.identity() instanceof WechatAccountIdentity account)) throw new CommonException(UNSUPPORTED_CAPABILITY);
        var path = properties.credentials().getOrDefault(binding.credentialReference(), Map.of()).get(binding.credentialVersion());
        if (path == null || !path.isAbsolute()) throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(65537);
            if (bytes.length == 0 || bytes.length > 65536) throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
            var document = json.readValue(bytes, WechatCredentialDocument.class);
            if (document == null || !tenant.equals(document.tenantId()) || !binding.credentialReference().equals(document.reference())
                    || !binding.credentialVersion().equals(document.version()) || !account.appId().equals(document.appId())
                    || !account.merchantId().equals(document.merchantId()) || document.merchantSerial() == null
                    || !document.merchantSerial().matches("[0-9A-F]{8,64}") || document.signingKeyVersion() <= 0
                    || document.platformKeyId() == null || !document.platformKeyId().matches("(?:PUB_KEY_ID_[A-Za-z0-9]{1,116}|[0-9A-F]{8,64})"))
                throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
            return document;
        } catch (Exception invalid) { throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE); }
    }
    private PublicKey publicKey(String value) throws Exception {
        if (value == null || value.length() > 8192) throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
        var encoded = value.strip();
        if (encoded.startsWith("-----BEGIN PUBLIC KEY-----") && encoded.endsWith("-----END PUBLIC KEY-----"))
            encoded = encoded.substring("-----BEGIN PUBLIC KEY-----".length(), encoded.length() - "-----END PUBLIC KEY-----".length());
        var key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded.replaceAll("\\s", ""))));
        if (!(key instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() < 2048 || rsa.getModulus().bitLength() > 4096)
            throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
        return key;
    }
}
