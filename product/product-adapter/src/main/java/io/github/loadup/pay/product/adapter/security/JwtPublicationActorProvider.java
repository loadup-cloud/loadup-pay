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
import io.github.loadup.pay.product.app.security.PublicationActor;
import io.github.loadup.pay.product.app.security.PublicationActorProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.ACTION_FORBIDDEN;

@Component
public class JwtPublicationActorProvider implements PublicationActorProvider {
    private static CommonException forbidden() {
        return new CommonException(ACTION_FORBIDDEN);
    }

    @Override
    public PublicationActor current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getCredentials() instanceof Jwt jwt)) {
            throw forbidden();
        }
        try {
            String tenant = jwt.getClaimAsString("tenant_id");
            String type = jwt.getClaimAsString("principal_type");
            if (tenant == null
                    || !tenant.equals(tenant.trim())
                    || type == null
                    || !Set.of("SERVICE", "OPERATOR").contains(type)) {
                throw forbidden();
            }
            if ("SERVICE".equals(type)
                    && (jwt.getSubject() == null || !jwt.getSubject().equals(jwt.getClaimAsString("service_id")))) {
                throw forbidden();
            }
            return new PublicationActor(
                    tenant,
                    jwt.getSubject(),
                    auth.getAuthorities().stream()
                            .map(authority -> authority.getAuthority())
                            .collect(Collectors.toUnmodifiableSet()));
        } catch (IllegalArgumentException invalid) {
            throw forbidden();
        }
    }
}
