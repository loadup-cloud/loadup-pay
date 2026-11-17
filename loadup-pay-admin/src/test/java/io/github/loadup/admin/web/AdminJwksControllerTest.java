/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.admin.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import org.junit.jupiter.api.Test;

class AdminJwksControllerTest {
    @Test
    void publishesOnlyPublicVerificationMaterialEvenIfTheSourceContainsAPrivateKey() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var key = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey(pair.getPrivate())
                .keyID("operator-key")
                .build();
        var endpoint = new AdminJwksController(new ImmutableJWKSet<>(new JWKSet(key)));

        var result = JWKSet.parse(endpoint.jwks()).getKeys();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getKeyID()).isEqualTo("operator-key");
        assertThat(result.getFirst().isPrivate()).isFalse();
        assertThat(result.getFirst().toJSONObject()).doesNotContainKeys("d", "p", "q", "dp", "dq", "qi");
    }
}
