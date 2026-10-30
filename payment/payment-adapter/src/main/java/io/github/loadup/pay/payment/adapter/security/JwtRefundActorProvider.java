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
package io.github.loadup.pay.payment.adapter.security;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.payment.app.security.RefundActor;
import io.github.loadup.pay.payment.app.security.RefundActorProvider;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;

@Component
public class JwtRefundActorProvider implements RefundActorProvider {
    @Override
    public RefundActor current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getCredentials() instanceof Jwt jwt)) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        try {
            String tenant = jwt.getClaimAsString("tenant_id"), type = jwt.getClaimAsString("principal_type");
            String subject = jwt.getSubject(), merchant = jwt.getClaimAsString("merchant_id");
            if (!Set.of("MERCHANT", "MERCHANT_APP").contains(type) || jwt.getIssuer() == null
                    || tenant == null || !tenant.equals(tenant.trim()) || subject == null || subject.isBlank()
                    || merchant == null || !merchant.equals(merchant.trim())
                    || "MERCHANT".equals(type) && !subject.equals(merchant)) {
                throw new CommonException(ACTION_FORBIDDEN);
            }
            var framed = new StringBuilder();
            for (var value : List.of(jwt.getIssuer().toString(), type, subject, merchant)) {
                framed.append(value.getBytes(StandardCharsets.UTF_8).length).append(':').append(value);
            }
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(framed.toString().getBytes(StandardCharsets.UTF_8)));
            return new RefundActor(tenant, merchant, hash, auth.getAuthorities().stream()
                    .map(authority -> authority.getAuthority()).collect(Collectors.toUnmodifiableSet()));
        } catch (RuntimeException invalid) {
            throw new CommonException(ACTION_FORBIDDEN);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new AssertionError(unavailable);
        }
    }
}
