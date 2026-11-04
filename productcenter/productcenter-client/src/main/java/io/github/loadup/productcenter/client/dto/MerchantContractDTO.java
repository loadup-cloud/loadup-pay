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
package io.github.loadup.productcenter.client.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

public record MerchantContractDTO(
        @Schema(description = "Resource identifier") String id,
        @Schema(description = "Merchant identifier") String merchantId,
        @Schema(description = "Scope key") String scopeKey,
        @Schema(description = "Plan version id") String planVersionId,
        @Schema(description = "Current lifecycle status") String status,
        @Schema(description = "Generation") long generation,

        @Schema(description = "Creation time in the application local time zone")
        LocalDateTime createdAt,

        @Schema(description = "Last update time in the application local time zone")
        LocalDateTime updatedAt,

        @Schema(description = "Revision") int revision,
        @Schema(description = "Effective from") Instant effectiveFrom,
        @Schema(description = "Effective to") Instant effectiveTo,
        @Schema(description = "Snapshot hash") String snapshotHash,
        @Schema(description = "Entries on the current page") Map<String, ContractItemDTO> items) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
