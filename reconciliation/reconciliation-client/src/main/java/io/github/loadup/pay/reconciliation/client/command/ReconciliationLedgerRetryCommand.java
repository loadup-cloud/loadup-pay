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
package io.github.loadup.pay.reconciliation.client.command;

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReconciliationLedgerRetryCommand(
        @Schema(description = "Original owned batch") @NotBlank @Size(max = 64) String batchId,
        @Schema(description = "Original payment capture id") @NotBlank @Size(max = 64) String captureId,
        @Schema(description = "Expected original native task id") @NotBlank @Size(max = 64) String expectedTaskId) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
