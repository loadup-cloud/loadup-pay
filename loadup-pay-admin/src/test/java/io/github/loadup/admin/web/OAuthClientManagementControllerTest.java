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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.loadup.admin.config.OAuthClientManagementHttpService;
import io.github.loadup.commons.enums.CommonResultCodeEnum;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.result.Result;
import io.github.loadup.commons.result.SuccessResponse;
import io.github.loadup.components.oauth.management.OAuthClientAuthorization;
import io.github.loadup.components.oauth.management.OAuthClientCreateRequest;
import io.github.loadup.components.oauth.management.OAuthClientCredentialResponse;
import io.github.loadup.components.oauth.management.OAuthClientExpiryRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class OAuthClientManagementControllerTest {
    private static final String REGISTRATION_ID = "acquire-payment";
    private static final String AUTHORIZATION = "Bearer operator-signed-token";
    private static final Instant EXPIRY = Instant.parse("2030-01-01T00:00:00Z");

    private AnnotationConfigApplicationContext context;
    private OAuthClientManagementHttpService clients;
    private OAuthClientManagementController controller;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        clients = context.getBean(OAuthClientManagementHttpService.class);
        controller = context.getBean(OAuthClientManagementController.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void createForwardsBearerAndRequestAndReturnsNoStoreRedactedCredential() {
        authenticate("OPERATOR", "oauth:client:create");
        var request = createRequest();
        var credential = credential("new-client-secret");
        when(clients.create(eq(AUTHORIZATION), eq(REGISTRATION_ID), same(request)))
                .thenReturn(SuccessResponse.of(credential));

        ResponseEntity<SuccessResponse<OAuthClientCredentialResponse>> response =
                controller.create(REGISTRATION_ID, request, AUTHORIZATION);

        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody().data()).isSameAs(credential);
        assertThat(response.getBody().data().secret()).isEqualTo("new-client-secret");
        assertThat(response.getBody().data().toString()).doesNotContain("new-client-secret");
        verify(clients).create(eq(AUTHORIZATION), eq(REGISTRATION_ID), same(request));
    }

    @Test
    void rotateForwardsOnceAndReturnsNoStoreCredential() {
        authenticate("OPERATOR", "oauth:client:rotate");
        var request = new OAuthClientExpiryRequest(EXPIRY);
        var credential = credential("rotated-client-secret");
        when(clients.rotate(eq(AUTHORIZATION), eq(REGISTRATION_ID), same(request)))
                .thenReturn(SuccessResponse.of(credential));

        ResponseEntity<SuccessResponse<OAuthClientCredentialResponse>> response =
                controller.rotate(REGISTRATION_ID, request, AUTHORIZATION);

        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody().data().secret()).isEqualTo("rotated-client-secret");
        assertThat(response.getBody().data().toString()).doesNotContain("rotated-client-secret");
        verify(clients).rotate(eq(AUTHORIZATION), eq(REGISTRATION_ID), same(request));
    }

    @Test
    void missingPermissionAndNonOperatorAreDeniedBeforeCallingBackend() {
        authenticate("OPERATOR", "oauth:client:read");
        assertThatThrownBy(() -> controller.create(REGISTRATION_ID, createRequest(), AUTHORIZATION))
                .isInstanceOf(AccessDeniedException.class);

        authenticate("SERVICE", "oauth:client:create");
        assertThatThrownBy(() -> controller.create(REGISTRATION_ID, createRequest(), AUTHORIZATION))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(clients);
    }

    @Test
    void conflictEnvelopeMapsToKnownCommonException() {
        authenticate("OPERATOR", "oauth:client:create");
        var request = createRequest();
        when(clients.create(eq(AUTHORIZATION), eq(REGISTRATION_ID), same(request)))
                .thenReturn(new SuccessResponse<>(Result.buildFailure(CommonResultCodeEnum.CONFLICT), null));

        assertThatThrownBy(() -> controller.create(REGISTRATION_ID, request, AUTHORIZATION))
                .isInstanceOf(CommonException.class)
                .satisfies(failure -> assertThat(
                                ((CommonException) failure).getResultCode().getCode())
                        .isEqualTo(CommonResultCodeEnum.CONFLICT.getCode()));
        verify(clients).create(eq(AUTHORIZATION), eq(REGISTRATION_ID), same(request));
    }

    private static OAuthClientCreateRequest createRequest() {
        var authorization = new OAuthClientAuthorization(
                "Payment caller", Set.of("pay:payment:read"), "payment", Map.of(), Duration.ofMinutes(5));
        return new OAuthClientCreateRequest("acquire.payment", authorization, EXPIRY);
    }

    private static OAuthClientCredentialResponse credential(String secret) {
        return new OAuthClientCredentialResponse(REGISTRATION_ID, "acquire.payment", secret, EXPIRY);
    }

    private static void authenticate(String principalType, String permission) {
        var jwt = Jwt.withTokenValue("signed-operator-token")
                .header("alg", "none")
                .subject("operator-1")
                .issuedAt(Instant.parse("2026-01-01T00:00:00Z"))
                .expiresAt(Instant.parse("2030-01-01T00:00:00Z"))
                .claim("principal_type", principalType)
                .build();
        var authentication = new JwtAuthenticationToken(jwt, Set.of(new SimpleGrantedAuthority(permission)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class TestConfiguration {
        @Bean
        OAuthClientManagementHttpService oauthClientManagementHttpService() {
            return mock(OAuthClientManagementHttpService.class);
        }

        @Bean
        ManagementResponses managementResponses() {
            return new ManagementResponses();
        }

        @Bean
        OAuthClientManagementController oauthClientManagementController(
                OAuthClientManagementHttpService clients, ManagementResponses responses) {
            return new OAuthClientManagementController(clients, responses);
        }
    }
}
