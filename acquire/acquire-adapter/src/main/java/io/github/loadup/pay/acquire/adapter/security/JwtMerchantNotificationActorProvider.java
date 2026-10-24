/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.acquire.adapter.security;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.acquire.app.security.MerchantNotificationActor;
import io.github.loadup.pay.acquire.app.security.MerchantNotificationActorProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.stream.Collectors;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;

@Component
public class JwtMerchantNotificationActorProvider implements MerchantNotificationActorProvider {
    @Override public MerchantNotificationActor current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getCredentials() instanceof Jwt jwt)) throw new CommonException(ACTION_FORBIDDEN);
        try {
            String tenant = jwt.getClaimAsString("tenant_id"), type = jwt.getClaimAsString("principal_type"), principal = jwt.getSubject();
            String merchant = jwt.getClaimAsString("merchant_id");
            if (tenant == null || !tenant.equals(tenant.trim()) || principal == null || !principal.equals(principal.trim()) || type == null)
                throw new CommonException(ACTION_FORBIDDEN);
            if (merchant != null && !merchant.equals(merchant.trim())) throw new CommonException(ACTION_FORBIDDEN);
            if ("SERVICE".equals(type) && !principal.equals(jwt.getClaimAsString("service_id"))) throw new CommonException(ACTION_FORBIDDEN);
            if (!Set.of("MERCHANT", "MERCHANT_APP", "OPERATOR", "SERVICE").contains(type)) throw new CommonException(ACTION_FORBIDDEN);
            return new MerchantNotificationActor(tenant, type, principal, merchant,
                    auth.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toUnmodifiableSet()));
        } catch (RuntimeException invalid) { throw new CommonException(ACTION_FORBIDDEN); }
    }
}
