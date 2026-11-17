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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Frozen successful refund, original collected payment, and the exact committed capacity version. */
public record RefundFinancialEvidence(
        int schemaVersion,
        String refundId,
        String refundRequestId,
        long refundVersion,
        String refundEvidenceId,
        long amountMinor,
        long cumulativePaymentRefundedMinor,
        long paymentRefundVersion,
        OffsetDateTime refundedAt,
        PaymentFinancialEvidence payment) {
    public RefundFinancialEvidence {
        DomainChecks.id(refundId);
        DomainChecks.id(refundRequestId);
        DomainChecks.id(refundEvidenceId);
        Objects.requireNonNull(payment);
        Objects.requireNonNull(refundedAt);
        require(
                schemaVersion == 1
                        && refundVersion > 0
                        && paymentRefundVersion > 0
                        && amountMinor > 0
                        && payment.state() == PaymentState.SUCCEEDED
                        && amountMinor <= cumulativePaymentRefundedMinor
                        && cumulativePaymentRefundedMinor <= payment.amountMinor(),
                INVALID_ARGUMENT);
        refundedAt = refundedAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
    }

    public List<String> canonicalFields() {
        var fields = new ArrayList<>(List.of(
                "refund-financial-evidence-v1",
                Integer.toString(schemaVersion),
                refundId,
                refundRequestId,
                Long.toString(refundVersion),
                refundEvidenceId,
                Long.toString(amountMinor),
                Long.toString(cumulativePaymentRefundedMinor),
                Long.toString(paymentRefundVersion),
                refundedAt.toString()));
        fields.addAll(payment.canonicalFields());
        return List.copyOf(fields);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
