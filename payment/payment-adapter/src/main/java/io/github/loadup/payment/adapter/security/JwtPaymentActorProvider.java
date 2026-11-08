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
package io.github.loadup.payment.adapter.security;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.contracts.payment.PaymentResultCodeEnum;
import io.github.loadup.payment.app.security.PaymentActor;
import io.github.loadup.payment.app.security.PaymentActorProvider;
import java.util.stream.Collectors;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class JwtPaymentActorProvider implements PaymentActorProvider {
    private static CommonException forbidden() {
        return new CommonException(PaymentResultCodeEnum.ACTION_FORBIDDEN);
    }

    @Override
    public PaymentActor current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getCredentials() instanceof Jwt jwt)) {
            throw forbidden();
        }
        try {
            String tenant = jwt.getClaimAsString("tenant_id");
            String service = jwt.getClaimAsString("service_id");
            if (!"SERVICE".equals(jwt.getClaimAsString("principal_type"))
                    || tenant == null
                    || service == null
                    || !tenant.equals(tenant.trim())
                    || !service.equals(service.trim())
                    || !service.equals(jwt.getSubject())) {
                throw forbidden();
            }
            return new PaymentActor(
                    tenant,
                    service,
                    authentication.getAuthorities().stream()
                            .map(authority -> authority.getAuthority())
                            .collect(Collectors.toUnmodifiableSet()));
        } catch (IllegalArgumentException invalid) {
            throw forbidden();
        }
    }
}
