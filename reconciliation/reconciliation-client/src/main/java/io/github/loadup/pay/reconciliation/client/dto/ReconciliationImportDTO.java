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
package io.github.loadup.pay.reconciliation.client.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationImportState;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationSourceKind;
import io.swagger.v3.oas.annotations.media.Schema;

public record ReconciliationImportDTO(
        @Schema(description = "Import id") String importId,
        @Schema(description = "Original batch") String batchId,
        @Schema(description = "Frozen source; manual upload is not verified channel download") ReconciliationSourceKind sourceKind,
        @Schema(description = "Owned source file") String fileId,
        @Schema(description = "Original SHA-256") String sourceSha256,
        @Schema(description = "Original durable transfer task") String taskId,
        @Schema(description = "Native transfer state, independent of logical sealing") String taskStatus,
        @Schema(description = "Logical import lifecycle") ReconciliationImportState state,
        @Schema(description = "Sealed artifact, absent before successful commit") String artifactId,
        @Schema(description = "Stable rejection code; no raw parser or file error") String rejectionCode,
        @Schema(description = "Import version") long version) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
