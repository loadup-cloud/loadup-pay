/*
 * Copyright (C) 2026 LoadUp Cloud
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
package io.github.loadup.productcenter.adapter.security;

import io.github.loadup.commons.enums.CommonResultCodeEnum;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.commons.validation.DomainChecks;
import java.util.function.Supplier;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

public final class AuthenticatedTenant {
    private AuthenticatedTenant() {}

    public static <T> T call(Authentication authentication, Supplier<T> action) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getCredentials() instanceof Jwt jwt)) {
            throw new CommonException(CommonResultCodeEnum.UNAUTHENTICATED);
        }
        String tenant = jwt.getClaimAsString("tenant_id");
        DomainChecks.id(tenant);
        if (!tenant.equals(tenant.trim())) {
            throw new CommonException(CommonResultCodeEnum.ACCESS_DENIED);
        }
        return TenantUtil.callWithTenant(tenant, action::get);
    }
}
