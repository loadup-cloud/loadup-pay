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
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.product.domain.model.RefundAmountRestorePolicy;
import io.github.loadup.pay.product.domain.model.RefundCountRestorePolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record LimitPolicyRefDTO(
        @Schema(description = "Policy id") @Size(max = 64) @NotBlank
        String policyId,

        @Schema(description = "Policy version") @Positive int policyVersion,

        @Schema(description = "Canonical lower-case SHA-256 policy hash") @Pattern(regexp = "[0-9a-f]{64}") @NotBlank
        String policyHash,

        @Schema(description = "Parent aggregation enabled") boolean parentAggregationEnabled,

        @Schema(description = "Versioned parent policy reference") @Size(max = 128)
        String parentPolicyRef,

        @Schema(description = "Amount restore") @NotNull RefundAmountRestorePolicy amountRestore,
        @Schema(description = "Count restore") @NotNull RefundCountRestorePolicy countRestore) {
    public LimitPolicyRefDTO {
        if (parentAggregationEnabled) {
            DomainChecks.text(parentPolicyRef, 128);
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
