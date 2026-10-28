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
package io.github.loadup.pay.acquire.client.event;

import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.acquire.client.enums.AcquireOrderState;
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
public record OrderSnapshotEventDTO(
        @Schema(description = "Order id") String orderId,
        @Schema(description = "Payee merchant id") String payeeMerchantId,
        @Schema(description = "Merchant order number") String merchantOrderNo,

        @Schema(description = "Fixed order amount in minor units")
        long amountMinor,

        @Schema(description = "Currency") String currency,
        @Schema(description = "Gross successful amount") long paidAmountMinor,
        @Schema(description = "Held payment amount") long paymentReservedMinor,
        @Schema(description = "Order lifecycle state") AcquireOrderState state,

        @Schema(description = "Original order creation time")
        OffsetDateTime orderCreatedAt,

        @Schema(description = "Source update time") OffsetDateTime sourceUpdatedAt,
        @Schema(description = "Source aggregate version") long sourceVersion) {
    public OrderSnapshotEventDTO {

        DomainChecks.id(orderId);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.text(merchantOrderNo, 64);
        DomainChecks.cnyAmount(amountMinor, currency);
        require(
                amountMinor > 0
                        && paidAmountMinor >= 0
                        && paymentReservedMinor >= 0
                        && paidAmountMinor <= amountMinor
                        && paymentReservedMinor <= amountMinor - paidAmountMinor
                        && sourceVersion >= 0
                        && state != null,
                INVALID_ARGUMENT);
        require(
                (state == AcquireOrderState.PAID) == (paidAmountMinor == amountMinor)
                        && (state != AcquireOrderState.CLOSED || paymentReservedMinor == 0),
                INVALID_STATE);
        Objects.requireNonNull(orderCreatedAt);
        Objects.requireNonNull(sourceUpdatedAt);
        require(!sourceUpdatedAt.isBefore(orderCreatedAt), INVALID_ARGUMENT);
        if (orderCreatedAt != null)
            orderCreatedAt = orderCreatedAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
        if (sourceUpdatedAt != null)
            sourceUpdatedAt =
                    sourceUpdatedAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
    }

    public void requireEnvelope(EventEnvelope<?> envelope) {
        require(equals(envelope.data()), CONFLICT);
        envelope.requireSource("pay.acquire.snapshot.realtime.v1", "acquire", orderId, sourceVersion);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
