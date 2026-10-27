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
import io.github.loadup.pay.acquire.app.security.CashierActor;
import io.github.loadup.pay.acquire.domain.gateway.CheckoutSessionGateway;
import io.github.loadup.pay.acquire.domain.model.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CashierAccessTest {
    final CheckoutSessionGateway sessions = mock(CheckoutSessionGateway.class);
    final CashierAccess access = new CashierAccess(sessions, clock(NOW));
    final StoredAcquireOrder order = order(AcquireOrderState.PENDING, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10));
    final StoredCheckoutSession active = session(CheckoutSessionState.ACTIVE, NOW.plusMinutes(5));
    final CashierActor actor = new CashierActor("tenant", "session", "order", active.session().expiresAt());

    @Test void originalSessionAndOwnerMustMatchBeforeAReservation() {
        when(sessions.lock("tenant", "session")).thenReturn(Optional.of(active));
        new OrderCreationTransactions(new UnitTransactions()).writeLocal("tenant", () -> {
            access.authorize(actor, OWNER, order, true);
            assertThrows(CommonException.class, () -> access.authorize(actor, "x".repeat(64), order, true));
            return true;
        });
        verify(sessions, atLeastOnce()).lock("tenant", "session");
    }
    @Test void expiredOrCancelledSessionCannotCreateButCommittedIntentsRemainBoundForCompletion() {
        var cancelled = session(CheckoutSessionState.CANCELLED, NOW.plusMinutes(5));
        when(sessions.lock("tenant", "session")).thenReturn(Optional.of(cancelled));
        new OrderCreationTransactions(new UnitTransactions()).writeLocal("tenant", () -> {
            assertThrows(CommonException.class, () -> access.authorize(actor, OWNER, order, true));
            access.authorize(actor, OWNER, order, false);
            return true;
        });
        when(sessions.find("tenant", "session")).thenReturn(Optional.of(cancelled));
        assertThrows(CommonException.class, () -> access.read(actor, order, "PAYMENT_PROGRESS"));
    }
    @Test void validCapabilityCannotChangeTheOrderOrTheStoredExpiry() {
        when(sessions.find("tenant", "session")).thenReturn(Optional.of(active));
        assertThrows(CommonException.class, () -> access.read(new CashierActor("tenant", "session", "other", actor.expiresAt()), order, "ORDER_SUMMARY"));
        assertThrows(CommonException.class, () -> access.read(new CashierActor("tenant", "session", "order", actor.expiresAt().plusSeconds(1)), order, "ORDER_SUMMARY"));
    }

    @Test void revocationBlocksNewReservationsAndReadsButPreservesCommittedIntentCompletion() {
        var revoked = active.revoke(0L, NOW);
        when(sessions.lock("tenant", "session")).thenReturn(Optional.of(revoked));
        new OrderCreationTransactions(new UnitTransactions()).writeLocal("tenant", () -> {
            assertThrows(CommonException.class, () -> access.authorize(actor, OWNER, order, true));
            access.authorize(actor, OWNER, order, false);
            return true;
        });
        when(sessions.find("tenant", "session")).thenReturn(Optional.of(revoked));
        for (var permission : revoked.scope().permissions()) {
            assertThrows(CommonException.class, () -> access.read(actor, order, permission));
        }
    }
}
