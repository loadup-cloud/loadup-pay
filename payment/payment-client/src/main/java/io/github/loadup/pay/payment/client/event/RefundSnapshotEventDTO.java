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
package io.github.loadup.pay.payment.client.event;

import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.payment.domain.model.RefundState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_STATE;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Complete source-owned snapshot; no synchronous source lookup is required by realtime.
 */
public record RefundSnapshotEventDTO(
        @Schema(description = "Child refund id") String refundId,
        @Schema(description = "Refund request id") String refundRequestId,
        @Schema(description = "Payment id") String paymentId,
        @Schema(description = "Order id") String orderId,
        @Schema(description = "Payee merchant id") String payeeMerchantId,

        @Schema(description = "Fixed refund amount in minor units")
        long amountMinor,

        @Schema(description = "Currency") String currency,
        @Schema(description = "Refund lifecycle state") RefundState state,

        @Schema(description = "Original child creation time")
        OffsetDateTime refundCreatedAt,

        @Schema(description = "Verified refund success time")
        OffsetDateTime succeededAt,

        @Schema(description = "Source aggregate version") long sourceVersion) {
    public RefundSnapshotEventDTO {

        DomainChecks.id(refundId);
        DomainChecks.id(refundRequestId);
        DomainChecks.id(paymentId);
        DomainChecks.id(orderId);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.cnyAmount(amountMinor, currency);
        require(amountMinor > 0 && sourceVersion >= 0 && state != null, INVALID_ARGUMENT);
        require((state == RefundState.SUCCEEDED) == (succeededAt != null), INVALID_STATE);
        Objects.requireNonNull(refundCreatedAt);
        if (refundCreatedAt != null)
            refundCreatedAt =
                    refundCreatedAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
        if (succeededAt != null)
            succeededAt = succeededAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
    }

    public void requireEnvelope(EventEnvelope<?> envelope) {
        require(equals(envelope.data()), CONFLICT);
        envelope.requireSource("pay.payment.refund.realtime.v1", "payment", refundId, sourceVersion);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
