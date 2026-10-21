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
package io.github.loadup.pay.payment.adapter.security;

import io.github.loadup.commons.error.CommonException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtPaymentActorProviderTest {
    final JwtPaymentActorProvider provider = new JwtPaymentActorProvider();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    void authenticated(String type, String service, String tenant) {
        var jwt = Jwt.withTokenValue("synthetic")
                .header("alg", "RS256")
                .subject("acquire")
                .claim("principal_type", type)
                .claim("service_id", service)
                .claim("tenant_id", tenant)
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        "verified", jwt, List.of(new SimpleGrantedAuthority("pay:payment:accept"))));
    }

    @Test
    void verifiedServiceClaimsProvideTheTenantAndExactPermissions() {
        authenticated("SERVICE", "acquire", "tenant");
        assertEquals("acquire", provider.current().serviceId());
        assertEquals("tenant", provider.current().tenantId());
        assertTrue(provider.current().permissions().contains("pay:payment:accept"));
    }

    @Test
    void aMerchantCannotPromoteItselfIntoTheServiceProtocol() {
        authenticated("MERCHANT_APP", "acquire", "tenant");
        assertThrows(CommonException.class, provider::current);
    }

    @Test
    void serviceBindingAndTenantWhitespaceAreRejected() {
        authenticated("SERVICE", "product", "tenant");
        assertThrows(CommonException.class, provider::current);
        authenticated("SERVICE", "acquire", " tenant");
        assertThrows(CommonException.class, provider::current);
    }

    @Test
    void authenticationWithoutVerifiedJwtCredentialsIsRejected() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated("payment", "raw-header", List.of()));
        assertThrows(CommonException.class, provider::current);
    }
}

