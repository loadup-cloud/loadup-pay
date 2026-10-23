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
package io.github.loadup.pay.acquire.client.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.acquire.client.enums.AcquireOrderState;
import io.github.loadup.pay.acquire.client.enums.PaymentProgress;
import io.github.loadup.commons.time.BusinessTime;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record AcquireOrderDTO(
        @Schema(description = "Order id") @Size(max = 64) String orderId,

        @Schema(description = "Merchant order no") @Size(max = 64)
        String merchantOrderNo,

        @Schema(description = "Payee merchant id") @Size(max = 64)
        String payeeMerchantId,

        @Schema(description = "Currency") @Size(max = 64) @Pattern(regexp = "CNY")
        String currency,

        @Schema(description = "Amount minor") long amountMinor,
        @Schema(description = "Paid amount minor") long paidAmountMinor,
        @Schema(description = "Payment reserved minor") long paymentReservedMinor,
        @Schema(description = "Outstanding amount minor") long outstandingAmountMinor,
        @Schema(description = "Available to pay minor") long availableToPayMinor,
        @Schema(description = "State") AcquireOrderState state,
        @Schema(description = "Progress") PaymentProgress progress,
        @Schema(description = "Can accept new payment") boolean canAcceptNewPayment,
        @Schema(description = "Order due at") OffsetDateTime orderDueAt,
        @Schema(description = "Version") long version) {
    public AcquireOrderDTO {
        if (orderDueAt != null)
            orderDueAt = orderDueAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
