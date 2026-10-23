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
package io.github.loadup.pay.acquire.adapter.security;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.pay.acquire.app.security.AcquireActor;
import io.github.loadup.pay.acquire.app.security.AcquireActorProvider;
import io.github.loadup.pay.common.DomainChecks;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;

@Component
public class JwtAcquireActorProvider implements AcquireActorProvider {
    private final String productAudience;

    public JwtAcquireActorProvider(@Value("${loadup.modules.acquire.product-audience}") String productAudience) {
        if (productAudience.isBlank()) throw new IllegalArgumentException("Product JWT audience is required");
        this.productAudience = productAudience;
    }

    @Override
    public AcquireActor current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getCredentials() instanceof Jwt jwt)) throw forbidden();
        try {
            String tenant = jwt.getClaimAsString("tenant_id"), type = jwt.getClaimAsString("principal_type");
            String principal = jwt.getSubject(), merchant = jwt.getClaimAsString("merchant_id");
            DomainChecks.id(tenant);
            DomainChecks.text(principal, 128);
            if (!tenant.equals(tenant.trim())
                    || !Set.of("MERCHANT_APP", "MERCHANT", "OPERATOR").contains(type)) throw forbidden();
            if (merchant != null) DomainChecks.id(merchant);
            if (!"OPERATOR".equals(type) && merchant == null) throw forbidden();
            if ("MERCHANT".equals(type) && !principal.equals(merchant)) throw forbidden();
            // Same immutable principal tuple as product; bearer token rotations do not change ownership.
            String scope = HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(JsonUtil.toJson(Arrays.asList(tenant, type, principal, merchant))
                                    .getBytes(StandardCharsets.UTF_8)));
            String delegated = jwt.getAudience() != null && jwt.getAudience().contains(productAudience)
                    ? jwt.getTokenValue()
                    : null;
            return new AcquireActor(
                    tenant,
                    scope,
                    delegated,
                    auth.getAuthorities().stream().map(a -> a.getAuthority()).collect(Collectors.toUnmodifiableSet()));
        } catch (RuntimeException invalid) {
            throw forbidden();
        } catch (NoSuchAlgorithmException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }

    private CommonException forbidden() {
        return new CommonException(ACTION_FORBIDDEN);
    }
}
