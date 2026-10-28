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

import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.product.domain.model.RefundAllocationPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RefundTermsDTO(
        @Schema(description = "Enabled") boolean enabled,
        @Schema(description = "Partial allowed") boolean partialAllowed,
        @Schema(description = "Window seconds") @Positive long windowSeconds,
        @Schema(description = "Allocation policy") @NotNull RefundAllocationPolicy allocationPolicy) {
    public RefundTermsDTO {
        DomainViolation.require(windowSeconds > 0 && allocationPolicy != null, DomainViolation.Reason.INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
