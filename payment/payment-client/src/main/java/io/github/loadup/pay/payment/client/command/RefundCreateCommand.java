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
package io.github.loadup.pay.payment.client.command;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.payment.client.dto.RefundExplicitAllocationDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RefundCreateCommand(
        @Schema(description = "Order id") @NotBlank @Size(max = 64)
        String orderId,

        @Schema(description = "Payee merchant id") @NotBlank @Size(max = 64)
        String payeeMerchantId,

        @Schema(description = "Merchant refund no") @NotBlank @Size(max = 64)
        String merchantRefundNo,

        @Schema(description = "Fixed requested refund amount in CNY minor units (fen)") @Positive long amountMinor,

        @Schema(description = "Reason code") @NotBlank @Size(max = 64)
        String reasonCode,

        @Schema(description = "Explicit allocations; must be empty for the supported FIFO policy") @Valid List<RefundExplicitAllocationDTO> explicitAllocations) {
    public RefundCreateCommand {
        explicitAllocations = explicitAllocations == null ? List.of() : List.copyOf(explicitAllocations);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
