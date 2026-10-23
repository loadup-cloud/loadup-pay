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
import io.github.loadup.pay.common.BusinessTime;
import io.github.loadup.pay.payment.client.enums.RefundState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record TradeRefundDTO(
        @Schema(description = "Refund id") @Size(max = 64) String refundId,

        @Schema(description = "Refund request id") @Size(max = 64)
        String refundRequestId,

        @Schema(description = "Payment id") @Size(max = 64) String paymentId,
        @Schema(description = "Order id") @Size(max = 64) String orderId,

        @Schema(description = "Payee merchant id") @Size(max = 64)
        String payeeMerchantId,

        @Schema(description = "Amount minor") long amountMinor,

        @Schema(description = "Currency") @Size(max = 64) @Pattern(regexp = "CNY")
        String currency,

        @Schema(description = "State") RefundState state,
        @Schema(description = "Original child refund creation time") OffsetDateTime refundCreatedAt,
        @Schema(description = "Succeeded at") OffsetDateTime succeededAt,
        @Schema(description = "Source version") long sourceVersion,
        @Schema(description = "Snapshot at") OffsetDateTime snapshotAt) {
    public TradeRefundDTO {
        if (refundCreatedAt != null)
            refundCreatedAt = refundCreatedAt.atZoneSameInstant(BusinessTime.ZONE).toOffsetDateTime();
        if (succeededAt != null)
            succeededAt = succeededAt.atZoneSameInstant(BusinessTime.ZONE).toOffsetDateTime();
        if (snapshotAt != null)
            snapshotAt = snapshotAt.atZoneSameInstant(BusinessTime.ZONE).toOffsetDateTime();
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
