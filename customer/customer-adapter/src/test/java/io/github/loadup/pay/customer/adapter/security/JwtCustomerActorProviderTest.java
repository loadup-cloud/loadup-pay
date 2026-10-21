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
package io.github.loadup.pay.customer.adapter.security;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.customer.domain.model.PrincipalType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtCustomerActorProviderTest {
    private final JwtCustomerActorProvider provider = new JwtCustomerActorProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticated(Jwt jwt, String... permissions) {
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        "verified-principal",
                        jwt,
                        java.util.Arrays.stream(permissions)
                                .map(SimpleGrantedAuthority::new)
                                .toList()));
    }

    private Jwt.Builder token(String type) {
        return Jwt.withTokenValue("synthetic-test-token")
                .header("alg", "RS256")
                .subject("app")
                .claim("tenant_id", "tenant")
                .claim("principal_type", type);
    }

    @Test
    void absentOrUnverifiedCredentialsAreRejected() {
        assertThrows(CommonException.class, provider::current);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated("principal", "plain-credentials", List.of()));
        assertThrows(CommonException.class, provider::current);
    }

    @Test
    void merchantAppMustCarryItsTrustedMerchantBinding() {
        authenticated(token("MERCHANT_APP").build());
        assertThrows(CommonException.class, provider::current);
        authenticated(token("MERCHANT_APP").claim("merchant_id", "parent").build());
        assertEquals("parent", provider.current().merchantId());
        assertFalse(provider.current().canManage());
    }

    @Test
    void merchantPrincipalCannotClaimAnotherMerchantIdentity() {
        authenticated(token("MERCHANT").claim("merchant_id", "different").build());
        assertThrows(CommonException.class, provider::current);
    }

    @Test
    void onlyExplicitManageAuthorityEnablesManagement() {
        authenticated(token("OPERATOR").build(), "ROLE_ADMIN");
        assertFalse(provider.current().canManage());
        authenticated(token("OPERATOR").build(), JwtCustomerActorProvider.MANAGE_PERMISSION);
        assertTrue(provider.current().canManage());
        assertEquals(PrincipalType.OPERATOR, provider.current().principalType());
        assertEquals("tenant", provider.current().tenantId());
    }

    @Test
    void unknownPrincipalTypesAreRejected() {
        authenticated(token("CONSUMER").build());
        assertThrows(CommonException.class, provider::current);
    }
}
