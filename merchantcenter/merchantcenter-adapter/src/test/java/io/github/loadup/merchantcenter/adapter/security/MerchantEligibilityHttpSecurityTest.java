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
package io.github.loadup.merchantcenter.adapter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.github.loadup.components.observability.ApiResultMetrics;
import io.github.loadup.components.resourceserver.ResourceServerAutoConfiguration;
import io.github.loadup.merchantcenter.adapter.web.MerchantEligibilityController;
import io.github.loadup.merchantcenter.client.dto.MerchantEligibilityDTO;
import io.github.loadup.merchantcenter.client.facade.MerchantEligibilityFacade;
import io.github.loadup.merchantcenter.client.http.CustomerHttpService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.Filter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class MerchantEligibilityHttpSecurityTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ResourceServerAutoConfiguration.class))
            .withUserConfiguration(Support.class)
            .withPropertyValues(
                    "loadup.security.resource-server.enabled=true",
                    "loadup.security.resource-server.issuer-uri=https://issuer.test",
                    "loadup.security.resource-server.audience=customer");

    private static String token(KeyPair key, String audience, String use, Instant expires) throws Exception {
        var claims = new JWTClaimsSet.Builder()
                .subject("app")
                .issuer("https://issuer.test")
                .audience(audience)
                .issueTime(Date.from(Instant.now().minusSeconds(10)))
                .expirationTime(Date.from(expires))
                .claim("token_use", use)
                .claim("tenant_id", "trusted-tenant")
                .claim("principal_type", "MERCHANT_APP")
                .claim("merchant_id", "merchant")
                .build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(key.getPrivate()));
        return jwt.serialize();
    }

    @Test
    void validatesSignatureAudienceUseAndExpiryBeforeCallingTheFacade() {
        runner.run(context -> {
            var keys = context.getBean(KeyPair.class);
            var otherKey = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            var calls = context.getBean(AtomicInteger.class);
            MockMvc mvc = MockMvcBuilders.standaloneSetup(
                            new MerchantEligibilityController(context.getBean(MerchantEligibilityFacade.class)))
                    .addFilters(context.getBean("springSecurityFilterChain", Filter.class))
                    .build();
            String[] invalid = {
                "invalid",
                token(otherKey, "customer", "access", Instant.now().plusSeconds(300)),
                token(keys, "other", "access", Instant.now().plusSeconds(300)),
                token(keys, "customer", "refresh", Instant.now().plusSeconds(300)),
                token(keys, "customer", "access", Instant.now().minusSeconds(300))
            };
            for (String bearer : invalid) {
                var response = mvc.perform(post(CustomerHttpService.AUTHORIZE_PATH)
                                .header("Authorization", "Bearer " + bearer)
                                .contentType("application/json")
                                .content("{\"payeeMerchantId\":\"merchant\",\"action\":\"CREATE_ORDER\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse();
                assertThat(response.getContentAsString()).contains("UNAUTHENTICATED");
            }
            assertThat(calls.get()).isZero();
            mvc.perform(post(CustomerHttpService.AUTHORIZE_PATH)
                            .header(
                                    "Authorization",
                                    "Bearer "
                                            + token(
                                                    keys,
                                                    "customer",
                                                    "access",
                                                    Instant.now().plusSeconds(300)))
                            .header("X-Tenant-Id", "forged-tenant")
                            .contentType("application/json")
                            .content("{\"payeeMerchantId\":\"merchant\",\"action\":\"CREATE_ORDER\"}"))
                    .andExpect(status().isOk());
            assertThat(calls.get()).isEqualTo(1);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class Support {
        @Bean
        KeyPair keys() throws Exception {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        }

        @Bean
        JwtDecoder decoder(KeyPair keys) {
            return NimbusJwtDecoder.withPublicKey((RSAPublicKey) keys.getPublic())
                    .build();
        }

        @Bean
        AtomicInteger calls() {
            return new AtomicInteger();
        }

        @Bean
        MerchantEligibilityFacade eligibility(AtomicInteger calls) {
            var actors = new JwtCustomerActorProvider();
            return command -> {
                assertThat(actors.current().tenantId()).isEqualTo("trusted-tenant");
                calls.incrementAndGet();
                return new MerchantEligibilityDTO(false, "CUSTOMER_ACTION_FORBIDDEN", null, 0, null, null);
            };
        }

        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().build();
        }

        @Bean(destroyMethod = "close")
        SimpleMeterRegistry metrics() {
            return new SimpleMeterRegistry();
        }

        @Bean
        ApiResultMetrics apiResultMetrics(SimpleMeterRegistry metrics) {
            return new ApiResultMetrics(metrics);
        }
    }
}
