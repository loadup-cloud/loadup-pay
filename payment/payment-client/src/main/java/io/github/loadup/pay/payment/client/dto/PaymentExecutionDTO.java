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
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.payment.client.enums.PaymentState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record PaymentExecutionDTO(
        @Schema(description = "Payment id") @Size(max = 64) String paymentId,
        @Schema(description = "Order id") @Size(max = 64) String orderId,

        @Schema(description = "Allocation id") @Size(max = 64)
        String allocationId,

        @Schema(description = "Amount minor") long amountMinor,

        @Schema(description = "Currency") @Size(max = 64) @Pattern(regexp = "CNY")
        String currency,

        @Schema(description = "State") PaymentState state,

        @Schema(description = "Active attempt id") @Size(max = 64)
        String activeAttemptId,

        @Schema(description = "Action") @Valid PaymentActionDTO action,

        @Schema(description = "Terminal evidence id") @Size(max = 64)
        String terminalEvidenceId,

        @Schema(description = "Version") long version,
        @Schema(description = "Initiated at") OffsetDateTime initiatedAt) {
    public PaymentExecutionDTO {
        if (initiatedAt != null)
            initiatedAt = initiatedAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
