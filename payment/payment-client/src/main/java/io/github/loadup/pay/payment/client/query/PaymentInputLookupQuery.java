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
package io.github.loadup.pay.payment.client.query;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Read only the original input reference for the exact immutable acquiring intent; no payer input is returned.
 */
public record PaymentInputLookupQuery(
        @Schema(description = "Fixed payment id") @NotBlank @Size(max = 64) String paymentId,
        @Schema(description = "Original order id") @NotBlank @Size(max = 64) String orderId,
        @Schema(description = "Fixed acquiring allocation id") @NotBlank @Size(max = 64) String allocationId,
        @Schema(description = "Collecting merchant id") @NotBlank @Size(max = 64) String payeeMerchantId,
        @Schema(description = "Payment amount in fen", example = "100") @Positive long amountMinor,
        @Schema(description = "Currency", example = "CNY") @NotBlank @Pattern(regexp = "CNY") String currency,
        @Schema(description = "Original action input key") @NotBlank @Size(max = 128) String inputKey,
        @Schema(description = "Original channel and payment method") @NotNull @Valid PaymentChoiceDTO choice) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
