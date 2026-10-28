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
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.PaymentProgress;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.Set;

public record CashierOrderDTO(
        @Schema(description = "Order id") @Size(max = 64) String orderId,

        @Schema(description = "Merchant display name") @Size(max = 64)
        String merchantDisplayName,

        @Schema(description = "Subject") @Size(max = 200) String subject,

        @Schema(description = "Currency") @Size(max = 64) @Pattern(regexp = "CNY")
        String currency,

        @Schema(description = "Amount minor") long amountMinor,
        @Schema(description = "Paid amount minor") long paidAmountMinor,
        @Schema(description = "Payment reserved minor") long paymentReservedMinor,
        @Schema(description = "Available to pay minor") long availableToPayMinor,
        @Schema(description = "Frozen minimum amount for one payment") long minimumPaymentMinor,
        @Schema(description = "Frozen maximum amount for one payment") long maximumPaymentMinor,
        @Schema(description = "Whether the exact remainder may be below the minimum") boolean allowExactRemainderBelowMin,
        @Schema(description = "Remaining successful-or-reserved payment slots; absent when unlimited") Integer remainingPaymentCount,
        @Schema(description = "State") AcquireOrderState state,
        @Schema(description = "Progress") PaymentProgress progress,
        @Schema(description = "Choices") @Valid Set<PaymentChoiceDTO> choices,
        @Schema(description = "Session expires at") OffsetDateTime sessionExpiresAt) {
    public CashierOrderDTO {
        choices = choices == null ? Set.of() : Set.copyOf(choices);
        if (sessionExpiresAt != null)
            sessionExpiresAt =
                    sessionExpiresAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
