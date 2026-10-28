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
package io.github.loadup.pay.reconciliation.domain.model;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_STATE;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Frozen approved review and one Outbox delivery. Recorded observations never erase the original difference.
 */
public record ReconciliationVerificationRequest(String id, ReconciliationReviewReference reference, String proposalId,
                                                String approvalEventId, long reviewVersion, String callerServiceId,
                                                String requestKey, String requestDigest,
                                                ReconciliationScope scope, ReconciliationKey key,
                                                @DiagnosticHidden String merchantPaymentNo, String deliveryId,
                                                ReconciliationVerificationState state,
                                                ReconciliationVerificationReceipt receipt, long version) {
    public ReconciliationVerificationRequest {
        DomainChecks.id(id);
        Objects.requireNonNull(reference);
        DomainChecks.id(proposalId);
        DomainChecks.id(approvalEventId);
        DomainChecks.id(callerServiceId);
        DomainChecks.text(requestKey, 128);
        DomainChecks.id(deliveryId);
        Objects.requireNonNull(scope);
        Objects.requireNonNull(key);
        DomainChecks.text(merchantPaymentNo, 64);
        require(reviewVersion >= 2L && reviewVersion % 2L == 0L && requestDigest != null && requestDigest.matches("[0-9a-f]{64}") && reference.matchKeyHash().equals(key.fingerprint()), INVALID_ARGUMENT);
        require(state != null && version == (state == ReconciliationVerificationState.INIT ? 0L : 1L)
                && (state == ReconciliationVerificationState.RECORDED) == (receipt != null), INVALID_STATE);
        if (receipt != null) receipt.requireKind(key.kind());
    }

    public ReconciliationVerificationRequest complete(ReconciliationVerificationReceipt value) {
        Objects.requireNonNull(value);
        if (state == ReconciliationVerificationState.RECORDED) {
            require(receipt.equals(value), CONFLICT);
            return this;
        }
        return new ReconciliationVerificationRequest(id, reference, proposalId, approvalEventId, reviewVersion, callerServiceId, requestKey, requestDigest,
                scope, key, merchantPaymentNo, deliveryId, ReconciliationVerificationState.RECORDED, value, 1L);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
