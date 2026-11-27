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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.github.loadup.commons.enums.CommonResultCodeEnum;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.kms.KmsKeyMetadata;
import io.github.loadup.components.kms.KmsSignature;
import io.github.loadup.components.kms.KmsSignatureAlgorithm;
import io.github.loadup.components.kms.KmsTemplate;
import io.github.loadup.components.oauth.OAuthClientSettings;
import io.github.loadup.components.oauth.client.IssuedClientCredential;
import io.github.loadup.components.oauth.client.OAuthClientManager;
import io.github.loadup.components.oauth.management.OAuthClientAuthorization;
import io.github.loadup.components.oauth.management.OAuthClientCreateRequest;
import io.github.loadup.components.oauth.management.OAuthClientManagementPaths;
import io.github.loadup.components.resourceserver.LoadUpJwtAuthenticationConverter;
import io.github.loadup.components.resourceserver.ResourceServerAutoConfiguration;
import io.github.loadup.components.resourceserver.ResourceServerProperties;
import io.github.loadup.components.testcontainers.annotation.ContainerType;
import io.github.loadup.components.testcontainers.annotation.EnableTestContainers;
import io.github.loadup.modules.audit.client.facade.AuditFacade;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
        properties = {
            "AUTHSERVER_JDBC_URL=jdbc:postgresql://127.0.0.1:1/unused",
            "AUTHSERVER_JDBC_USERNAME=sa",
            "AUTHSERVER_JDBC_PASSWORD=unused",
            "loadup.kms.enabled=false",
            "JWT_ISSUER_URI=https://auth.test"
        })
@EnableTestContainers(value = ContainerType.POSTGRESQL, reuse = false)
@AutoConfigureMockMvc
@Import(OAuth2AuthorizationFlowIT.Fixture.class)
class OAuth2AuthorizationFlowIT {
    static final String SECRET = "synthetic-client-secret-32-characters";
    private final MockMvc mvc;
    private final JdbcTemplate jdbc;
    private final KeyPair pair;
    private final OAuthClientManager manager;
    private final KeyPair operatorKeys;

    @MockitoSpyBean
    AuditFacade audit;

