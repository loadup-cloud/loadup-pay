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
import io.github.loadup.pay.common.DomainChecks;

import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.pay.common.DomainViolation.require;

/**
 * Stable route/request identities and the durable one-way Y2 submission boundary.
 */
public record StoredPaymentAttempt(PaymentAttempt attempt, String limitOperationId, String callerServiceId,
                                   String requestKey, String requestDigest, String paymentRequestDigest,
                                   @DiagnosticHidden String preparedInputRef, RiskDecision riskDecision,
                                   @DiagnosticHidden String executionPermitHash, LocalDateTime preparedAt,
                                   LocalDateTime submissionStartedAt, long rowVersion) {
    public StoredPaymentAttempt {
        Objects.requireNonNull(attempt);
        DomainChecks.id(limitOperationId);
        DomainChecks.id(callerServiceId);
        DomainChecks.text(requestKey, 128);
        Objects.requireNonNull(preparedAt);
        require(requestDigest != null && requestDigest.matches("[0-9a-f]{64}")
                && paymentRequestDigest != null && paymentRequestDigest.matches("[0-9a-f]{64}") && rowVersion >= 0, INVALID_ARGUMENT);
        if (preparedInputRef != null) DomainChecks.id(preparedInputRef);
        if (attempt.executionEpoch() == 0) {
            require(executionPermitHash == null && submissionStartedAt == null
                    && (attempt.state() == PaymentAttemptState.INIT || attempt.state() == PaymentAttemptState.CLOSED), INVALID_ARGUMENT);
            if (attempt.state() == PaymentAttemptState.INIT)
                require(riskDecision == null && rowVersion == 0, INVALID_ARGUMENT);
            else require(rowVersion > 0, INVALID_ARGUMENT);
        } else {
            require(executionPermitHash != null && executionPermitHash.matches("[0-9a-f]{64}")
                    && submissionStartedAt != null && !submissionStartedAt.isBefore(preparedAt)
                    && riskDecision != null && riskDecision.decision() != RiskDecisionType.DENY && rowVersion > 0, INVALID_ARGUMENT);
        }
    }

    public StoredPaymentAttempt beginSubmission(long epoch, String permitHash, RiskDecision decision, LocalDateTime now) {
        require(decision != null && decision.decision() != RiskDecisionType.DENY, INVALID_ARGUMENT);
        return new StoredPaymentAttempt(attempt.beginSubmission(epoch), limitOperationId, callerServiceId, requestKey,
                requestDigest, paymentRequestDigest, preparedInputRef, decision, permitHash, preparedAt, now,
                Math.incrementExact(rowVersion));
    }

    public StoredPaymentAttempt closeBeforeSubmission(String evidenceId, RiskDecision decision) {
        return new StoredPaymentAttempt(attempt.closeBeforeSubmission(evidenceId), limitOperationId, callerServiceId,
                requestKey, requestDigest, paymentRequestDigest, preparedInputRef, decision, null, preparedAt, null,
                Math.incrementExact(rowVersion));
    }

    public StoredPaymentAttempt applyOutcome(ChannelPaymentOutcome outcome, boolean executionFenced) {
        var updated = attempt.recordOutcome(outcome, executionFenced);
        if (updated.equals(attempt)) return this;
        return new StoredPaymentAttempt(updated, limitOperationId, callerServiceId, requestKey, requestDigest,
                paymentRequestDigest, preparedInputRef, riskDecision, executionPermitHash, preparedAt,
                submissionStartedAt, Math.incrementExact(rowVersion));
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
