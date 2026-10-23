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
package io.github.loadup.pay.realtime.adapter.security;

import io.github.loadup.commons.error.CommonException;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JwtRealtimeActorProviderTest {
    final JwtRealtimeActorProvider provider = new JwtRealtimeActorProvider();
    void jwt(String type, String subject, String service, String merchant) {
        var value = Jwt.withTokenValue("synthetic").header("alg", "RS256").subject(subject).claim("tenant_id", "tenant").claim("principal_type", type);
        if (service != null) value.claim("service_id", service);
        if (merchant != null) value.claim("merchant_id", merchant);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("verified", value.build(),
                List.of(new SimpleGrantedAuthority("pay:realtime:trade:read"))));
    }
    @AfterEach void close() { SecurityContextHolder.clearContext(); }
    @Test void merchantIdentityComesFromVerifiedClaims() {
        jwt("MERCHANT_APP", "app", null, "merchant");
        assertEquals("merchant", provider.current().merchantId()); assertNull(provider.current().serviceId());
    }
    @Test void merchantSubjectMustMatchItsOwnMerchant() {
        jwt("MERCHANT", "other-merchant", null, "merchant");
        assertThrows(CommonException.class, provider::current);
    }
    @Test void servicePrincipalHasNoImplicitMerchantQueryScope() {
        jwt("SERVICE", "payment", "payment", "merchant");
        assertEquals("payment", provider.current().serviceId()); assertNull(provider.current().merchantId());
    }
    @Test void mismatchedServiceAndUnscopedOperatorAreRejected() {
        jwt("SERVICE", "other-service", "payment", null); assertThrows(CommonException.class, provider::current);
        jwt("OPERATOR", "operator", null, null); assertThrows(CommonException.class, provider::current);
    }
    @Test void missingAuthenticationDoesNotAuthorizeAReadModel() {
        assertThrows(CommonException.class, provider::current);
    }
}
