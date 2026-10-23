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
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Immutable evidence of one applied deadline change and its original optimistic precondition.
 */
public record OrderExtensionReceipt(
        String id, String orderId, String callerScopeHash, String requestKey, String requestDigest,
        LocalDateTime originalDueAt, LocalDateTime previousDueAt, LocalDateTime requestedDueAt,
        long expectedOrderVersion, long deadlineVersion, LocalDateTime appliedAt) {
    public OrderExtensionReceipt {
        DomainChecks.id(id);
        DomainChecks.id(orderId);
        DomainChecks.text(requestKey, 128);
        Objects.requireNonNull(originalDueAt);
        Objects.requireNonNull(previousDueAt);
        Objects.requireNonNull(requestedDueAt);
        Objects.requireNonNull(appliedAt);
        require(callerScopeHash != null && callerScopeHash.matches("[0-9a-f]{64}")
                && requestDigest != null && requestDigest.matches("[0-9a-f]{64}")
                && expectedOrderVersion >= 0 && deadlineVersion > 1
                && !originalDueAt.isAfter(previousDueAt)
                && requestedDueAt.isAfter(previousDueAt) && requestedDueAt.isAfter(appliedAt), INVALID_ARGUMENT);
        for (var time : java.util.List.of(originalDueAt, previousDueAt, requestedDueAt, appliedAt))
            require(time.getYear() >= 1970 && time.getYear() <= 9998 && time.getNano() % 1000 == 0, INVALID_ARGUMENT);
    }

    public void requireSameRequest(String callerScope, String digest, long expectedVersion, LocalDateTime due) {
        require(callerScopeHash.equals(callerScope) && requestDigest.equals(digest)
                && expectedOrderVersion == expectedVersion && requestedDueAt.equals(due), CONFLICT);
    }

    public void requireAppliedTo(AcquireOrder order, long currentDeadlineVersion) {
        require(orderId.equals(order.id()) && order.version() > expectedOrderVersion
                && currentDeadlineVersion >= deadlineVersion
                && !order.orderDueAt().isBefore(requestedDueAt)
                && (currentDeadlineVersion != deadlineVersion || order.orderDueAt().equals(requestedDueAt)), CONFLICT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
