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
import io.github.loadup.pay.acquire.app.security.CashierActor;
import io.github.loadup.pay.acquire.domain.gateway.CheckoutSessionGateway;
import io.github.loadup.pay.acquire.domain.model.CheckoutSessionState;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SESSION_UNAUTHORIZED;

@Service
public class CheckoutAuthenticationService {
    private final CheckoutSessionGateway sessions;
    private final CheckoutTokens tokens;
    private final OrderCreationTransactions transactions;
    private final Clock clock;

    public CheckoutAuthenticationService(
            CheckoutSessionGateway sessions,
            CheckoutTokens tokens,
            OrderCreationTransactions transactions,
            @Qualifier("acquireClock") Clock clock) {
        this.sessions = sessions;
        this.tokens = tokens;
        this.transactions = transactions;
        this.clock = clock;
    }

    public CashierActor authenticate(String tenantLocator, String token, String origin) {
        // A tenant locator is only a bounded lookup hint. It grants no authority without this capability check.
        if (tenantLocator == null
                || !tenantLocator.equals(tenantLocator.trim())
                || tenantLocator.isBlank()
                || tenantLocator.length() > 64
                || tenantLocator.chars().anyMatch(Character::isISOControl)
                || token == null
                || token.length() > 108
                || !token.matches("[A-Za-z0-9_-]{1,64}\\.[A-Za-z0-9_-]{43}"))
            throw new CommonException(SESSION_UNAUTHORIZED);
        String sessionId = token.substring(0, token.indexOf('.'));
        return transactions.read(tenantLocator, () -> {
            var stored = sessions.find(tenantLocator, sessionId)
                    .orElseThrow(() -> new CommonException(SESSION_UNAUTHORIZED));
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), clock.getZone());
            var state = stored.session().state();
            if (!tokens.matches(token, stored)
                    || (state != CheckoutSessionState.ACTIVE && state != CheckoutSessionState.COMPLETED)
                    || !now.isBefore(stored.session().expiresAt())
                    || !stored.scope().permissions().contains("ORDER_SUMMARY")
                    || (origin != null && !origin.equals(stored.allowedOrigin())))
                throw new CommonException(SESSION_UNAUTHORIZED);
            return new CashierActor(
                    tenantLocator,
                    sessionId,
                    stored.session().orderId(),
                    stored.session().expiresAt());
        });
    }
}
