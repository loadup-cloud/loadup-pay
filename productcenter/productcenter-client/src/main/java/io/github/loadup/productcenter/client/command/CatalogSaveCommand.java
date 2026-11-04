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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CatalogSaveCommand(
        @Schema(description = "Resource identifier") String id,

        @Schema(description = "Expected version for optimistic concurrency")
        Long expectedRowVersion,

        @Schema(description = "Task or catalog category") @NotBlank String kind,

        @Schema(description = "Business code") @NotBlank @Size(max = 128) String code,

        @Schema(description = "Version") @Min(1) int version,

        @Schema(description = "Definition") @NotNull @Size(max = 64) Map<String, Object> definition) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
