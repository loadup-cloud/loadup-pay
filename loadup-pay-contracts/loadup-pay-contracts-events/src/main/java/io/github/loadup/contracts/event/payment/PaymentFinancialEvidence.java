/*
 * Copyright (C) 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.contracts.event.payment;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.payment.channel.contract.ChannelAmounts;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Source-verified terminal payment and permanent collection/no-collection proof. Caller identity is authenticated separately. */
public record PaymentFinancialEvidence(
        int schemaVersion,
        String paymentId,
        String orderId,
        String allocationId,
        String operationId,
        String payeeMerchantId,
        long amountMinor,
        String currency,
        PaymentState state,
        long paymentVersion,
        String terminalEvidenceId,
        String activeAttemptId,
        OffsetDateTime initiatedAt,
        OffsetDateTime succeededAt,
        boolean executionFenced) {
    public PaymentFinancialEvidence {
        DomainChecks.id(paymentId);
        DomainChecks.id(orderId);
        DomainChecks.id(allocationId);
        DomainChecks.id(operationId);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.id(terminalEvidenceId);
        if (activeAttemptId != null) {
            DomainChecks.id(activeAttemptId);
        }
        Objects.requireNonNull(initiatedAt);
        ChannelAmounts.cnyAmount(amountMinor, currency);
        require(
                schemaVersion == 1
                        && paymentVersion > 0
                        && amountMinor > 0
                        && state != null
                        && Set.of(PaymentState.SUCCEEDED, PaymentState.FAILED, PaymentState.CLOSED)
                                .contains(state),
                INVALID_ARGUMENT);
        require(
                state == PaymentState.SUCCEEDED
                        ? activeAttemptId != null && succeededAt != null && !succeededAt.isBefore(initiatedAt)
                        : executionFenced && succeededAt == null,
                INVALID_ARGUMENT);
        initiatedAt = initiatedAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
        if (succeededAt != null) {
            succeededAt = succeededAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
        }
    }

    public List<String> canonicalFields() {
        return List.of(
                "payment-financial-evidence-v1",
                Integer.toString(schemaVersion),
                paymentId,
                orderId,
                allocationId,
                operationId,
                payeeMerchantId,
                Long.toString(amountMinor),
                currency,
                state.name(),
                Long.toString(paymentVersion),
                terminalEvidenceId,
                activeAttemptId == null ? "" : activeAttemptId,
                initiatedAt.toString(),
                succeededAt == null ? "" : succeededAt.toString(),
                Boolean.toString(executionFenced));
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
