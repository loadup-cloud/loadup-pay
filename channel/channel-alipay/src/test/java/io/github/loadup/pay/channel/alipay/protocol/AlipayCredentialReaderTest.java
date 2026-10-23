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

import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.kms.KmsCiphertext;
import io.github.loadup.components.kms.KmsTemplate;
import io.github.loadup.pay.channel.api.model.AlipayAccountIdentity;
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.channel.alipay.config.AlipayProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AlipayCredentialReaderTest {
    @TempDir Path directory;

    @Test
    void readsMountedDirectAndProviderCredentialsWithStrictFrozenIdentity() throws Exception {
        var keyPair = KeyPairGenerator.getInstance("RSA");
        keyPair.initialize(2048);
        var publicKey = Base64.getEncoder().encodeToString(keyPair.generateKeyPair().getPublic().getEncoded());
        var credentialPath = directory.resolve("credential-v3.json");
        Files.writeString(credentialPath, credential("tenant", "credential-ref", "3", "app", "seller", publicKey));
        var authorizationPath = directory.resolve("authorization-v5.json");
        Files.writeString(authorizationPath, "{\"tenantId\":\"tenant\",\"reference\":\"auth-ref\",\"version\":\"5\","
                + "\"appId\":\"app\",\"sellerId\":\"seller\",\"token\":{\"keyName\":\"auth-key\","
                + "\"version\":2,\"value\":\"vault:v2:opaque\"}}");
        var props = new AlipayProperties(true, Map.of("credential-ref", Map.of("3", credentialPath)),
                Map.of("auth-ref", Map.of("5", authorizationPath)));
        var kms = mock(KmsTemplate.class);
        byte[] plaintext = "provider-token".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(kms.decrypt(any(), any())).thenReturn(plaintext);
        var reader = new AlipayCredentialReader(props, JsonMapper.builder().build(), kms);

        var direct = TenantUtil.callWithTenant("tenant", () -> reader.read("tenant", binding(AccessMode.DIRECT,
                new AlipayAccountIdentity("app", "seller", null, null, null), "credential-ref", "3")));
        assertEquals("app", direct.appId());
        assertEquals("seller", direct.sellerId());
        assertNull(direct.appAuthToken());

        var delegated = TenantUtil.callWithTenant("tenant", () -> reader.read("tenant", binding(AccessMode.SERVICE_PROVIDER,
                new AlipayAccountIdentity("app", "seller", null, "auth-ref", "5"), "credential-ref", "3")));
        assertEquals("provider-token", delegated.appAuthToken());
        assertArrayEquals(new byte[plaintext.length], plaintext);
        verify(kms).decrypt(argThat(key -> key.name().equals("auth-key") && key.version() == 2),
                argThat(cipher -> cipher.keyName().equals("auth-key") && cipher.version() == 2));
        assertFalse(delegated.toString().contains("provider-token"));
        var authorizationDocument = new AlipayAuthorizationDocument("tenant", "auth-ref", "5", "app", "seller",
                new KmsCiphertext("auth-key", 2, "vault:v2:opaque"));
        assertFalse(authorizationDocument.toString().contains("opaque"));
        assertFalse(props.toString().contains(credentialPath.toString()));
    }

    @Test
    void rejectsTenantReferenceVersionAppSellerAndUnmappedOrMalformedResources() throws Exception {
        var keyPair = KeyPairGenerator.getInstance("RSA");
        keyPair.initialize(2048);
        var publicKey = Base64.getEncoder().encodeToString(keyPair.generateKeyPair().getPublic().getEncoded());
        var mounted = directory.resolve("mounted.json");
        Files.writeString(mounted, credential("tenant", "credential-ref", "3", "app", "seller", publicKey));
        var reader = reader(Map.of("credential-ref", Map.of("3", mounted)), Map.of(), mock(KmsTemplate.class));

        assertThrows(RuntimeException.class, () -> TenantUtil.callWithTenant("other", () -> reader.read("tenant",
                binding(AccessMode.DIRECT, new AlipayAccountIdentity("app", "seller", null, null, null), "credential-ref", "3"))));
        TenantUtil.runWithTenant("tenant", () -> {
            assertThrows(RuntimeException.class, () -> reader.read("tenant", binding(AccessMode.DIRECT,
                    new AlipayAccountIdentity("app", "seller", null, null, null), "missing", "3")));
            assertThrows(RuntimeException.class, () -> reader.read("tenant", binding(AccessMode.DIRECT,
                    new AlipayAccountIdentity("app", "seller", null, null, null), "credential-ref", "4")));
            assertThrows(RuntimeException.class, () -> reader.read("tenant", binding(AccessMode.DIRECT,
                    new AlipayAccountIdentity("other-app", "seller", null, null, null), "credential-ref", "3")));
            assertThrows(RuntimeException.class, () -> reader.read("tenant", binding(AccessMode.DIRECT,
                    new AlipayAccountIdentity("app", "other-seller", null, null, null), "credential-ref", "3")));
            assertThrows(RuntimeException.class, () -> reader.read("tenant", binding(AccessMode.DIRECT,
                    new AlipayAccountIdentity("app", "seller", null, null, null), "../../mounted", "3")));
        });

        var malformed = directory.resolve("malformed.json");
        Files.writeString(malformed, credential("tenant", "credential-ref", "3", "app", "seller", publicKey) + " {}");
        var malformedReader = reader(Map.of("credential-ref", Map.of("3", malformed)), Map.of(), mock(KmsTemplate.class));
        TenantUtil.runWithTenant("tenant", () -> assertThrows(RuntimeException.class, () -> malformedReader.read("tenant",
                binding(AccessMode.DIRECT, new AlipayAccountIdentity("app", "seller", null, null, null), "credential-ref", "3"))));

        var unknown = directory.resolve("unknown.json");
        Files.writeString(unknown, credential("tenant", "credential-ref", "3", "app", "seller", publicKey)
                .replace("\"sellerId\":\"seller\"", "\"sellerId\":\"seller\",\"unexpected\":true"));
        var unknownReader = reader(Map.of("credential-ref", Map.of("3", unknown)), Map.of(), mock(KmsTemplate.class));
        TenantUtil.runWithTenant("tenant", () -> assertThrows(RuntimeException.class, () -> unknownReader.read("tenant",
                binding(AccessMode.DIRECT, new AlipayAccountIdentity("app", "seller", null, null, null), "credential-ref", "3"))));

        var wrongTenant = directory.resolve("wrong-tenant.json");
        Files.writeString(wrongTenant, credential("another-tenant", "credential-ref", "3", "app", "seller", publicKey));
        var wrongTenantReader = reader(Map.of("credential-ref", Map.of("3", wrongTenant)), Map.of(), mock(KmsTemplate.class));
        TenantUtil.runWithTenant("tenant", () -> assertThrows(RuntimeException.class, () -> wrongTenantReader.read("tenant",
                binding(AccessMode.DIRECT, new AlipayAccountIdentity("app", "seller", null, null, null), "credential-ref", "3"))));

        for (var mismatch : Map.of("reference", credential("tenant", "other-ref", "3", "app", "seller", publicKey),
                "version", credential("tenant", "credential-ref", "4", "app", "seller", publicKey),
                "seller", credential("tenant", "credential-ref", "3", "app", "other-seller", publicKey)).entrySet()) {
            var path = directory.resolve("wrong-" + mismatch.getKey() + ".json");
            Files.writeString(path, mismatch.getValue());
            var mismatchedReader = reader(Map.of("credential-ref", Map.of("3", path)), Map.of(), mock(KmsTemplate.class));
            TenantUtil.runWithTenant("tenant", () -> assertThrows(RuntimeException.class, () -> mismatchedReader.read("tenant",
                    binding(AccessMode.DIRECT, new AlipayAccountIdentity("app", "seller", null, null, null), "credential-ref", "3"))));
        }
    }

    @Test
    void verificationKeyReadsFrozenDirectAndProviderDescriptorsWithoutKmsCalls() throws Exception {
        var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        var pair = keyPairGenerator.generateKeyPair();
        var encoded = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        var path = directory.resolve("verification-credential.json");
        Files.writeString(path, credential("tenant", "credential-ref", "3", "app", "seller", encoded));
        var kms = mock(KmsTemplate.class);
        var reader = reader(Map.of("credential-ref", Map.of("3", path)), Map.of(), kms);
        var direct = binding(AccessMode.DIRECT,
                new AlipayAccountIdentity("app", "seller", null, null, null), "credential-ref", "3");
        var provider = binding(AccessMode.SERVICE_PROVIDER,
                new AlipayAccountIdentity("app", "seller", "operator-app", "auth-ref", "5"), "credential-ref", "3");

        var directKey = TenantUtil.callWithTenant("tenant", () -> reader.verificationKey("tenant", direct));
        var providerKey = TenantUtil.callWithTenant("tenant", () -> reader.verificationKey("tenant", provider));

        assertArrayEquals(pair.getPublic().getEncoded(), directKey.getEncoded());
        assertArrayEquals(pair.getPublic().getEncoded(), providerKey.getEncoded());
        verifyNoInteractions(kms);
    }

    @Test
    void verificationKeyRejectsWrongTenantAppSellerAndInvalidRsaKey() throws Exception {
        var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        var validPublicKey = Base64.getEncoder().encodeToString(keyPairGenerator.generateKeyPair().getPublic().getEncoded());
        var path = directory.resolve("frozen-credential.json");
        Files.writeString(path, credential("tenant", "credential-ref", "3", "app", "seller", validPublicKey));
        var kms = mock(KmsTemplate.class);
        var reader = reader(Map.of("credential-ref", Map.of("3", path)), Map.of(), kms);
        var direct = binding(AccessMode.DIRECT,
                new AlipayAccountIdentity("app", "seller", null, null, null), "credential-ref", "3");

        assertThrows(RuntimeException.class,
                () -> TenantUtil.callWithTenant("other", () -> reader.verificationKey("tenant", direct)));
        TenantUtil.runWithTenant("tenant", () -> {
            assertThrows(RuntimeException.class, () -> reader.verificationKey("tenant", binding(AccessMode.DIRECT,
                    new AlipayAccountIdentity("wrong-app", "seller", null, null, null), "credential-ref", "3")));
            assertThrows(RuntimeException.class, () -> reader.verificationKey("tenant", binding(AccessMode.DIRECT,
                    new AlipayAccountIdentity("app", "wrong-seller", null, null, null), "credential-ref", "3")));
        });

        var wrongTenantPath = directory.resolve("wrong-tenant-verification.json");
        Files.writeString(wrongTenantPath, credential("other-tenant", "credential-ref", "3", "app", "seller", validPublicKey));
        var wrongTenantReader = reader(Map.of("credential-ref", Map.of("3", wrongTenantPath)), Map.of(), kms);
        TenantUtil.runWithTenant("tenant", () -> assertThrows(RuntimeException.class,
                () -> wrongTenantReader.verificationKey("tenant", direct)));

        var weakGenerator = KeyPairGenerator.getInstance("RSA");
        weakGenerator.initialize(1024);
        var weakPath = directory.resolve("weak-verification-key.json");
        Files.writeString(weakPath, credential("tenant", "credential-ref", "3", "app", "seller",
                Base64.getEncoder().encodeToString(weakGenerator.generateKeyPair().getPublic().getEncoded())));
        var weakReader = reader(Map.of("credential-ref", Map.of("3", weakPath)), Map.of(), kms);
        TenantUtil.runWithTenant("tenant", () -> assertThrows(RuntimeException.class,
                () -> weakReader.verificationKey("tenant", direct)));
        verifyNoInteractions(kms);
    }

    private AlipayCredentialReader reader(Map<String, Map<String, Path>> credentials,
            Map<String, Map<String, Path>> authorizations, KmsTemplate kms) {
        return new AlipayCredentialReader(new AlipayProperties(true, credentials, authorizations), JsonMapper.builder().build(), kms);
    }

    private ChannelBindingRevision binding(AccessMode mode, AlipayAccountIdentity identity, String reference, String version) {
        return new ChannelBindingRevision("binding-1", "merchant-1", Channel.ALIPAY, mode, "a".repeat(64),
                reference, version, identity, Set.of(PaymentMethod.PAYMENT_CODE));
    }

    private static String credential(String tenant, String reference, String version, String app, String seller, String key) {
        return "{\"tenantId\":\"" + tenant + "\",\"reference\":\"" + reference + "\",\"version\":\"" + version
                + "\",\"appId\":\"" + app + "\",\"sellerId\":\"" + seller
                + "\",\"signingKeyName\":\"signing-key\",\"signingKeyVersion\":4,\"platformPublicKey\":\"" + key + "\"}";
    }
}
