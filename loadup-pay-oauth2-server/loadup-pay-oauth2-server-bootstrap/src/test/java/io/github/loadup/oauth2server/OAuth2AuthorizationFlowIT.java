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
package io.github.loadup.oauth2server;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.jwk.JWKSet;
import io.github.loadup.components.kms.KmsKeyMetadata;
import io.github.loadup.components.kms.KmsSignature;
import io.github.loadup.components.kms.KmsSignatureAlgorithm;
import io.github.loadup.components.kms.KmsTemplate;
import io.github.loadup.components.resourceserver.LoadUpJwtAuthenticationConverter;
import io.github.loadup.components.resourceserver.ResourceServerAutoConfiguration;
import io.github.loadup.components.resourceserver.ResourceServerProperties;
import io.github.loadup.components.testcontainers.annotation.ContainerType;
import io.github.loadup.components.testcontainers.annotation.EnableTestContainers;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        properties = {
            "AUTHSERVER_JDBC_URL=jdbc:postgresql://127.0.0.1:1/unused",
            "AUTHSERVER_JDBC_USERNAME=sa",
            "AUTHSERVER_JDBC_PASSWORD=unused",
            "loadup.kms.enabled=false",
            "JWT_ISSUER_URI=https://auth.test",
            "AUTHSERVER_SERVICE_AUTHORIZATIONS={\"clients\":[{\"clientId\":\"acquire.tenant.payment\",\"tenantId\":\"tenant\",\"serviceId\":\"acquire\",\"audience\":\"payment\",\"permissions\":[\"pay:payment:read\"],\"secretEnv\":\"TEST_CLIENT_SECRET\",\"enabled\":true}]}",
            "TEST_CLIENT_SECRET=synthetic-client-secret-32-characters"
        })
@EnableTestContainers(value = ContainerType.POSTGRESQL, reuse = false)
@AutoConfigureMockMvc
@Import(OAuth2AuthorizationFlowIT.Fixture.class)
class OAuth2AuthorizationFlowIT {
    static final String SECRET = "synthetic-client-secret-32-characters";
    private final MockMvc mvc;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;
    private final KeyPair pair;

