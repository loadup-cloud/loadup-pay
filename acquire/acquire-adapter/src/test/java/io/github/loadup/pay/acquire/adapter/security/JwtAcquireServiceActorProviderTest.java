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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAcquireServiceActorProviderTest {
    final JwtAcquireServiceActorProvider provider = new JwtAcquireServiceActorProvider();

    void authenticate(String type, String tenant, String service, String subject) {
        var jwt = Jwt.withTokenValue("synthetic-service-token").header("alg", "RS256").subject(subject)
                .claim("principal_type", type).claim("tenant_id", tenant).claim("service_id", service).build();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("verified", jwt,
                List.of(new SimpleGrantedAuthority("pay:acquire:payment:outcome"))));
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void verifiedServiceClaimsAndAuthoritiesProduceOnlyAServiceActor() {
        authenticate("SERVICE", "tenant", "payment", "payment");
        var actor = provider.current();
        assertEquals("tenant", actor.tenantId());
        assertEquals("payment", actor.serviceId());
        assertTrue(actor.permissions().contains("pay:acquire:payment:outcome"));
        assertFalse(actor.toString().contains("synthetic-service-token"));
    }

    @Test
    void merchantTokenCannotMasqueradeAsThePaymentProducer() {
        authenticate("MERCHANT_APP", "tenant", "payment", "payment");
        assertThrows(CommonException.class, provider::current);
    }

    @Test
    void serviceClaimMustEqualTheVerifiedSubject() {
        authenticate("SERVICE", "tenant", "payment", "other-service");
        assertThrows(CommonException.class, provider::current);
    }

    @Test
    void tenantAndServiceWhitespaceAliasesAreRejected() {
        authenticate("SERVICE", " tenant", "payment", "payment");
        assertThrows(CommonException.class, provider::current);
        authenticate("SERVICE", "tenant", "payment ", "payment ");
        assertThrows(CommonException.class, provider::current);
    }

    @Test
    void absentAuthenticationCannotCreateATrustedProducer() {
        assertThrows(CommonException.class, provider::current);
    }
}
