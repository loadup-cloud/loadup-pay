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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.merchantcenter.adapter.web.MerchantController;
import io.github.loadup.merchantcenter.client.dto.MerchantDTO;
import io.github.loadup.merchantcenter.client.facade.MerchantFacade;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class MerchantControllerSecurityTest {
    private AnnotationConfigApplicationContext context;
    private MerchantFacade facade;
    private MerchantController controller;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        context = new AnnotationConfigApplicationContext(Support.class);
        facade = context.getBean(MerchantFacade.class);
        controller = context.getBean(MerchantController.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void authorizedOperatorDoesNotNeedTenantClaim() {
        authenticate("OPERATOR", "merchant:read");
        when(facade.detail("entry")).thenReturn(mock(MerchantDTO.class));

        controller.detail(new IdQuery("entry"));

        verify(facade).detail("entry");
    }

    @Test
    void missingAuthenticationCannotReachFacade() {
        assertThatThrownBy(() -> controller.detail(new IdQuery("entry")))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(facade);
    }

    @Test
    void missingAuthorityCannotReachFacade() {
        authenticate("OPERATOR", "unrelated");
        assertThatThrownBy(() -> controller.detail(new IdQuery("entry"))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(facade);
    }

    @Test
    void wrongPrincipalTypeCannotReachFacade() {
        authenticate("SERVICE", "merchant:read");
        assertThatThrownBy(() -> controller.detail(new IdQuery("entry"))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(facade);
    }

    private static void authenticate(String type, String authority) {
        var jwt = Jwt.withTokenValue("verified")
                .header("alg", "RS256")
                .subject("operator")
                .claim("principal_type", type)
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(authority))));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class Support {
        @Bean
        MerchantFacade facade() {
            return mock(MerchantFacade.class);
        }

        @Bean
        MerchantController controller(MerchantFacade facade) {
            return new MerchantController(facade);
        }
    }
}
