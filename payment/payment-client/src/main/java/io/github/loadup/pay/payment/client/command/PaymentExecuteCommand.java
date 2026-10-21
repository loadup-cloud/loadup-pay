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

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.pay.product.client.dto.AdmittedOrderTermsDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PaymentExecuteCommand(
        @Schema(description = "Payment id") @NotBlank @Size(max = 64)
        String paymentId,

        @Schema(description = "Order id") @NotBlank @Size(max = 64)
        String orderId,

        @Schema(description = "Allocation id") @NotBlank @Size(max = 64)
        String allocationId,

        @Schema(description = "Admission id") @NotBlank @Size(max = 64)
        String admissionId,

        @Schema(description = "Payee merchant id") @NotBlank @Size(max = 64)
        String payeeMerchantId,

        @Schema(description = "Amount minor") @Positive long amountMinor,

        @Schema(description = "Currency") @NotBlank @Size(max = 64) @Pattern(regexp = "CNY")
        String currency,

        @Schema(description = "Request digest") @NotBlank @Size(max = 64)
        String requestDigest,

        @Schema(description = "Choice") @NotNull @Valid PaymentChoiceDTO choice,

        @Schema(description = "Prepared input ref") @Size(max = 64) @DiagnosticHidden
        String preparedInputRef,

        @Schema(description = "Admitted terms") @NotNull @Valid
        AdmittedOrderTermsDTO admittedTerms) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
