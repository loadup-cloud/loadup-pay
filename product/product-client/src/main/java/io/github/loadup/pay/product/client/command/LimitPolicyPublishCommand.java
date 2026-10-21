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
package io.github.loadup.pay.product.client.command;

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LimitPolicyPublishCommand(
        @Schema(description = "Payee owning the signed acquiring contract") @NotBlank @Size(max = 64)
        String payeeMerchantId,

        @Schema(description = "Signed contract product scope") @NotBlank @Size(max = 128)
        String productScopeKey,

        @Schema(description = "Expected immutable contract identifier") @NotBlank @Size(max = 64)
        String expectedContractId,

        @Schema(description = "Initial signed revision; currently only 1 is supported") @Min(1) @Max(1)
        int expectedRevision,

        @Schema(description = "Expected framework snapshot hash") @NotBlank @Size(max = 128)
        String expectedSnapshotHash) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
