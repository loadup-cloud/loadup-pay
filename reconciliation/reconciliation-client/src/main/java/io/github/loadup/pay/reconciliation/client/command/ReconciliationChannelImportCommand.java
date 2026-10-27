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
import jakarta.validation.constraints.*;

/** Manual CHANNEL file only; no caller-supplied account, parser, totals or ledger-completeness assertions. */
public record ReconciliationChannelImportCommand(
        @Schema(description = "Original batch id") @NotBlank @Size(max = 64) String batchId,
        @Schema(description = "Caller idempotency key") @NotBlank @Size(max = 128) String requestKey,
        @Schema(description = "Owned immutable FileResource id") @NotBlank @Size(max = 64) String fileId,
        @Schema(description = "Expected original bytes SHA-256 returned by upload; not channel provenance") @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String sourceSha256) {
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
