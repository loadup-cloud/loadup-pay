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
import io.github.loadup.pay.customer.app.security.CustomerActor;
import io.github.loadup.pay.customer.app.security.CustomerActorProvider;
import io.github.loadup.pay.customer.client.error.CustomerResultCodeEnum;
import io.github.loadup.pay.customer.domain.model.PrincipalType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class JwtCustomerActorProvider implements CustomerActorProvider {
    public static final String MANAGE_PERMISSION = "pay:customer:manage";

    private static CommonException denied() {
        return new CommonException(CustomerResultCodeEnum.ACTION_FORBIDDEN);
    }

    @Override
    public CustomerActor current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getCredentials() instanceof Jwt jwt)) throw denied();
        try {
            String tenant = jwt.getClaimAsString("tenant_id");
            String type = jwt.getClaimAsString("principal_type");
            String merchant = jwt.getClaimAsString("merchant_id");
            if (tenant == null || !tenant.equals(tenant.trim()) || type == null || jwt.getSubject() == null)
                throw denied();
            PrincipalType principalType = PrincipalType.valueOf(type);
            if (principalType != PrincipalType.OPERATOR && (merchant == null || merchant.isBlank())) throw denied();
            if (principalType == PrincipalType.MERCHANT && !jwt.getSubject().equals(merchant)) throw denied();
            boolean manage = authentication.getAuthorities().stream()
                    .anyMatch(authority -> MANAGE_PERMISSION.equals(authority.getAuthority()));
            return new CustomerActor(tenant, principalType, jwt.getSubject(), merchant, manage);
        } catch (IllegalArgumentException failure) {
            throw denied();
        }
    }
}
