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
package io.github.loadup.pay.reconciliation.adapter.security;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.reconciliation.app.security.ReconciliationActor;
import io.github.loadup.pay.reconciliation.app.security.ReconciliationActorProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import java.util.stream.Collectors;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.ACTION_FORBIDDEN;

/** Consumes the resource server's verified authentication; never parses an unverified bearer token. */
@Component
public class JwtReconciliationActorProvider implements ReconciliationActorProvider {
    @Override public ReconciliationActor current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getCredentials() instanceof Jwt jwt))
            throw new CommonException(ACTION_FORBIDDEN);
        try {
            String tenant = jwt.getClaimAsString("tenant_id"), service = jwt.getClaimAsString("service_id");
            if (!"SERVICE".equals(jwt.getClaimAsString("principal_type")) || tenant == null || !tenant.equals(tenant.trim())
                    || service == null || !service.equals(service.trim()) || !service.equals(jwt.getSubject()))
                throw new CommonException(ACTION_FORBIDDEN);
            var permissions = auth.getAuthorities().stream().map(authority -> authority.getAuthority()).collect(Collectors.toUnmodifiableSet());
            return new ReconciliationActor(tenant, service, permissions);
        } catch (RuntimeException invalid) { throw new CommonException(ACTION_FORBIDDEN); }
    }
}
