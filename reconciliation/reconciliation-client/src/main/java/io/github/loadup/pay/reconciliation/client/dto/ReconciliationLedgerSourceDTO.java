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
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Source-owned capture status; this is not an attached ledger input or a completed reconciliation")
public record ReconciliationLedgerSourceDTO(
        @Schema(description = "Original payment capture id") String captureId,
        @Schema(description = "INIT, SEALED or BLOCKED") String state,
        @Schema(description = "Blocker code when BLOCKED; no financial state change") String blocker,
        @Schema(description = "One blocker sample identity, not an exhaustive difference list") String blockerSubjectId,
        @Schema(description = "Original source task id") String nativeTaskId,
        @Schema(description = "Independent native task status") String nativeStatus) {
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
