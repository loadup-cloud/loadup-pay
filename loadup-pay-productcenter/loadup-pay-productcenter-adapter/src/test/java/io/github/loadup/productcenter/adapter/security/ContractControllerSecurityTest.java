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
package io.github.loadup.productcenter.adapter.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.result.PageDTO;
import io.github.loadup.productcenter.adapter.web.ContractController;
import io.github.loadup.productcenter.client.command.CatalogActionCommand;
import io.github.loadup.productcenter.client.command.CatalogSaveCommand;
import io.github.loadup.productcenter.client.dto.CatalogVersionDTO;
import io.github.loadup.productcenter.client.facade.CatalogFacade;
import io.github.loadup.productcenter.client.query.CatalogPageQuery;
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

class ContractControllerSecurityTest {
    private AnnotationConfigApplicationContext context;
    private RecordingCatalog facade;
    private ContractController controller;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        context = new AnnotationConfigApplicationContext(Support.class);
        facade = context.getBean(RecordingCatalog.class);
        controller = context.getBean(ContractController.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        context.close();
    }

    @Test
    void authorizedOperatorDoesNotNeedTenantClaim() {
        authenticate("OPERATOR", "contract:catalog:read");

        controller.catalogDetail(new IdQuery("entry"));

        assertEquals(1, facade.calls);
        assertEquals("entry", facade.lastId);
    }

    @Test
    void missingAuthenticationCannotReachFacade() {
        assertThrows(
                AuthenticationCredentialsNotFoundException.class, () -> controller.catalogDetail(new IdQuery("entry")));
        assertEquals(0, facade.calls);
    }

    @Test
    void missingAuthorityCannotReachFacade() {
        authenticate("OPERATOR", "unrelated");
        assertThrows(AccessDeniedException.class, () -> controller.catalogDetail(new IdQuery("entry")));
        assertEquals(0, facade.calls);
    }

    @Test
    void wrongPrincipalTypeCannotReachFacade() {
        authenticate("SERVICE", "contract:catalog:read");
        assertThrows(AccessDeniedException.class, () -> controller.catalogDetail(new IdQuery("entry")));
        assertEquals(0, facade.calls);
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
        RecordingCatalog facade() {
            return new RecordingCatalog();
        }

        @Bean
        ContractController controller(CatalogFacade facade) {
            return new ContractController(facade, null, null);
        }
    }

    static class RecordingCatalog implements CatalogFacade {
        private int calls;
        private String lastId;

        @Override
        public CatalogVersionDTO detail(String id) {
            calls++;
            lastId = id;
            return null;
        }

        @Override
        public CatalogVersionDTO save(CatalogSaveCommand command, String actor) {
            throw new AssertionError("Unexpected save");
        }

        @Override
        public CatalogVersionDTO publish(CatalogActionCommand command, String actor) {
            throw new AssertionError("Unexpected publish");
        }

        @Override
        public CatalogVersionDTO retire(CatalogActionCommand command, String actor) {
            throw new AssertionError("Unexpected retire");
        }

        @Override
        public PageDTO<CatalogVersionDTO> page(CatalogPageQuery query) {
            throw new AssertionError("Unexpected page");
        }
    }
}
