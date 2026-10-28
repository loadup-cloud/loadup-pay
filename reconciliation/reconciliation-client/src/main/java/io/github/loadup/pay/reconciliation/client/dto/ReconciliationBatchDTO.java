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
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.reconciliation.client.enums.ReconciliationBatchState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

public record ReconciliationBatchDTO(
        @Schema(description = "Batch id") String batchId,
        @Schema(description = "Channel") Channel channel,
        @Schema(description = "Actual collecting-account scope hash") String accountScopeHash,
        @Schema(description = "Channel statement date", format = "date") LocalDate billDate,
        @Schema(description = "Trusted statement and date-basis profile revision") String statementProfile,
        @Schema(description = "Batch lifecycle state") ReconciliationBatchState state,
        @Schema(description = "Frozen channel input; null until sealed") ReconciliationInputDTO channelInput,
        @Schema(description = "Frozen authoritative local input; null until sealed") ReconciliationInputDTO ledgerInput,
        @Schema(description = "Original durable run id; not execution permission") String runId,
        @Schema(description = "Matched count; null before completion") Long matchedCount,
        @Schema(description = "Difference count; null before completion") Long differenceCount,
        @Schema(description = "Deterministic result SHA-256; null before completion") String resultHash,
        @Schema(description = "Batch aggregate version") long version) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
