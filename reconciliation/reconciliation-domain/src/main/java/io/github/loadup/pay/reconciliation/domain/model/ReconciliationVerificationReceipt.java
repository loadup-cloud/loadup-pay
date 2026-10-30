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
import io.github.loadup.commons.money.Money;

import java.util.Set;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Consumer-owned immutable metadata snapshot of the trusted owner's query, not a financial posting.
 */
public record ReconciliationVerificationReceipt(ReconciliationVerificationDisposition disposition,
                                                String bindingRevisionId,
                                                String paymentId, String attemptId, String refundId, Long sourceVersion,
                                                String localState, ReconciliationVerificationOutcome outcome,
                                                Money amount, @DiagnosticHidden String observedChannelTransactionNo,
                                                @DiagnosticHidden String observedRefundTransactionNo,
                                                Long succeededEpochMicros, String sourceDigest, long startedEpochMicros,
                                                long finishedEpochMicros, Long acceptedEpochMicros) {
    public ReconciliationVerificationReceipt {
        require(disposition != null && startedEpochMicros > 0 && finishedEpochMicros >= startedEpochMicros, INVALID_ARGUMENT);
        if (disposition == ReconciliationVerificationDisposition.OBSERVED) {
            require(text(bindingRevisionId, 64) && text(paymentId, 64) && text(attemptId, 64) && sourceVersion != null && sourceVersion >= 0
                    && text(localState, 32) && outcome != null && amount != null && amount.getCent() > 0 && "CNY".equals(amount.getCurrencyValue())
                    && sourceDigest != null && sourceDigest.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
            if (refundId != null) {
                require(text(refundId, 64), INVALID_ARGUMENT);
            }
            boolean success = outcome == ReconciliationVerificationOutcome.SUCCEEDED || outcome == ReconciliationVerificationOutcome.CLOSED_WITH_FUNDS_RETURNED;
            require(success == (succeededEpochMicros != null) && (succeededEpochMicros == null || succeededEpochMicros > 0), INVALID_ARGUMENT);
            require(!success || text(observedChannelTransactionNo, 128), INVALID_ARGUMENT);
            require(observedChannelTransactionNo == null || text(observedChannelTransactionNo, 128), INVALID_ARGUMENT);
            require(observedRefundTransactionNo == null || text(observedRefundTransactionNo, 128), INVALID_ARGUMENT);
        } else {
            require(bindingRevisionId == null && paymentId == null && attemptId == null && refundId == null && sourceVersion == null && localState == null
                    && outcome == null && amount == null && observedChannelTransactionNo == null && observedRefundTransactionNo == null
                    && succeededEpochMicros == null && sourceDigest == null && acceptedEpochMicros == null, INVALID_ARGUMENT);
        }
    }

    private static boolean text(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max && value.chars().noneMatch(Character::isISOControl);
    }

    public void requireKind(ReconciliationRecordKind kind) {
        if (disposition != ReconciliationVerificationDisposition.OBSERVED) {
            return;
        }
        boolean refund = kind == ReconciliationRecordKind.REFUND;
        require(refund == (refundId != null), INVALID_ARGUMENT);
        if (acceptedEpochMicros != null) {
            require(refund && acceptedEpochMicros > 0 && (succeededEpochMicros == null || acceptedEpochMicros <= succeededEpochMicros), INVALID_ARGUMENT);
        }
        if (refund) {
            require(Set.of(ReconciliationVerificationOutcome.ACCEPTED, ReconciliationVerificationOutcome.SUCCEEDED, ReconciliationVerificationOutcome.FAILED_WITH_NO_REFUND, ReconciliationVerificationOutcome.UNKNOWN).contains(outcome)
                    && text(observedChannelTransactionNo, 128) && (outcome == ReconciliationVerificationOutcome.SUCCEEDED) == (observedRefundTransactionNo != null), INVALID_ARGUMENT);
        } else {
            require(outcome != ReconciliationVerificationOutcome.FAILED_WITH_NO_REFUND && observedRefundTransactionNo == null, INVALID_ARGUMENT);
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
