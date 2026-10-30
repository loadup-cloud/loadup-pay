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
package io.github.loadup.pay.product.adapter.security;

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

class JwtPublicationActorProviderTest {
    final JwtPublicationActorProvider provider = new JwtPublicationActorProvider();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    void token(String type, String service) {
        var builder = Jwt.withTokenValue("synthetic")
                .header("alg", "RS256")
                .subject("publisher")
                .claim("tenant_id", "tenant")
                .claim("principal_type", type);
        if (service != null) {
            builder.claim("service_id", service);
        }
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        "verified", builder.build(), List.of(new SimpleGrantedAuthority("pay:product:publish"))));
    }

    @Test
    void merchantsCannotInvokeThePrivilegedPublicationProtocolEvenWithAMisassignedPermission() {
        token("MERCHANT_APP", null);
        assertThrows(CommonException.class, provider::current);
    }

    @Test
    void serviceSubjectMustMatchTheSignedServiceBinding() {
        token("SERVICE", "another");
        assertThrows(CommonException.class, provider::current);
        token("SERVICE", "publisher");
        assertEquals("publisher", provider.current().principalId());
    }

    @Test
    void operatorsKeepTheirOwnAuditableIdentityAndPermissionSet() {
        token("OPERATOR", null);
        assertEquals("publisher", provider.current().principalId());
        assertTrue(provider.current().permissions().contains("pay:product:publish"));
    }
}
