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
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** An absent payment has a durable gate and receipt, never a fabricated zero-amount payment. */
public record PaymentCloseDTO(
        @Schema(description = "Payment id") @NotBlank @Size(max = 64)
        String paymentId,

        @Schema(description = "Order id") @NotBlank @Size(max = 64)
        String orderId,

        @Schema(description = "Current persisted fence epoch") @Positive
        long fenceEpoch,

        @Schema(description = "Execution is persistently fenced")
        boolean fenced,

        @Schema(description = "Current payment; null if it was never accepted") @Valid
        PaymentExecutionDTO payment,

        @Schema(description = "Trusted no-collection evidence; null while funds may still be collected") @Size(max = 64)
        String noCollectionEvidenceId) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
