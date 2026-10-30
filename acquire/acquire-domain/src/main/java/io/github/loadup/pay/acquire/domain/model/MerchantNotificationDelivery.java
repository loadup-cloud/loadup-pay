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

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_STATE;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * A stable event/body with an independent delivery cursor; attempts confer no financial authority.
 */
public record MerchantNotificationDelivery(String id, String eventId, String orderId, String payeeMerchantId,
                                           String eventType, String aggregateId, long sourceVersion,
                                           @DiagnosticHidden String body,
                                           String bodyDigest, MerchantNotificationPolicy policy,
                                           LocalDateTime occurredAt, LocalDateTime expiresAt,
                                           LocalDateTime nextAttemptAt, int attemptCount,
                                           MerchantNotificationState state,
                                           String routeFingerprint, LocalDateTime acknowledgedAt, long rowVersion) {
    public MerchantNotificationDelivery {
        DomainChecks.id(id);
        DomainChecks.id(eventId);
        DomainChecks.id(orderId);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.id(aggregateId);
        DomainChecks.text(eventType, 64);
        DomainChecks.text(body, 65536);
        Objects.requireNonNull(policy);
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(expiresAt);
        Objects.requireNonNull(nextAttemptAt);
        Objects.requireNonNull(state);
        require(sourceVersion > 0 && rowVersion >= 0 && attemptCount >= 0 && attemptCount <= policy.retryMaxAttempts()
                && bodyDigest != null && bodyDigest.matches("[0-9a-f]{64}") && expiresAt.isAfter(occurredAt)
                && (routeFingerprint == null || routeFingerprint.matches("[0-9a-f]{64}")), INVALID_ARGUMENT);
        require((state == MerchantNotificationState.ACKED) == (acknowledgedAt != null), INVALID_ARGUMENT);
        require(state != MerchantNotificationState.INIT || attemptCount == 0, INVALID_ARGUMENT);
        require(!nextAttemptAt.isBefore(occurredAt) && !nextAttemptAt.isAfter(expiresAt)
                && expiresAt.equals(occurredAt.plusSeconds(policy.retryWindowSeconds())), INVALID_ARGUMENT);
        require(acknowledgedAt == null || !acknowledgedAt.isBefore(occurredAt) && acknowledgedAt.isBefore(expiresAt), INVALID_ARGUMENT);
    }

    public static MerchantNotificationDelivery init(String id, String eventId, String orderId, String merchant,
                                                    String eventType, String aggregateId, long sourceVersion, String body, String digest,
                                                    MerchantNotificationPolicy policy, LocalDateTime occurredAt) {
        return new MerchantNotificationDelivery(id, eventId, orderId, merchant, eventType, aggregateId, sourceVersion,
                body, digest, policy, occurredAt, occurredAt.plusSeconds(policy.retryWindowSeconds()), occurredAt,
                0, MerchantNotificationState.INIT, null, null, 0);
    }

    public boolean finished() {
        return state == MerchantNotificationState.ACKED || state == MerchantNotificationState.EXHAUSTED;
    }

    public boolean expired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public MerchantNotificationDelivery begin(LocalDateTime now, String fingerprint) {
        require(!finished() && !expired(now) && !now.isBefore(nextAttemptAt) && attemptCount < policy.retryMaxAttempts(), INVALID_STATE);
        int started = Math.incrementExact(attemptCount);
        // A successor is durable before HTTP. Crashes consume a permit, not a new event identity.
        var due = now.plusNanos(Math.multiplyExact((long) policy.timeoutMillis(), 1_000_000L)).plusSeconds(policy.delaySeconds(started));
        if (due.isAfter(expiresAt)) {
            due = expiresAt;
        }
        return copy(due, started, MerchantNotificationState.PENDING,
                routeFingerprint == null ? fingerprint : routeFingerprint, null);
    }

    public MerchantNotificationDelivery acknowledge(LocalDateTime now) {
        require(!finished() && !expired(now) && attemptCount > 0, INVALID_STATE);
        return copy(nextAttemptAt, attemptCount, MerchantNotificationState.ACKED, routeFingerprint, now);
    }

    public MerchantNotificationDelivery exhaust() {
        if (finished()) {
            return this;
        }
        return copy(nextAttemptAt, attemptCount, MerchantNotificationState.EXHAUSTED, routeFingerprint, null);
    }

    private MerchantNotificationDelivery copy(LocalDateTime next, int attempts, MerchantNotificationState newState, String route, LocalDateTime ack) {
        return new MerchantNotificationDelivery(id, eventId, orderId, payeeMerchantId, eventType, aggregateId, sourceVersion,
                body, bodyDigest, policy, occurredAt, expiresAt, next, attempts, newState, route, ack, Math.incrementExact(rowVersion));
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
