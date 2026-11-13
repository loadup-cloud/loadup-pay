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
package io.github.loadup.reconciliation.adapter.security;

import static io.github.loadup.reconciliation.client.error.ReconciliationResultCodeEnum.ACTION_FORBIDDEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.database.config.DatabaseProperties;
import io.github.loadup.components.database.tenant.DatabaseTenant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtReconciliationActorProviderTest {
    private final JwtReconciliationActorProvider provider =
            new JwtReconciliationActorProvider(new DatabaseTenant(new DatabaseProperties()));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void onlyVerifiedServiceClaimsSupplyTenantCallerAndAuthorities() {
        install("SERVICE", "ops", "ops");
        var actor = provider.current();
        assertEquals("__default__", actor.tenantId());
        assertEquals("ops", actor.serviceId());
        assertEquals(Set.of("pay:reconciliation:batch:read"), actor.permissions());
    }

    @Test
    void missingUnauthenticatedAndNonJwtCredentialsAreRejected() {
        denied();
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated("ops", "bearer"));
        denied();
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated("ops", "bearer", List.of()));
        denied();
    }

    @Test
    void merchantsAndSelfReportedOrWhitespaceServiceIdentitiesCannotAcquireOperationsScope() {
        install("MERCHANT", "ops", "ops");
        denied();
        install("SERVICE", "other", "ops");
        denied();
        install("SERVICE", " ops ", " ops ");
        denied();
    }

    private void denied() {
        assertEquals(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, provider::current).getResultCode());
    }

    private void install(String type, String subject, String service) {
        var builder = Jwt.withTokenValue("synthetic")
                .header("alg", "RS256")
                .subject(subject)
                .claim("principal_type", type)
                .claim("service_id", service);
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                        "verified",
                        builder.build(),
                        List.of(new SimpleGrantedAuthority("pay:reconciliation:batch:read"))));
    }
}
