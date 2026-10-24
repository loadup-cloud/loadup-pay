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
import static io.github.loadup.commons.error.DomainViolation.require;

/** Immutable request binding; repeated revocations preserve the first marker and never reopen a capability. */
public record CheckoutSessionRevocationReceipt(String id, String sessionId, String orderId, String requestKey,
        String callerScopeHash, String requestDigest, @DiagnosticHidden String reason, boolean newlyRevoked,
        long beforeVersion, long sessionVersion, LocalDateTime revokedAt, LocalDateTime acceptedAt) {
    public CheckoutSessionRevocationReceipt {
        DomainChecks.id(id); DomainChecks.id(sessionId); DomainChecks.id(orderId); DomainChecks.text(requestKey, 128); DomainChecks.text(reason, 500);
        Objects.requireNonNull(revokedAt); Objects.requireNonNull(acceptedAt);
        require(callerScopeHash != null && callerScopeHash.matches("[0-9a-f]{64}") && requestDigest != null && requestDigest.matches("[0-9a-f]{64}")
                && beforeVersion >= 0 && sessionVersion > 0, INVALID_ARGUMENT);
        require(sessionVersion == (newlyRevoked ? Math.incrementExact(beforeVersion) : beforeVersion), INVALID_ARGUMENT);
    }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
