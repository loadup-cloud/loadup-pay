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
import io.github.loadup.pay.acquire.domain.model.CheckoutSessionState;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.acquire.domain.model.StoredCheckoutSession;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SESSION_UNAUTHORIZED;

@Component
public class CashierAccess {
    private final CheckoutSessionGateway sessions;
    private final Clock clock;

    public CashierAccess(CheckoutSessionGateway sessions, Clock clock) {
        this.sessions = sessions;
        this.clock = clock;
    }

    public StoredCheckoutSession read(CashierActor actor, StoredAcquireOrder order, String permission) {
        var session = bound(actor, order, permission);
        if (session.revokedAt() != null || !now().isBefore(session.session().expiresAt()) || !(session.session().state() == CheckoutSessionState.ACTIVE
                || session.session().state() == CheckoutSessionState.COMPLETED)) {
            throw new CommonException(SESSION_UNAUTHORIZED);
        }
        return session;
    }

    void authorize(CashierActor actor, String owner, StoredAcquireOrder order, boolean creating) {
        if (!owner.equals(order.callerScopeHash())) {
            throw new CommonException(SESSION_UNAUTHORIZED);
        }
        var stored = bound(actor, order, "PAYMENT_START");
        // A committed A2 intent may finish after capability expiry; only new reservations need a live session.
        if (creating && (stored.revokedAt() != null || !stored.session().permits(actor.orderId(), now()))) {
            throw new CommonException(SESSION_UNAUTHORIZED);
        }
    }

    private StoredCheckoutSession bound(CashierActor actor, StoredAcquireOrder order, String permission) {
        var stored = (TransactionSynchronizationManager.isActualTransactionActive() ? sessions.lock(actor.tenantId(), actor.sessionId())
                : sessions.find(actor.tenantId(), actor.sessionId())).orElseThrow(() -> new CommonException(SESSION_UNAUTHORIZED));
        if (!actor.sessionId().equals(stored.session().id()) || !actor.orderId().equals(stored.session().orderId())
                || !actor.orderId().equals(order.order().id()) || !actor.expiresAt().equals(stored.session().expiresAt())
                || !stored.payeeMerchantId().equals(order.order().payeeMerchantId()) || !stored.scope().callerScopeHash().equals(order.callerScopeHash())
                || !stored.scope().permissions().contains(permission)) {
            throw new CommonException(SESSION_UNAUTHORIZED);
        }
        return stored;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }
}
