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
package io.github.loadup.pay.realtime.client.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public record TradeOrderDTO(
        @Schema(description = "Order id") String orderId,
        @Schema(description = "Payee merchant id") String payeeMerchantId,
        @Schema(description = "Merchant order number") String merchantOrderNo,
        @Schema(description = "Currency") String currency,
        @Schema(description = "Original total amount") long amountMinor,
        @Schema(description = "Gross successful amount") long paidAmountMinor,
        @Schema(description = "Held payment amount") long paymentReservedMinor,
        @Schema(description = "Original order state") AcquireOrderState state,

        @Schema(description = "Original order creation time")
        OffsetDateTime orderCreatedAt,

        @Schema(description = "Refunded minor; null before the refund summary arrives") Long refundedMinor,
        @Schema(description = "Observed net amount; null without the refund summary") Long netReceivedMinor,
        @Schema(description = "Order source version") long orderSourceVersion,
        @Schema(description = "Refund source version; null when unavailable") Long refundSourceVersion,
        @Schema(description = "Independent source amounts currently agree") boolean amountsConsistent,
        @Schema(description = "Snapshot at") OffsetDateTime snapshotAt) {
    public TradeOrderDTO {
        if (orderCreatedAt != null)
            orderCreatedAt = orderCreatedAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
        if (snapshotAt != null)
            snapshotAt = snapshotAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
