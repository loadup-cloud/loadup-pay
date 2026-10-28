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
import io.github.loadup.pay.acquire.client.enums.OrderCreationRecoveryState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

public record DirectPayRecoveryDTO(
        @Schema(description = "Original merchant order number") String merchantOrderNo,
        @Schema(description = "Reserved original order identity; may exist before an order is materialized") String orderId,
        @Schema(description = "Original creation outcome, independent of collection") OrderCreationRecoveryState creationState,
        @Schema(description = "Validated original creation result code; absent before a decision") String resultCode,
        @Schema(description = "Original materialized order; absent unless creation completed") @Valid AcquireOrderDTO order,
        @Schema(description = "Original first payment recovery; absent unless creation completed") @Valid PaymentRecoveryDTO payment) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
