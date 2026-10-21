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
import io.github.loadup.pay.acquire.client.enums.AcquireOrderState;
import io.github.loadup.pay.common.*;
import io.github.loadup.pay.payment.client.dto.*;
import io.github.loadup.pay.payment.client.enums.PaymentState;
import io.github.loadup.pay.payment.client.enums.RefundState;
import io.github.loadup.pay.realtime.client.enums.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

public record TradeOrderDTO(
        @Schema(description = "Order id") String orderId,
        @Schema(description = "Payee merchant id") String payeeMerchantId,
        @Schema(description = "Merchant order number") String merchantOrderNo,
        @Schema(description = "Currency") String currency,
        @Schema(description = "Original total amount") long amountMinor,
        @Schema(description = "Gross successful amount") long paidAmountMinor,
        @Schema(description = "Held payment amount") long paymentReservedMinor,
        @Schema(description = "Original order state") AcquireOrderState state,
        @Schema(description = "Original order creation time") OffsetDateTime orderCreatedAt,
        @Schema(description = "Refunded minor") long refundedMinor,
        @Schema(description = "Net received minor") long netReceivedMinor,
        @Schema(description = "Order source version") long orderSourceVersion,
        @Schema(description = "Refund source version") long refundSourceVersion,
        @Schema(description = "Snapshot at") OffsetDateTime snapshotAt) {
    public TradeOrderDTO {
        if (orderCreatedAt != null)
            orderCreatedAt = orderCreatedAt.atZoneSameInstant(BusinessTime.ZONE).toOffsetDateTime();
        if (snapshotAt != null)
            snapshotAt = snapshotAt.atZoneSameInstant(BusinessTime.ZONE).toOffsetDateTime();
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
