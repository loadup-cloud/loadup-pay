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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtProductActorProviderTest {
    private final JwtProductActorProvider provider = new JwtProductActorProvider("customer");

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticated(String token, List<String> audience, String type, String merchant) {
        var builder = Jwt.withTokenValue(token)
                .header("alg", "RS256")
                .subject("app")
                .claim("tenant_id", "tenant")
                .claim("principal_type", type)
                .claim("aud", audience);
        if (merchant != null) builder.claim("merchant_id", merchant);
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        "verified", builder.build(), List.of(new SimpleGrantedAuthority("pay:product:admit"))));
    }

    @Test
    void onlyCustomerAudienceEnablesCredentialDelegation() {
        authenticated("synthetic", List.of("product"), "MERCHANT_APP", "merchant");
        assertNull(provider.current().customerAccessToken());
        authenticated("synthetic", List.of("product", "customer"), "MERCHANT_APP", "merchant");
        assertEquals("synthetic", provider.current().customerAccessToken());
    }

    @Test
    void tokenRotationDoesNotChangeTheOriginalCallerScopeAndDiagnosticsHideTokens() {
        authenticated("synthetic-secret-v1", List.of("product", "customer"), "MERCHANT_APP", "merchant");
        var first = provider.current();
        assertFalse(first.toString().contains("synthetic-secret-v1"));
        authenticated("synthetic-secret-v2", List.of("product", "customer"), "MERCHANT_APP", "merchant");
        assertEquals(first.scopeHash(), provider.current().scopeHash());
    }

    @Test
    void missingMerchantBindingAndForgedMerchantIdentityAreRejected() {
        authenticated("synthetic", List.of("product"), "MERCHANT_APP", null);
        assertThrows(CommonException.class, provider::current);
        authenticated("synthetic", List.of("product"), "MERCHANT", "another-merchant");
        assertThrows(CommonException.class, provider::current);
    }

    @Test
    void anonymousInvocationIsRejected() {
        assertThrows(CommonException.class, provider::current);
    }
}
