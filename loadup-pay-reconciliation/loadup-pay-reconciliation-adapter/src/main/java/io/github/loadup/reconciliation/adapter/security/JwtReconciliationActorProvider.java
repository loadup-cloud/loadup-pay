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

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.database.tenant.DatabaseTenant;
import io.github.loadup.reconciliation.app.security.ReconciliationActor;
import io.github.loadup.reconciliation.app.security.ReconciliationActorProvider;
import java.util.stream.Collectors;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Consumes the resource server's verified authentication; never parses an unverified bearer token.
 */
@Component
public class JwtReconciliationActorProvider implements ReconciliationActorProvider {
    private final DatabaseTenant databaseTenant;

    public JwtReconciliationActorProvider(DatabaseTenant databaseTenant) {
        this.databaseTenant = databaseTenant;
    }

    @Override
    public ReconciliationActor current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getCredentials() instanceof Jwt jwt)) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        try {
            String tenant = databaseTenant.currentTenantId(), service = jwt.getClaimAsString("service_id");
            if (!"SERVICE".equals(jwt.getClaimAsString("principal_type"))
                    || service == null
                    || !service.equals(service.trim())
                    || !service.equals(jwt.getSubject())) {
                throw new CommonException(ACTION_FORBIDDEN);
            }
            var permissions = auth.getAuthorities().stream()
                    .map(authority -> authority.getAuthority())
                    .collect(Collectors.toUnmodifiableSet());
            return new ReconciliationActor(tenant, service, permissions);
        } catch (RuntimeException invalid) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
    }
}
