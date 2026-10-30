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
package io.github.loadup.pay.acquire.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_STATE;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Token generation and hashing belong to the security adapter, not this aggregate.
 */
public record CheckoutSession(String id, String orderId, CheckoutSessionState state, LocalDateTime expiresAt) {
    public CheckoutSession {
        DomainChecks.id(id);
        DomainChecks.id(orderId);
        Objects.requireNonNull(state);
        Objects.requireNonNull(expiresAt);
    }

    public static CheckoutSession init(String id, String orderId, LocalDateTime expiresAt) {
        return new CheckoutSession(id, orderId, CheckoutSessionState.INIT, expiresAt);
    }

    public boolean permits(String requestedOrderId, LocalDateTime now) {
        return state == CheckoutSessionState.ACTIVE && orderId.equals(requestedOrderId) && now.isBefore(expiresAt);
    }

    public CheckoutSession activate(LocalDateTime now) {
        Objects.requireNonNull(now);
        require(now.isBefore(expiresAt), INVALID_STATE);
        if (state == CheckoutSessionState.ACTIVE) {
            return this;
        }
        require(state == CheckoutSessionState.INIT, INVALID_STATE);
        return new CheckoutSession(id, orderId, CheckoutSessionState.ACTIVE, expiresAt);
    }

    public CheckoutSession expire(LocalDateTime now) {
        Objects.requireNonNull(now);
        if (terminal() || now.isBefore(expiresAt)) {
            return this;
        }
        return new CheckoutSession(id, orderId, CheckoutSessionState.EXPIRED, expiresAt);
    }

    public CheckoutSession complete(AcquireOrder order) {
        require(orderId.equals(order.id()) && order.state() == AcquireOrderState.PAID, CONFLICT);
        if (state == CheckoutSessionState.COMPLETED) {
            return this;
        }
        require(!terminal(), INVALID_STATE);
        return new CheckoutSession(id, orderId, CheckoutSessionState.COMPLETED, expiresAt);
    }

    public CheckoutSession cancel() {
        if (state == CheckoutSessionState.CANCELLED) {
            return this;
        }
        require(!terminal(), INVALID_STATE);
        return new CheckoutSession(id, orderId, CheckoutSessionState.CANCELLED, expiresAt);
    }

    private boolean terminal() {
        return state == CheckoutSessionState.COMPLETED
                || state == CheckoutSessionState.CANCELLED
                || state == CheckoutSessionState.EXPIRED;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
