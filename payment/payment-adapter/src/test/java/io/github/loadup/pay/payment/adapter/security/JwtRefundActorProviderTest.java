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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtRefundActorProviderTest {
    final JwtRefundActorProvider provider = new JwtRefundActorProvider();

    void authenticate(String type, String subject, String merchant, String tenant) {
        var jwt = Jwt.withTokenValue("synthetic").header("alg", "RS256").issuer("https://issuer.example")
                .subject(subject).claim("principal_type", type).claim("merchant_id", merchant).claim("tenant_id", tenant).build();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("verified", jwt,
                List.of(new SimpleGrantedAuthority("pay:payment:refund:create"))));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void merchantAppUsesOnlyItsSignedMerchantClaimAndGrantedAuthorities() {
        authenticate("MERCHANT_APP", "app", "merchant", "tenant");
        var actor = provider.current();
        assertEquals("merchant", actor.merchantId());
        assertEquals("tenant", actor.tenantId());
        assertTrue(actor.subjectHash().matches("[0-9a-f]{64}"));
        assertTrue(actor.permissions().contains("pay:payment:refund:create"));
        assertFalse(actor.permissions().contains("pay:payment:refund:read"));
    }

    @Test
    void merchantSubjectMustMatchItsMerchantWhileServiceAndOperatorCannotRefund() {
        authenticate("MERCHANT", "other", "merchant", "tenant");
        assertThrows(CommonException.class, provider::current);
        authenticate("SERVICE", "merchant", "merchant", "tenant");
        assertThrows(CommonException.class, provider::current);
        authenticate("OPERATOR", "merchant", "merchant", "tenant");
        assertThrows(CommonException.class, provider::current);
    }

    @Test
    void missingMerchantOrUnnormalizedTenantCannotChooseAnOwnerFromRequestBody() {
        authenticate("MERCHANT_APP", "app", null, "tenant");
        assertThrows(CommonException.class, provider::current);
        authenticate("MERCHANT_APP", "app", "merchant", " tenant");
        assertThrows(CommonException.class, provider::current);
    }

    @Test
    void unsignedHeaderOrUnauthenticatedContextCannotEnterTheMerchantProtocol() {
        assertThrows(CommonException.class, provider::current);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("merchant", "raw", List.of()));
        assertThrows(CommonException.class, provider::current);
    }

    @Test
    void credentialRotationKeepsTheSameInitiatingSubjectAuditHash() {
        authenticate("MERCHANT_APP", "app", "merchant", "tenant");
        var first = provider.current().subjectHash();
        authenticate("MERCHANT_APP", "app", "merchant", "tenant");
        assertEquals(first, provider.current().subjectHash());
        authenticate("MERCHANT_APP", "other-app", "merchant", "tenant");
        assertNotEquals(first, provider.current().subjectHash());
    }
}
