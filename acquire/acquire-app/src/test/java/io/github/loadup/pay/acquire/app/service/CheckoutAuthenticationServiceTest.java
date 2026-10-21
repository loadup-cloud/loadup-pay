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
package io.github.loadup.pay.acquire.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.acquire.app.port.CheckoutTokens;
import io.github.loadup.pay.acquire.domain.gateway.CheckoutSessionGateway;
import io.github.loadup.pay.acquire.domain.model.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CheckoutAuthenticationServiceTest {
    final CheckoutSessionGateway sessions = mock(CheckoutSessionGateway.class);
    final CheckoutTokens tokens = mock(CheckoutTokens.class);
    final CheckoutAuthenticationService service = new CheckoutAuthenticationService(
            sessions, tokens, new OrderCreationTransactions(new UnitTransactions()), clock(NOW));

    @BeforeEach
    void setup() {
        when(sessions.find("tenant", "session"))
                .thenReturn(Optional.of(session(CheckoutSessionState.ACTIVE, NOW.plusMinutes(5))));
        when(tokens.matches(eq(TOKEN), any())).thenReturn(true);
    }

    @Test
    void checksTheCapabilityBeforeReturningAnOrderOnlyActor() {
        var actor = service.authenticate("tenant", TOKEN, ORIGIN);
        assertEquals("order", actor.orderId());
        assertEquals("session", actor.sessionId());
        assertEquals("tenant", actor.tenantId());
    }

    @Test
    void tenantLocatorAloneCannotSwitchTheAuthenticatedOrderScope() {
        when(sessions.find("other", "session")).thenReturn(Optional.empty());
        assertThrows(CommonException.class, () -> service.authenticate("other", TOKEN, ORIGIN));
        when(tokens.matches(eq(TOKEN), any())).thenReturn(false);
        assertThrows(CommonException.class, () -> service.authenticate("tenant", TOKEN, ORIGIN));
    }

    @Test
    void rejectsExactExpiryAndCancelledExpiredOrUnactivatedSessions() {
        for (var state :
                List.of(CheckoutSessionState.INIT, CheckoutSessionState.CANCELLED, CheckoutSessionState.EXPIRED)) {
            when(sessions.find("tenant", "session")).thenReturn(Optional.of(session(state, NOW.plusMinutes(5))));
            assertThrows(CommonException.class, () -> service.authenticate("tenant", TOKEN, ORIGIN));
        }
        var boundary = NOW.withNano(0);
        var expired = session(CheckoutSessionState.ACTIVE, boundary);
        when(sessions.find("tenant", "session")).thenReturn(Optional.of(expired));
        var atCutoff = new CheckoutAuthenticationService(
                sessions, tokens, new OrderCreationTransactions(new UnitTransactions()), clock(boundary));
        assertThrows(CommonException.class, () -> atCutoff.authenticate("tenant", TOKEN, ORIGIN));
    }

    @Test
    void completedSessionsRemainReadOnlyUntilTheirOriginalExpiry() {
        when(sessions.find("tenant", "session"))
                .thenReturn(Optional.of(session(CheckoutSessionState.COMPLETED, NOW.plusMinutes(5))));
        assertEquals("order", service.authenticate("tenant", TOKEN, ORIGIN).orderId());
    }

    @Test
    void aBrowserOriginMustMatchAndMalformedCredentialsDoNotTriggerALookup() {
        assertThrows(CommonException.class, () -> service.authenticate("tenant", TOKEN, "https://evil.example"));
        clearInvocations(sessions);
        assertThrows(CommonException.class, () -> service.authenticate("tenant", "arbitrary", null));
        assertThrows(CommonException.class, () -> service.authenticate(" tenant", TOKEN, null));
        verifyNoInteractions(sessions);
    }
}
