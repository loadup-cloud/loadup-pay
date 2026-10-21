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
package io.github.loadup.pay.product.client.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AdmittedOrderTermsDTO(
        @Schema(description = "Admission id") @Size(max = 64)
        String admissionId,

        @Schema(description = "Order id") @Size(max = 64) String orderId,

        @Schema(description = "Payee merchant id") @Size(max = 64)
        String payeeMerchantId,

        @Schema(description = "Order amount minor") @Positive
        long orderAmountMinor,

        @Schema(description = "Currency") @Size(max = 64) @Pattern(regexp = "CNY")
        String currency,

        @Schema(description = "Contract id") @Size(max = 64) String contractId,
        @Schema(description = "Revision") @Positive int revision,
        @Schema(description = "Generation") @PositiveOrZero long generation,

        @Schema(description = "Snapshot hash") @Size(max = 128)
        String snapshotHash,

        @Schema(description = "Compiled terms hash") @Size(max = 64)
        String compiledTermsHash,

        @Schema(description = "Terms") @Valid AcquiringTermsDTO terms) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
