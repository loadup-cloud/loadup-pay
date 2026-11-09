/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.productcenter.client.command;

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record ContractSignCommand(
        @Schema(description = "Merchant identifier") @NotBlank @Size(max = 64) String merchantId,

        @Schema(description = "Scope key") @NotBlank @Size(max = 128) String scopeKey,

        @Schema(description = "Plan version id") @NotBlank String planVersionId,

        @Schema(description = "Idempotency key") @NotBlank @Size(max = 128) String requestKey,

        @Schema(description = "Selected items") @NotNull @Size(max = 256) Set<String> selectedItems,

        @Schema(description = "Values") @NotNull Map<String, Map<String, String>> values,
        @Schema(description = "Effective from") Instant effectiveFrom,
        @Schema(description = "Effective to") Instant effectiveTo) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
