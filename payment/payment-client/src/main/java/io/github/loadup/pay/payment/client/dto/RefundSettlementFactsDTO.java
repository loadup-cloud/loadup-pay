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
package io.github.loadup.pay.payment.client.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.payment.client.event.RefundOutcomeEventDTO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

public record RefundSettlementFactsDTO(
        @Schema(description = "Verified refund and latest successful refund cumulative for its payment") RefundOutcomeEventDTO refund,
        @Schema(description = "Original successful payment amount") long paymentAmountMinor,
        @Schema(description = "Original successful payment version") long paymentVersion,
        @Schema(description = "Original successful payment evidence") String paymentEvidenceId,
        @Schema(description = "Original successful payment attempt") String paymentAttemptId,
        @Schema(description = "Original payment acceptance time") OffsetDateTime paymentInitiatedAt,
        @Schema(description = "Latest payment refund-capacity source version") long paymentRefundVersion) {
    public RefundSettlementFactsDTO {
        Objects.requireNonNull(refund);
        Objects.requireNonNull(paymentInitiatedAt);
        DomainChecks.id(paymentEvidenceId);
        DomainChecks.id(paymentAttemptId);
        require(paymentAmountMinor > 0 && paymentVersion > 0 && paymentRefundVersion > 0
                && refund.amountMinor() <= paymentAmountMinor && refund.cumulativePaymentRefundedMinor() <= paymentAmountMinor, INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
