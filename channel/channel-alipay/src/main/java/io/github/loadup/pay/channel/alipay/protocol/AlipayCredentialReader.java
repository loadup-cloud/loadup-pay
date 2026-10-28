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
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.kms.KmsKeyRef;
import io.github.loadup.components.kms.KmsTemplate;
import io.github.loadup.pay.channel.alipay.config.AlipayProperties;
import io.github.loadup.pay.channel.api.model.AlipayAccountIdentity;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;

import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.CHANNEL_CREDENTIAL_UNAVAILABLE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.UNSUPPORTED_CAPABILITY;

public class AlipayCredentialReader {
    private final AlipayProperties properties;
    private final JsonMapper json;
    private final KmsTemplate kms;

    public AlipayCredentialReader(AlipayProperties properties, JsonMapper json, KmsTemplate kms) {
        this.properties = properties;
        this.kms = kms;
        this.json = json.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    }

    public AlipayCredentials read(String tenant, ChannelBindingRevision binding) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!properties.enabled() || binding == null || binding.channel() != Channel.ALIPAY
                || !(binding.identity() instanceof AlipayAccountIdentity identity))
            throw new CommonException(UNSUPPORTED_CAPABILITY);
        try {
            var document = read(properties.credentials(), binding.credentialReference(), binding.credentialVersion(), AlipayCredentialDocument.class);
            if (!tenant.equals(document.tenantId()) || !binding.credentialReference().equals(document.reference())
                    || !binding.credentialVersion().equals(document.version()) || !identity.appId().equals(document.appId())
                    || document.signingKeyVersion() <= 0) throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
            var signer = new KmsKeyRef(document.signingKeyName(), document.signingKeyVersion());
            var publicKey = publicKey(document.platformPublicKey());
            String authorization = null;
            if (binding.accessMode() == AccessMode.DIRECT) {
                if (!identity.sellerId().equals(document.sellerId()) || identity.authorizationReference() != null)
                    throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
            } else {
                if (document.sellerId() != null && !identity.sellerId().equals(document.sellerId()))
                    throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
                var delegated = read(properties.authorizations(), identity.authorizationReference(), identity.authorizationVersion(), AlipayAuthorizationDocument.class);
                if (!tenant.equals(delegated.tenantId()) || !identity.authorizationReference().equals(delegated.reference())
                        || !identity.authorizationVersion().equals(delegated.version()) || !identity.appId().equals(delegated.appId())
                        || !identity.sellerId().equals(delegated.sellerId()) || delegated.token() == null)
                    throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
                byte[] plaintext = kms.decrypt(new KmsKeyRef(delegated.token().keyName(), delegated.token().version()), delegated.token());
                if (plaintext == null) throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
                try {
                    if (plaintext.length == 0 || plaintext.length > 4096)
                        throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
                    authorization = new String(plaintext, StandardCharsets.UTF_8);
                    if (!authorization.chars().allMatch(c -> c >= 33 && c <= 126))
                        throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
                } finally {
                    Arrays.fill(plaintext, (byte) 0);
                }
            }
            return new AlipayCredentials(tenant, identity.appId(), identity.sellerId(), signer, publicKey, authorization);
        } catch (RuntimeException failure) {
            throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
        }
    }

    /**
     * Verification needs only the frozen descriptor's platform key, never a signing or merchant authorization decrypt.
     */
    public PublicKey verificationKey(String tenant, ChannelBindingRevision binding) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        if (!properties.enabled() || binding == null || binding.channel() != Channel.ALIPAY
                || !(binding.identity() instanceof AlipayAccountIdentity identity))
            throw new CommonException(UNSUPPORTED_CAPABILITY);
        try {
            var document = read(properties.credentials(), binding.credentialReference(), binding.credentialVersion(), AlipayCredentialDocument.class);
            if (!tenant.equals(document.tenantId()) || !binding.credentialReference().equals(document.reference())
                    || !binding.credentialVersion().equals(document.version()) || !identity.appId().equals(document.appId())
                    || document.sellerId() != null && !identity.sellerId().equals(document.sellerId())
                    || binding.accessMode() == AccessMode.DIRECT && !identity.sellerId().equals(document.sellerId()))
                throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
            return publicKey(document.platformPublicKey());
        } catch (RuntimeException invalid) {
            throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
        }
    }

    private <T> T read(Map<String, Map<String, Path>> catalog, String reference, String version, Class<T> type) {
        if (reference == null || version == null) throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
        var path = catalog.getOrDefault(reference, Map.of()).get(version);
        if (path == null || !path.isAbsolute()) throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
        // Paths come exclusively from trusted configuration, never from concatenated resource references.
        try (var input = Files.newInputStream(path)) {
            byte[] payload = input.readNBytes(65537);
            if (payload.length == 0 || payload.length > 65536)
                throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
            return json.readValue(payload, type);
        } catch (Exception invalid) {
            throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
        }
    }

    private PublicKey publicKey(String value) {
        try {
            if (value == null || value.length() > 8192) throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
            String encoded = value.strip();
            if (encoded.startsWith("-----BEGIN PUBLIC KEY-----") && encoded.endsWith("-----END PUBLIC KEY-----"))
                encoded = encoded.substring("-----BEGIN PUBLIC KEY-----".length(),
                        encoded.length() - "-----END PUBLIC KEY-----".length());
            var key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded.replaceAll("\\s", ""))));
            if (!(key instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() < 2048 || rsa.getModulus().bitLength() > 4096)
                throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
            return key;
        } catch (Exception invalid) {
            throw new CommonException(CHANNEL_CREDENTIAL_UNAVAILABLE);
        }
    }
}
