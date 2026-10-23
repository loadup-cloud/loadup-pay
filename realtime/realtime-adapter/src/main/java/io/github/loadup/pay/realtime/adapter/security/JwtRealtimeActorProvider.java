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
package io.github.loadup.pay.realtime.adapter.security;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.realtime.app.security.RealtimeActor;
import io.github.loadup.pay.realtime.app.security.RealtimeActorProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.QUERY_FORBIDDEN;

@Component
public class JwtRealtimeActorProvider implements RealtimeActorProvider {
    @Override
    public RealtimeActor current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getCredentials() instanceof Jwt jwt))
            throw new CommonException(QUERY_FORBIDDEN);
        try {
            String tenant = jwt.getClaimAsString("tenant_id"), type = jwt.getClaimAsString("principal_type");
            String subject = jwt.getSubject();
            if (tenant == null || !tenant.equals(tenant.trim()) || subject == null)
                throw new CommonException(QUERY_FORBIDDEN);
            var permissions = auth.getAuthorities().stream().map(authority -> authority.getAuthority()).collect(Collectors.toUnmodifiableSet());
            if ("SERVICE".equals(type)) {
                String service = jwt.getClaimAsString("service_id");
                if (!subject.equals(service) || !service.equals(service.trim()))
                    throw new CommonException(QUERY_FORBIDDEN);
                return new RealtimeActor(tenant, service, null, permissions);
            }
            if (!Set.of("MERCHANT", "MERCHANT_APP").contains(type)) throw new CommonException(QUERY_FORBIDDEN);
            String merchant = jwt.getClaimAsString("merchant_id");
            if (merchant == null || !merchant.equals(merchant.trim()) || "MERCHANT".equals(type) && !subject.equals(merchant))
                throw new CommonException(QUERY_FORBIDDEN);
            return new RealtimeActor(tenant, null, merchant, permissions);
        } catch (RuntimeException invalid) {
            throw new CommonException(QUERY_FORBIDDEN);
        }
    }
}
