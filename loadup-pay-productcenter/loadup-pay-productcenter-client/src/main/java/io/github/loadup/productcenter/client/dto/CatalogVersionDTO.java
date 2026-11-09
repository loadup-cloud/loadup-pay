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
import java.time.LocalDateTime;
import java.util.Map;

public record CatalogVersionDTO(
        @Schema(description = "Resource identifier") String id,
        @Schema(description = "Task or catalog category") String kind,
        @Schema(description = "Business code") String code,
        @Schema(description = "Version") int version,
        @Schema(description = "Current lifecycle status") String status,

        @Schema(description = "Current optimistic concurrency version")
        long rowVersion,

        @Schema(description = "Definition") Map<String, Object> definition,
        @Schema(description = "Updated by") String updatedBy,

        @Schema(description = "Creation time in the application local time zone")
        LocalDateTime createdAt,

        @Schema(description = "Last update time in the application local time zone")
        LocalDateTime updatedAt) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
