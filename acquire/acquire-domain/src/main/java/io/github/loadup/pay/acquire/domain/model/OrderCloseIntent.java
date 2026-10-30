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
 * The original close request is immutable; only the bounded scan cursor advances.
 */
public record OrderCloseIntent(String id, String orderId, String payeeMerchantId, String callerScopeHash,
                               String requestKey, String requestDigest, String reasonCode, long closeEpoch,
                               LocalDateTime requestedAt, String cursorPaymentId, long rowVersion) {
    public OrderCloseIntent {
        DomainChecks.id(id);
        DomainChecks.id(orderId);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.text(requestKey, 128);
        DomainChecks.text(reasonCode, 64);
        Objects.requireNonNull(requestedAt);
        Objects.requireNonNull(cursorPaymentId);
        if (!cursorPaymentId.isEmpty()) {
            DomainChecks.id(cursorPaymentId);
        }
        require(closeEpoch > 0 && rowVersion >= 0 && callerScopeHash != null
                && callerScopeHash.matches("[0-9a-f]{64}") && requestDigest != null
                && requestDigest.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
    }

    public void requireSameRequest(String callerScope, String key, String digest) {
        require(callerScopeHash.equals(callerScope) && requestKey.equals(key) && requestDigest.equals(digest), CONFLICT);
    }

    public OrderCloseIntent advanceCursor(String paymentId) {
        return new OrderCloseIntent(id, orderId, payeeMerchantId, callerScopeHash, requestKey, requestDigest,
                reasonCode, closeEpoch, requestedAt, paymentId, Math.incrementExact(rowVersion));
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
