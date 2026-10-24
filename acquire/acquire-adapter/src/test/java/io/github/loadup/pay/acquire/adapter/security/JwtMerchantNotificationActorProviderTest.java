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
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JwtMerchantNotificationActorProviderTest {
    final JwtMerchantNotificationActorProvider provider = new JwtMerchantNotificationActorProvider();
    void authenticate(String type, String subject, String merchant, String service) {
        var jwt = Jwt.withTokenValue("synthetic-token").header("alg", "RS256").subject(subject).claim("tenant_id", "tenant")
                .claim("principal_type", type);
        if (merchant != null) jwt.claim("merchant_id", merchant);
        if (service != null) jwt.claim("service_id", service);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("verified", jwt.build(),
                List.of(new SimpleGrantedAuthority("pay:acquire:notification:read"))));
    }
    @AfterEach void close() { SecurityContextHolder.clearContext(); }
    @Test void merchantScopeComesOnlyFromTheVerifiedJwtAndNeverExposesTheToken() {
        authenticate("MERCHANT_APP", "app-a", "merchant-a", null);
        var actor = provider.current();
        assertEquals("merchant-a", actor.merchantId());
        assertEquals("app-a", actor.principalId());
        assertTrue(actor.permissions().contains("pay:acquire:notification:read"));
        assertFalse(actor.toString().contains("synthetic-token"));
    }
    @Test void operatorAndServiceHaveNoMerchantImpersonationScope() {
        authenticate("OPERATOR", "operator-a", null, null);
        assertFalse(provider.current().merchant());
        authenticate("SERVICE", "pay-ops", null, "pay-ops");
        assertEquals("SERVICE", provider.current().principalType());
        authenticate("OPERATOR", "operator-a", "merchant-a", null);
        assertThrows(CommonException.class, provider::current);
    }
    @Test void absentOrAmbiguousClaimsCannotCreateANotificationActor() {
        assertThrows(CommonException.class, provider::current);
        authenticate("MERCHANT", "other", "merchant", null);
        assertThrows(CommonException.class, provider::current);
        authenticate("MERCHANT_APP", "app", null, null);
        assertThrows(CommonException.class, provider::current);
        authenticate("SERVICE", "pay-ops", null, "other-service");
        assertThrows(CommonException.class, provider::current);
        authenticate("MERCHANT", " merchant", " merchant", null);
        assertThrows(CommonException.class, provider::current);
    }
}