    @Autowired
    OAuth2AuthorizationFlowIT(MockMvc mvc, JdbcTemplate jdbc, PlatformTransactionManager transactions, KeyPair pair) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.pair = pair;
    }

    OAuthClientCatalog catalog(boolean enabled) {
        return new OAuthClientCatalog(List.of(new OAuthClientCatalog.Client(
                "acquire.tenant.payment",
                "tenant",
                "SERVICE",
                "acquire",
                null,
                "payment",
                Set.of("pay:payment:read"),
                "TEST_CLIENT_SECRET",
                enabled)));
    }

    void synchronize(boolean enabled) {
        OAuthClientRegistry.synchronize(
                jdbc,
                new TransactionTemplate(transactions),
                catalog(enabled),
                new MockEnvironment().withProperty("TEST_CLIENT_SECRET", SECRET));
    }

    @BeforeEach
    void resetGrant() {
        synchronize(true);
    }

    String issue(String scope) throws Exception {
        var request = MockMvcRequestBuilders.post("/oauth2/token")
                .with(httpBasic("acquire.tenant.payment", SECRET))
                .param("grant_type", "client_credentials");
        if (scope != null) {
            request.param("scope", scope);
        }
        String response = mvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonMapper.builder()
                .build()
                .readTree(response)
                .get("access_token")
                .asString();
    }

    JwtDecoder decoder(String audience) {
        var properties = new ResourceServerProperties();
        properties.setIssuerUri("https://auth.test");
        properties.setAudience(audience);
        properties.setPublicKeyLocation("memory:public-key");
        byte[] pem = ("-----BEGIN PUBLIC KEY-----\n"
                        + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())
                        + "\n-----END PUBLIC KEY-----\n")
                .getBytes(StandardCharsets.US_ASCII);
        return new ResourceServerAutoConfiguration().resourceServerJwtDecoder(properties, new DefaultResourceLoader() {
            @Override
            public Resource getResource(String location) {
                return new ByteArrayResource(pem);
            }
        });
    }

    @Test
    void clientCredentialsIssueKmsSignedTenantBoundTokensThatResourceServersVerifyLocally() throws Exception {
        var jwt = decoder("payment").decode(issue("pay:payment:read"));
        Assertions.assertThat(jwt.getSubject()).isEqualTo("acquire");
        Assertions.assertThat(jwt.getAudience()).containsExactly("payment");
        Assertions.assertThat(jwt.getClaimAsString("tenant_id")).isEqualTo("tenant");
        Assertions.assertThat(jwt.getClaimAsString("principal_type")).isEqualTo("SERVICE");
        Assertions.assertThat(jwt.getClaimAsString("service_id")).isEqualTo("acquire");
        Assertions.assertThat(jwt.getClaimAsString("token_use")).isEqualTo("access");
        Assertions.assertThat(jwt.getHeaders().get("kid")).isEqualTo("auth-jwt-v1");
        Assertions.assertThat(
                        new LoadUpJwtAuthenticationConverter().convert(jwt).getAuthorities())
                .extracting("authority")
                .contains("pay:payment:read");
        Assertions.assertThat(new JdbcRegisteredClientRepository(jdbc).findByClientId("acquire.tenant.payment"))
                .isNotNull();
        Assertions.assertThat(jdbc.queryForObject("select count(*) from oauth2_authorization", Long.class))
                .isPositive();
        var jwks = mvc.perform(MockMvcRequestBuilders.get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Assertions.assertThat(JWKSet.parse(jwks).getKeys())
                .allSatisfy(key -> Assertions.assertThat(key.isPrivate()).isFalse());
    }

    @Test
    void omittedScopesDoNotGrantTheRegisteredBusinessPermissions() throws Exception {
        var jwt = decoder("payment").decode(issue(null));
        Assertions.assertThat(jwt.getClaimAsStringList("permissions")).isEmpty();
        Assertions.assertThat(
                        new LoadUpJwtAuthenticationConverter().convert(jwt).getAuthorities())
                .isEmpty();
    }

    @Test
    void wrongCredentialsExtraPermissionsWrongAudienceAndTamperingAreRejected() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/oauth2/token")
                        .with(httpBasic("acquire.tenant.payment", "wrong"))
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.post("/oauth2/token")
                        .with(httpBasic("acquire.tenant.payment", SECRET))
                        .param("grant_type", "client_credentials")
                        .param("scope", "pay:payment:accept"))
                .andExpect(status().isBadRequest());
        String token = issue("pay:payment:read");
        Assertions.assertThatThrownBy(() -> decoder("acquire").decode(token)).isInstanceOf(JwtException.class);
        String[] parts = token.split("[.]");
        String tampered = parts[0] + "."
                + Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString("{\"sub\":\"attacker\"}".getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];
        Assertions.assertThatThrownBy(() -> decoder("payment").decode(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void requestTenantAndAudienceCannotOverrideTheServerGrant() throws Exception {
        var response = mvc.perform(MockMvcRequestBuilders.post("/oauth2/token")
                        .with(httpBasic("acquire.tenant.payment", SECRET))
                        .param("grant_type", "client_credentials")
                        .param("tenant_id", "another")
                        .param("audience", "customer"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        var token = JsonMapper.builder()
                .build()
                .readTree(response)
                .get("access_token")
                .asString();
        Assertions.assertThat(decoder("payment").decode(token).getClaimAsString("tenant_id"))
                .isEqualTo("tenant");
    }

    @Test
    void disablingGrantStopsNewIssuanceAndUnknownEndpointsRemainClosed() throws Exception {
        synchronize(false);
        mvc.perform(MockMvcRequestBuilders.post("/oauth2/token")
                        .with(httpBasic("acquire.tenant.payment", SECRET))
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.get("/admin/clients")).andExpect(status().isForbidden());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fixture {
        @Bean
        KeyPair keyPair() throws Exception {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        }

        @Bean
        KmsTemplate kmsTemplate(KeyPair pair) throws Exception {
            var kms = Mockito.mock(KmsTemplate.class);
            String pem = "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())
                    + "\n-----END PUBLIC KEY-----\n";
            Mockito.when(kms.metadata("auth-jwt"))
                    .thenReturn(new KmsKeyMetadata("auth-jwt", "rsa", 1, 1, 1, false, false, true, Map.of(1, pem)));
            Mockito.when(kms.publicKey(ArgumentMatchers.any())).thenReturn(pem);
            Mockito.when(kms.sign(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                    .thenAnswer(invocation -> {
                        var signature = Signature.getInstance("SHA256withRSA");
                        signature.initSign(pair.getPrivate());
                        signature.update(invocation.getArgument(2, byte[].class));
                        return new KmsSignature(
                                "auth-jwt", 1, KmsSignatureAlgorithm.RSA_SHA256_PKCS1, signature.sign());
                    });
            return kms;
        }
    }
}