    @Autowired
    OAuth2AuthorizationFlowIT(
            MockMvc mvc,
            JdbcTemplate jdbc,
            KeyPair pair,
            OAuthClientManager manager,
            @Qualifier("operatorKeys") KeyPair operatorKeys) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.pair = pair;
        this.manager = manager;
        this.operatorKeys = operatorKeys;
    }

    void register(boolean enabled) {
        new JdbcRegisteredClientRepository(jdbc)
                .save(RegisteredClient.withId("acquire-payment")
                        .clientId("acquire.payment")
                        .clientSecret(PasswordEncoderFactories.createDelegatingPasswordEncoder()
                                .encode(SECRET))
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                        .scope("pay:payment:read")
                        .clientSettings(ClientSettings.builder()
                                .setting(OAuthClientSettings.ENABLED, enabled)
                                .setting(OAuthClientSettings.AUDIENCE, "payment")
                                .setting(PayOAuthClientSettings.SERVICE, "acquire")
                                .setting(PayOAuthClientSettings.PRINCIPAL_TYPE, "SERVICE")
                                .build())
                        .tokenSettings(TokenSettings.builder()
                                .accessTokenTimeToLive(Duration.ofMinutes(5))
                                .build())
                        .build());
    }

    @BeforeEach
    void resetGrant() {
        register(true);
    }

    String issue(String scope) throws Exception {
        return issue("acquire.payment", SECRET, scope);
    }

    String issue(String clientId, String secret, String scope) throws Exception {
        var request = MockMvcRequestBuilders.post("/oauth2/token")
                .with(httpBasic(clientId, secret))
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
    void clientCredentialsIssueTenantFreeKmsSignedTokensThatResourceServersVerifyLocally() throws Exception {
        var jwt = decoder("payment").decode(issue("pay:payment:read"));
        Assertions.assertThat(jwt.getSubject()).isEqualTo("acquire");
        Assertions.assertThat(jwt.getAudience()).containsExactly("payment");
        Assertions.assertThat(jwt.getClaimAsString("tenant_id")).isNull();
        Assertions.assertThat(jwt.getClaimAsString("principal_type")).isEqualTo("SERVICE");
        Assertions.assertThat(jwt.getClaimAsString("service_id")).isEqualTo("acquire");
        Assertions.assertThat(jwt.getClaimAsString("token_use")).isEqualTo("access");
        Assertions.assertThat(jwt.getHeaders().get("kid")).isEqualTo("auth-jwt-v1");
        Assertions.assertThat(
                        new LoadUpJwtAuthenticationConverter().convert(jwt).getAuthorities())
                .extracting("authority")
                .contains("pay:payment:read");
        Assertions.assertThat(new JdbcRegisteredClientRepository(jdbc).findByClientId("acquire.payment"))
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
                        .with(httpBasic("acquire.payment", "wrong"))
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.post("/oauth2/token")
                        .with(httpBasic("acquire.payment", SECRET))
                        .param("grant_type", "client_credentials")
                        .param("scope", "pay:payment:accept"))
                .andExpect(status().isBadRequest());
        String token = issue("pay:payment:read");
        Assertions.assertThatThrownBy(() -> decoder("acquire").decode(token)).isInstanceOf(JwtException.class);
        String[] parts = token.split("[.]");
        String tampered = parts[0]
                + "."
                + Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString("{\"sub\":\"attacker\"}".getBytes(StandardCharsets.UTF_8))
                + "."
                + parts[2];
        Assertions.assertThatThrownBy(() -> decoder("payment").decode(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void requestTenantAndAudienceCannotOverrideTheServerGrant() throws Exception {
        var response = mvc.perform(MockMvcRequestBuilders.post("/oauth2/token")
                        .with(httpBasic("acquire.payment", SECRET))
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
                .isNull();
    }

    @Test
    void disablingGrantStopsNewIssuanceAndUnknownEndpointsRemainClosed() throws Exception {
        register(false);
        mvc.perform(MockMvcRequestBuilders.post("/oauth2/token")
                        .with(httpBasic("acquire.payment", SECRET))
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.get("/admin/clients")).andExpect(status().isForbidden());
    }

    @Test
    void managedClientAuthorizationExpiryRotationAndDisableAreEnforcedByTokenEndpoint() throws Exception {
        var template = managedTemplate("managed-payment", "managed.payment");
        var created = manager.create(template, Instant.now().plusSeconds(3600));
        Assertions.assertThat(decoder("payment")
                        .decode(issue(created.clientId(), created.secret(), "pay:payment:read"))
                        .getSubject())
                .isEqualTo("acquire");
        Assertions.assertThat(manager.get(created.registrationId()).getClientSecret())
                .isNull();
        var stored = new JdbcRegisteredClientRepository(jdbc).findById(created.registrationId());
        Assertions.assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder()
                        .matches(created.secret(), stored.getClientSecret()))
                .isTrue();

        var changed = RegisteredClient.from(manager.get(created.registrationId()))
                .scopes(scopes -> {
                    scopes.clear();
                    scopes.add("pay:payment:accept");
                })
                .build();
        manager.updateAuthorization(created.registrationId(), changed);
        mvc.perform(MockMvcRequestBuilders.post("/oauth2/token")
                        .with(httpBasic(created.clientId(), created.secret()))
                        .param("grant_type", "client_credentials")
                        .param("scope", "pay:payment:read"))
                .andExpect(status().isBadRequest());
        issue(created.clientId(), created.secret(), "pay:payment:accept");

        var rotated =
                manager.rotateCredential(created.registrationId(), Instant.now().plusSeconds(3600));
        assertCredentialRejected(created.clientId(), created.secret());
        issue(rotated.clientId(), rotated.secret(), "pay:payment:accept");
        manager.setCredentialExpiry(rotated.registrationId(), Instant.now().minusSeconds(1));
        assertCredentialRejected(rotated.clientId(), rotated.secret());
        var renewed =
                manager.rotateCredential(rotated.registrationId(), Instant.now().plusSeconds(3600));
        issue(renewed.clientId(), renewed.secret(), "pay:payment:accept");
        manager.disable(renewed.registrationId());
        assertCredentialRejected(renewed.clientId(), renewed.secret());
        var stillDisabled =
                manager.rotateCredential(renewed.registrationId(), Instant.now().plusSeconds(3600));
        assertCredentialRejected(stillDisabled.clientId(), stillDisabled.secret());
    }

    private RegisteredClient managedTemplate(String id, String clientId) {
        return RegisteredClient.withId(id)
                .clientId(clientId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("pay:payment:read")
                .clientSettings(ClientSettings.builder()
                        .setting(OAuthClientSettings.AUDIENCE, "payment")
                        .setting(PayOAuthClientSettings.SERVICE, "acquire")
                        .setting(PayOAuthClientSettings.PRINCIPAL_TYPE, "SERVICE")
                        .build())
                .build();
    }

    @Test
    void concurrentCreationCannotOverwriteAnotherIssuedCredential() throws Exception {
        var template = managedTemplate("concurrent-payment", "concurrent.payment");
        var winner = new AtomicReference<IssuedClientCredential>();
        var start = new CyclicBarrier(2);
        Callable<Boolean> create = () -> {
            start.await(10, TimeUnit.SECONDS);
            try {
                winner.set(manager.create(template, Instant.now().plusSeconds(3600)));
                return true;
            } catch (CommonException conflict) {
                Assertions.assertThat(conflict.getResultCode()).isEqualTo(CommonResultCodeEnum.CONFLICT);
                return false;
            }
        };
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(create);
            var second = pool.submit(create);
            Assertions.assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        var issued = winner.get();
        issue(issued.clientId(), issued.secret(), "pay:payment:read");
    }

    private void assertCredentialRejected(String clientId, String secret) throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/oauth2/token")
                        .with(httpBasic(clientId, secret))
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized());
    }

    private String operatorToken(KeyPair signer, String type, String permission) throws Exception {
        var claims = new JWTClaimsSet.Builder()
                .subject("operator-1")
                .issuer("http://admin:8080")
                .audience("admin")
                .issueTime(Date.from(Instant.now().minusSeconds(1)))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .claim("token_use", "access")
                .claim("principal_type", type)
                .claim("permissions", List.of(permission))
                .build();
        var token = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        token.sign(new RSASSASigner(signer.getPrivate()));
        return "Bearer " + token.serialize();
    }

    private OAuthClientCreateRequest managementRequest(String clientId) {
        return new OAuthClientCreateRequest(
                clientId,
                new OAuthClientAuthorization(
                        "Managed client",
                        Set.of("pay:payment:read"),
                        "payment",
                        Map.of("pay.service.identity", "acquire", "pay.service.principal-type", "SERVICE"),
                        Duration.ofMinutes(5)),
                Instant.now().plusSeconds(3600));
    }

    @Test
    void operatorHttpManagementRequiresTrustedSignatureTypeAndPermissionAndPersistsTargetAudit() throws Exception {
        String id = "http-managed-payment";
        String endpoint = OAuthClientManagementPaths.BASE + "/" + id + "/create";
        String body = JsonMapper.builder().build().writeValueAsString(managementRequest("http.managed.payment"));
        String[] denied = {
            operatorToken(pair, "OPERATOR", "oauth:client:create"),
            operatorToken(operatorKeys, "SERVICE", "oauth:client:create"),
            operatorToken(operatorKeys, "OPERATOR", "oauth:client:read")
        };
        for (String authorization : denied) {
            mvc.perform(MockMvcRequestBuilders.post(endpoint)
                            .header("Authorization", authorization)
                            .contentType("application/json")
                            .content(body))
                    .andExpect(status().isOk());
            Assertions.assertThat(new JdbcRegisteredClientRepository(jdbc).findById(id))
                    .isNull();
        }
        var result = mvc.perform(MockMvcRequestBuilders.post(endpoint)
                        .header("Authorization", operatorToken(operatorKeys, "OPERATOR", "oauth:client:create"))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        Assertions.assertThat(result.getHeader("Cache-Control")).contains("no-store");
        var data = JsonMapper.builder()
                .build()
                .readTree(result.getContentAsString())
                .get("data");
        Assertions.assertThat(data.get("registrationId").asString()).isEqualTo(id);
        String credential = data.get("secret").asString();
        Assertions.assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder()
                        .matches(
                                credential,
                                new JdbcRegisteredClientRepository(jdbc)
                                        .findById(id)
                                        .getClientSecret()))
                .isTrue();
        Assertions.assertThat(jdbc.queryForObject(
                        "select count(*) from audit_event where actor_id = ? and action = ? and request_path = ? and"
                                + " outcome = ?",
                        Long.class,
                        "operator-1",
                        "OAUTH_CLIENT_CREATE",
                        endpoint,
                        "SUCCESS"))
                .isEqualTo(1L);
        Assertions.assertThat(jdbc.queryForObject(
                        "select count(*) from audit_event where request_path = ? and (action like ? or request_path"
                                + " like ?)",
                        Long.class,
                        endpoint,
                        "%" + credential + "%",
                        "%" + credential + "%"))
                .isZero();
    }

    @Test
    void anAuditFailureRollsBackClientCreationBeforeAnyCredentialIsReturned() throws Exception {
        String id = "audit-rollback-payment";
        String endpoint = OAuthClientManagementPaths.BASE + "/" + id + "/create";
        Mockito.doThrow(new IllegalStateException("synthetic audit failure"))
                .when(audit)
                .record(ArgumentMatchers.argThat(event -> event != null
                        && endpoint.equals(event.path())
                        && "SUCCESS".equals(event.outcome())
                        && "OAUTH_CLIENT_CREATE".equals(event.action())));
        try {
            var response = mvc.perform(MockMvcRequestBuilders.post(endpoint)
                            .header("Authorization", operatorToken(operatorKeys, "OPERATOR", "oauth:client:create"))
                            .contentType("application/json")
                            .content(JsonMapper.builder()
                                    .build()
                                    .writeValueAsString(managementRequest("audit.rollback.payment"))))
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse();
            Assertions.assertThat(response.getContentAsString())
                    .contains("SYS_ERROR")
                    .doesNotContain("\"secret\"");
            Assertions.assertThat(new JdbcRegisteredClientRepository(jdbc).findById(id))
                    .isNull();
            Assertions.assertThat(jdbc.queryForObject(
                            "select count(*) from audit_event where action = ? and request_path = ? and outcome = ?",
                            Long.class,
                            "OAUTH_CLIENT_CREATE",
                            endpoint,
                            "FAILURE"))
                    .isEqualTo(1L);
        } finally {
            Mockito.reset(audit);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fixture {
        @Bean
        @Primary
        KeyPair keyPair() throws Exception {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        }

        @Bean
        KeyPair operatorKeys() throws Exception {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        }

        @Bean
        JwtDecoder managementDecoder(@Qualifier("operatorKeys") KeyPair operatorKeys) {
            return NimbusJwtDecoder.withPublicKey((RSAPublicKey) operatorKeys.getPublic())
                    .build();
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
