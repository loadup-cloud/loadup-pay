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
import io.github.loadup.pay.product.app.security.ProductActor;
import io.github.loadup.pay.product.app.security.ProductActorProvider;
import io.github.loadup.pay.product.client.error.ProductResultCodeEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class JwtProductActorProvider implements ProductActorProvider {
    private final String customerAudience;

    public JwtProductActorProvider(@Value("${loadup.modules.product.customer-audience}") String customerAudience) {
        if (customerAudience.isBlank()) {
            throw new IllegalArgumentException("Customer JWT audience is required");
        }
        this.customerAudience = customerAudience;
    }

    private static CommonException forbidden() {
        return new CommonException(ProductResultCodeEnum.ACTION_FORBIDDEN);
    }

    @Override
    public ProductActor current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getCredentials() instanceof Jwt jwt)) {
            throw forbidden();
        }
        try {
            String tenant = jwt.getClaimAsString("tenant_id");
            String type = jwt.getClaimAsString("principal_type");
            String merchant = jwt.getClaimAsString("merchant_id");
            if (tenant == null
                    || !tenant.equals(tenant.trim())
                    || type == null
                    || jwt.getSubject() == null
                    || !Set.of("MERCHANT_APP", "MERCHANT", "OPERATOR").contains(type)) {
                throw forbidden();
            }
            if (!"OPERATOR".equals(type) && (merchant == null || merchant.isBlank())) {
                throw forbidden();
            }
            if ("MERCHANT".equals(type) && !jwt.getSubject().equals(merchant)) {
                throw forbidden();
            }
            String delegated = jwt.getAudience() != null && jwt.getAudience().contains(customerAudience)
                    ? jwt.getTokenValue()
                    : null;
            return new ProductActor(
                    tenant,
                    type,
                    jwt.getSubject(),
                    merchant,
                    delegated,
                    authentication.getAuthorities().stream()
                            .map(authority -> authority.getAuthority())
                            .collect(Collectors.toUnmodifiableSet()));
        } catch (IllegalArgumentException invalid) {
            throw forbidden();
        }
    }
}
