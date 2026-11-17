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
package io.github.loadup.acquire.adapter.security;

import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.resourceserver.LoadUpJwtAuthenticationConverter;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtAcquireActorProviderTest {
    private final JwtAcquireActorProvider provider = new JwtAcquireActorProvider();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticated(String token, List<String> audience, String type, String merchant) {
        var builder = Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject("app")
                .claim("principal_type", type)
                .claim("aud", audience);
        if (merchant != null) {
            builder.claim("merchant_id", merchant);
        }
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        "verified", builder.build(), List.of(new SimpleGrantedAuthority("pay:acquire:create"))));
    }

    @Test
    void exposesOnlyTheVerifiedBusinessScopeAndPermissions() {
        authenticated("synthetic", List.of("acquire"), "MERCHANT_APP", "merchant");
        var actor = provider.current();
        assertEquals("0cb99328a82102b51641a7f7289b96eb11c9f6c64663c6328adc20d2cf62d76c", actor.scopeHash());
        assertEquals(Set.of("pay:acquire:create"), actor.permissions());
        assertFalse(actor.toString().contains("synthetic"));
    }

    @Test
    void spoofedTenantClaimCannotChangeConfiguredTenant() {
        var jwt = Jwt.withTokenValue("synthetic")
                .header("alg", "RS256")
                .subject("app")
                .claim("tenant_id", "spoofed-tenant")
                .claim("principal_type", "MERCHANT_APP")
                .claim("merchant_id", "merchant")
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        "verified", jwt, List.of(new SimpleGrantedAuthority("pay:acquire:create"))));
    }

    @Test
    void tokenRotationDoesNotChangeTheOriginalCallerScopeAndDiagnosticsHideTokens() {
        authenticated("synthetic-secret-v1", List.of("acquire", "product"), "MERCHANT_APP", "merchant");
        var first = provider.current();
        assertFalse(first.toString().contains("synthetic-secret-v1"));
        authenticated("synthetic-secret-v2", List.of("acquire", "product"), "MERCHANT_APP", "merchant");
        assertEquals(first.scopeHash(), provider.current().scopeHash());
    }

    @Test
    void missingMerchantBindingAndForgedMerchantIdentityAreRejected() {
        authenticated("synthetic", List.of("acquire"), "MERCHANT_APP", null);
        assertThrows(CommonException.class, provider::current);
        authenticated("synthetic", List.of("acquire"), "MERCHANT", "another-merchant");
        assertThrows(CommonException.class, provider::current);
    }

    @Test
    void serviceIdentityCannotBecomeAMerchantDelegation() {
        authenticated("synthetic-service", List.of("acquire"), "SERVICE", "merchant");
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, provider::current).getResultCode());
    }

    @Test
    void frameworkVerifiedJwtRetainsItsBusinessIdentityAfterCredentialErasure() {
        var jwt = Jwt.withTokenValue("synthetic-framework-token")
                .header("alg", "RS256")
                .subject("app")
                .claim("principal_type", "MERCHANT_APP")
                .claim("merchant_id", "merchant")
                .claim("permissions", List.of("pay:acquire:create"))
                .build();
        var authentication = new LoadUpJwtAuthenticationConverter().convert(jwt);
        authentication.eraseCredentials();
        SecurityContextHolder.getContext().setAuthentication(authentication);

        var actor = provider.current();
        assertEquals(Set.of("pay:acquire:create"), actor.permissions());
        assertFalse(actor.toString().contains("synthetic-framework-token"));
    }

    @Test
    void anonymousInvocationIsRejected() {
        assertThrows(CommonException.class, provider::current);
    }
}
