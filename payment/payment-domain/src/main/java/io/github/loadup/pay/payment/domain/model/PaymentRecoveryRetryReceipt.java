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
package io.github.loadup.pay.payment.domain.model;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Immutable operational authority and first decision, separate from all financial evidence.
 */
public record PaymentRecoveryRetryReceipt(String id, String requestKey, String callerScopeHash, String callerServiceId,
                                          String requestDigest, String kind, String subjectId, long generation,
                                          String orderId, String nativeTaskId,
                                          int expectedAttempts, LocalDateTime expectedNextExecuteAt,
                                          @DiagnosticHidden String reason,
                                          String result, LocalDateTime acceptedAt) {
    public PaymentRecoveryRetryReceipt {
        DomainChecks.id(id);
        DomainChecks.text(requestKey, 128);
        DomainChecks.id(callerServiceId);
        DomainChecks.id(subjectId);
        DomainChecks.id(orderId);
        DomainChecks.id(nativeTaskId);
        DomainChecks.text(reason, 500);
        Objects.requireNonNull(expectedNextExecuteAt);
        Objects.requireNonNull(acceptedAt);
        require(callerScopeHash != null && callerScopeHash.matches("[0-9a-f]{64}")
                        && requestDigest != null && requestDigest.matches("[0-9a-f]{64}") && generation >= 0 && expectedAttempts > 0,
                INVALID_ARGUMENT);
        require(kind != null && Set.of("COLLECTION", "REFUND_REQUEST").contains(kind), INVALID_ARGUMENT);
        require(result != null && Set.of("RETRIED", "NO_LONGER_NEEDED").contains(result), INVALID_ARGUMENT);
        for (var time : java.util.List.of(expectedNextExecuteAt, acceptedAt)) {
            require(time.getNano() % 1000 == 0 && time.getYear() >= 1970 && time.getYear() <= 9998, INVALID_ARGUMENT);
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
