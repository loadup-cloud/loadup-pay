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
import io.github.loadup.pay.acquire.app.security.CashierActor;
import io.github.loadup.pay.acquire.app.service.CheckoutAuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RequestCashierActorProviderTest {
    final HttpServletRequest request = mock(HttpServletRequest.class);
    final CheckoutAuthenticationService authentication = mock(CheckoutAuthenticationService.class);
    final RequestCashierActorProvider provider = new RequestCashierActorProvider(request, authentication);

    void header(String name, String... values) {
        when(request.getHeaders(name)).thenAnswer(c -> Collections.enumeration(List.of(values)));
    }

    @BeforeEach
    void setup() {
        header("Authorization");
        header("X-Checkout-Tenant");
        header("Origin");
    }

    @Test
    void passesOnlyExplicitCapabilityHeadersIntoAuthenticationAndIgnoresRequestParameters() {
        header("Authorization", "Bearer opaque-capability");
        header("X-Checkout-Tenant", "tenant");
        var actor = new CashierActor("tenant", "session", "order", LocalDateTime.of(2026, 10, 21, 12, 5));
        when(authentication.authenticate("tenant", "opaque-capability", null)).thenReturn(actor);
        assertSame(actor, provider.current());
        verify(request, never()).getParameter(anyString());
    }

    @Test
    void duplicateAuthorizationOrTenantHeadersCannotSelectAnotherIdentity() {
        header("Authorization", "Bearer one", "Bearer two");
        header("X-Checkout-Tenant", "tenant");
        assertThrows(CommonException.class, provider::current);
        verifyNoInteractions(authentication);
        header("Authorization", "Bearer one");
        header("X-Checkout-Tenant", "one", "two");
        assertThrows(CommonException.class, provider::current);
        verifyNoInteractions(authentication);
    }

    @Test
    void anonymousOrCookieOnlyRequestsNeverReachTheScopedFacade() {
        assertThrows(CommonException.class, provider::current);
        verifyNoInteractions(authentication);
    }

    @Test
    void unsupportedAuthorizationSchemeOrOversizedHeadersAreRejectedWithoutEcho() {
        header("Authorization", "Basic synthetic-secret");
        assertThrows(CommonException.class, provider::current);
        header("Authorization", "Bearer " + "a".repeat(129));
        assertThrows(CommonException.class, provider::current);
        verifyNoInteractions(authentication);
    }
}
