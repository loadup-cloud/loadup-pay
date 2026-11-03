package io.github.loadup.pay.authserver;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.nimbusds.jose.jwk.JWKSet;
import io.github.loadup.components.kms.*;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
@Import(ServiceAuthorizationFlowIT.Fixture.class)
class ServiceAuthorizationFlowIT {
    static final String SECRET = "synthetic-client-secret-32-characters";
    private final MockMvc mvc;
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactions;
    private final KeyPair pair;

    @Autowired
    ServiceAuthorizationFlowIT(MockMvc mvc, JdbcTemplate jdbc, PlatformTransactionManager transactions, KeyPair pair) {
        this.mvc = mvc;
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.pair = pair;
    }

    ServiceClientCatalog catalog(boolean enabled) {
        return new ServiceClientCatalog(List.of(new ServiceClientCatalog.Client(
                "acquire.tenant.payment",
                "tenant",
                "acquire",
                "payment",
                Set.of("pay:payment:read"),
                "TEST_CLIENT_SECRET",
                enabled)));
    }

    void synchronize(boolean enabled) {
        ServiceClientRegistry.synchronize(
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
        var request = post("/oauth2/token")
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
        assertThat(jwt.getSubject()).isEqualTo("acquire");
        assertThat(jwt.getAudience()).containsExactly("payment");
        assertThat(jwt.getClaimAsString("tenant_id")).isEqualTo("tenant");
        assertThat(jwt.getClaimAsString("principal_type")).isEqualTo("SERVICE");
        assertThat(jwt.getClaimAsString("service_id")).isEqualTo("acquire");
        assertThat(jwt.getClaimAsString("token_use")).isEqualTo("access");
        assertThat(jwt.getHeaders().get("kid")).isEqualTo("auth-jwt-v1");
        assertThat(new LoadUpJwtAuthenticationConverter().convert(jwt).getAuthorities())
                .extracting("authority")
                .contains("pay:payment:read");
        assertThat(new JdbcRegisteredClientRepository(jdbc).findByClientId("acquire.tenant.payment"))
                .isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from oauth2_authorization", Long.class))
                .isPositive();
        var jwks = mvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(JWKSet.parse(jwks).getKeys())
                .allSatisfy(key -> assertThat(key.isPrivate()).isFalse());
    }

    @Test
    void omittedScopesDoNotGrantTheRegisteredBusinessPermissions() throws Exception {
        var jwt = decoder("payment").decode(issue(null));
        assertThat(jwt.getClaimAsStringList("permissions")).isEmpty();
        assertThat(new LoadUpJwtAuthenticationConverter().convert(jwt).getAuthorities()).isEmpty();
    }

    @Test
    void wrongCredentialsExtraPermissionsWrongAudienceAndTamperingAreRejected() throws Exception {
        mvc.perform(post("/oauth2/token")
                        .with(httpBasic("acquire.tenant.payment", "wrong"))
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/oauth2/token")
                        .with(httpBasic("acquire.tenant.payment", SECRET))
                        .param("grant_type", "client_credentials")
                        .param("scope", "pay:payment:accept"))
                .andExpect(status().isBadRequest());
        String token = issue("pay:payment:read");
        assertThatThrownBy(() -> decoder("acquire").decode(token)).isInstanceOf(JwtException.class);
        String[] parts = token.split("[.]");
        String tampered = parts[0] + "."
                + Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString("{\"sub\":\"attacker\"}".getBytes(StandardCharsets.UTF_8))
                + "." + parts[2];
        assertThatThrownBy(() -> decoder("payment").decode(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void requestTenantAndAudienceCannotOverrideTheServerGrant() throws Exception {
        var response = mvc.perform(post("/oauth2/token")
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
        assertThat(decoder("payment").decode(token).getClaimAsString("tenant_id"))
                .isEqualTo("tenant");
    }

    @Test
    void disablingGrantStopsNewIssuanceAndUnknownEndpointsRemainClosed() throws Exception {
        synchronize(false);
        mvc.perform(post("/oauth2/token")
                        .with(httpBasic("acquire.tenant.payment", SECRET))
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/clients")).andExpect(status().isForbidden());
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
            var kms = mock(KmsTemplate.class);
            String pem = "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getEncoder().encodeToString(pair.getPublic().getEncoded())
                    + "\n-----END PUBLIC KEY-----\n";
            when(kms.metadata("auth-jwt"))
                    .thenReturn(new KmsKeyMetadata("auth-jwt", "rsa", 1, 1, 1, false, false, true, Map.of(1, pem)));
            when(kms.publicKey(any())).thenReturn(pem);
            when(kms.sign(any(), any(), any())).thenAnswer(invocation -> {
                var signature = Signature.getInstance("SHA256withRSA");
                signature.initSign(pair.getPrivate());
                signature.update(invocation.getArgument(2, byte[].class));
                return new KmsSignature("auth-jwt", 1, KmsSignatureAlgorithm.RSA_SHA256_PKCS1, signature.sign());
            });
            return kms;
        }
    }
}
