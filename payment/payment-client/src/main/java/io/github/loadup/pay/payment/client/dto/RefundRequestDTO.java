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
import io.github.loadup.pay.payment.client.enums.RefundRequestState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RefundRequestDTO(
        @Schema(description = "Refund request id") @Size(max = 64)
        String refundRequestId,

        @Schema(description = "Order id") @Size(max = 64) String orderId,

        @Schema(description = "Merchant refund no") @Size(max = 64)
        String merchantRefundNo,

        @Schema(description = "Currency") @Size(max = 64) @Pattern(regexp = "CNY")
        String currency,

        @Schema(description = "Amount minor") long amountMinor,
        @Schema(description = "State") RefundRequestState state,
        @Schema(description = "Succeeded minor") long succeededMinor,
        @Schema(description = "Failed minor") long failedMinor,
        @Schema(description = "Unknown count") int unknownCount,
        @Schema(description = "Plan version") int planVersion,
        @Schema(description = "Allocations") @Valid List<RefundAllocationDTO> allocations,
        @Schema(description = "Version") long version) {
    public RefundRequestDTO {
        allocations = allocations == null ? List.of() : List.copyOf(allocations);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
