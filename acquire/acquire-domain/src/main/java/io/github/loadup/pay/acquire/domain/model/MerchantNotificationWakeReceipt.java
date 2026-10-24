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
import java.util.Set;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/** Immutable request binding and operational decision; does not change the original delivery budget. */
public record MerchantNotificationWakeReceipt(String id, String deliveryId, String eventId, String requestKey,
        String callerScopeHash, String principalType, String principalId, @DiagnosticHidden String reason, String requestDigest,
        String result, int attemptCount, long deliveryVersion, String nativeTaskId, LocalDateTime acceptedAt) {
    public MerchantNotificationWakeReceipt {
        DomainChecks.id(id); DomainChecks.id(deliveryId); DomainChecks.id(eventId); DomainChecks.text(requestKey, 128);
        DomainChecks.text(principalId, 128); DomainChecks.text(reason, 500); Objects.requireNonNull(acceptedAt);
        if (nativeTaskId != null) DomainChecks.id(nativeTaskId);
        require(principalType != null && Set.of("OPERATOR", "SERVICE").contains(principalType)
                && attemptCount >= 0 && deliveryVersion >= 0 && callerScopeHash != null && callerScopeHash.matches("[0-9a-f]{64}")
                && requestDigest != null && requestDigest.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
        require(result != null && Set.of("REGISTERED", "RETRIED", "RESCHEDULED", "PENDING", "RUNNING", "WAITING", "ACKED", "EXHAUSTED", "EXPIRED").contains(result), INVALID_ARGUMENT);
    }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
