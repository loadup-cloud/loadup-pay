/*
 * Copyright (C) 2025-2026 LoadUp Cloud
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

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.payment.channel.contract.ChannelAmounts;
import io.github.loadup.contracts.event.BusinessEventTypes;
import io.github.loadup.contracts.event.FrozenEventTypes;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Objects;

public record RefundOutcomeEventDTO(
        @Schema(description = "Refund id") String refundId,
        @Schema(description = "Refund request id") String refundRequestId,
        @Schema(description = "Payment id") String paymentId,
        @Schema(description = "Order id") String orderId,
        @Schema(description = "Operation id") String operationId,
        @Schema(description = "Payee merchant id") String payeeMerchantId,
        @Schema(description = "Amount minor") long amountMinor,
        @Schema(description = "Currency") String currency,
        @Schema(description = "State") RefundState state,
        @Schema(description = "Refund version") long refundVersion,

        @Schema(description = "Cumulative payment refunded minor")
        long cumulativePaymentRefundedMinor,

        @Schema(description = "Evidence id") String evidenceId,
        @JsonInclude(JsonInclude.Include.NON_NULL) RefundFinancialEvidence financialEvidence) {
    public RefundOutcomeEventDTO {
        if (financialEvidence != null) {
            DomainViolation.require(
                    financialEvidence.refundId().equals(refundId)
                            && financialEvidence.refundRequestId().equals(refundRequestId)
                            && financialEvidence.refundVersion() == refundVersion
                            && financialEvidence.refundEvidenceId().equals(evidenceId)
                            && financialEvidence.amountMinor() == amountMinor
                            && financialEvidence.cumulativePaymentRefundedMinor() == cumulativePaymentRefundedMinor
                            && financialEvidence.payment().paymentId().equals(paymentId)
                            && financialEvidence.payment().orderId().equals(orderId)
                            && financialEvidence.payment().operationId().equals(operationId)
                            && financialEvidence.payment().payeeMerchantId().equals(payeeMerchantId)
                            && financialEvidence.payment().currency().equals(currency)
                            && state == RefundState.SUCCEEDED,
                    DomainViolation.Reason.CONFLICT);
        }
        DomainChecks.id(refundId);
        DomainChecks.id(refundRequestId);
        DomainChecks.id(paymentId);
        DomainChecks.id(orderId);
        DomainChecks.id(operationId);
        DomainChecks.id(payeeMerchantId);
        DomainViolation.require(
                amountMinor > 0 && refundVersion > 0 && state != null && cumulativePaymentRefundedMinor >= 0,
                DomainViolation.Reason.INVALID_ARGUMENT);
        ChannelAmounts.cnyAmount(amountMinor, currency);
        if (state == RefundState.SUCCEEDED || state == RefundState.FAILED) {
            DomainChecks.id(evidenceId);
        }
        if (state == RefundState.SUCCEEDED) {
            DomainViolation.require(cumulativePaymentRefundedMinor >= amountMinor, DomainViolation.Reason.CONFLICT);
        }
    }

    public void requireEnvelope(EventEnvelope<?> envelope) {
        DomainViolation.require(equals(envelope.data()), DomainViolation.Reason.CONFLICT);
        if (financialEvidence != null) {
            envelope.requireSource(BusinessEventTypes.REFUND_FINANCIAL, "payment", refundId, refundVersion);
            return;
        }
        FrozenEventTypes.requireSource(
                envelope,
                BusinessEventTypes.REFUND_OUTCOME,
                "payment",
                refundId,
                refundVersion,
                "pay.payment.refund.limit.v1");
    }

    public RefundOutcomeEventDTO withFinancialEvidence(RefundFinancialEvidence evidence) {
        return new RefundOutcomeEventDTO(
                refundId,
                refundRequestId,
                paymentId,
                orderId,
                operationId,
                payeeMerchantId,
                amountMinor,
                currency,
                state,
                refundVersion,
                cumulativePaymentRefundedMinor,
                evidenceId,
                Objects.requireNonNull(evidence));
    }

    public RefundOutcomeEventDTO withoutFinancialEvidence() {
        return new RefundOutcomeEventDTO(
                refundId,
                refundRequestId,
                paymentId,
                orderId,
                operationId,
                payeeMerchantId,
                amountMinor,
                currency,
                state,
                refundVersion,
                cumulativePaymentRefundedMinor,
                evidenceId,
                null);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
