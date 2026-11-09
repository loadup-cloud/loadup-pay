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

public record ContractDecisionDTO(
        @Schema(description = "Allowed") boolean allowed,
        @Schema(description = "Reason") String reason,
        @Schema(description = "Contract id") String contractId,
        @Schema(description = "Revision") Integer revision,
        @Schema(description = "Snapshot hash") String snapshotHash,
        @Schema(description = "Configuration") ConfigurationDTO configuration) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
