/*
 * #%L
 * LoadUp Pay Reconciliation Client
 * %%
 * Copyright (C) 2025 - 2026 LoadUp Cloud
 * %%
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
 * #L%
 */
package io.github.loadup.pay.reconciliation.client.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import static io.github.loadup.pay.reconciliation.client.support.ReconciliationContracts.require;
import static io.github.loadup.pay.reconciliation.client.support.ReconciliationContracts.text;
import static io.github.loadup.pay.reconciliation.client.support.ReconciliationContracts.hash;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record ReconciliationBatchDTO(
        @Schema(description = "Batch id") String batchId,
        @Schema(description = "Channel") Channel channel,

        @Schema(description = "Actual collecting-account scope hash")
        String accountScopeHash,

        @Schema(description = "Channel statement date", format = "date")
        LocalDate billDate,

        @Schema(description = "Trusted statement and date-basis profile revision")
        String statementProfile,

        @Schema(description = "Batch lifecycle state") ReconciliationState state,

        @Schema(description = "Frozen channel input; null until sealed")
        ReconciliationInputDTO channelInput,

        @Schema(description = "Frozen authoritative local input; null until sealed")
        ReconciliationInputDTO ledgerInput,

        @Schema(description = "Original durable run id; not execution permission")
        String runId,

        @Schema(description = "Matched count; null before completion")
        Long matchedCount,

        @Schema(description = "Difference count; null before completion")
        Long differenceCount,

        @Schema(description = "Deterministic result SHA-256; null before completion")
        String resultHash,

        @Schema(description = "Batch aggregate version") @com.fasterxml.jackson.annotation.JsonProperty(required = true)
        @com.fasterxml.jackson.annotation.JsonSetter(nulls = com.fasterxml.jackson.annotation.Nulls.FAIL)
        long version,

        @Schema(description = "Native task id; populated in detail after start")
        String taskId,

        @Schema(description = "Native task status; populated in detail after start")
        String nativeStatus) {
    public ReconciliationBatchDTO {
        text(batchId, 64);
        text(statementProfile, 64);
        hash(accountScopeHash);
        require((channel == Channel.WECHAT || channel == Channel.ALIPAY) && billDate != null
                && billDate.getYear() >= 1970 && billDate.getYear() <= 9998 && state != null && version >= 0);
        require((taskId != null) == (nativeStatus != null) && (taskId == null || runId != null));
        if (taskId != null) {
            text(taskId, 64);
            require(java.util.Set.of("PENDING", "RUNNING", "SUCCEEDED", "FAILED", "CANCELLED").contains(nativeStatus));
        }
        boolean complete = state == ReconciliationState.MATCHED || state == ReconciliationState.DIFFERENCES;
        boolean started = complete || state == ReconciliationState.RUNNING;
        require(started == (runId != null) && complete == (matchedCount != null)
                && complete == (differenceCount != null) && complete == (resultHash != null)
                && (state == ReconciliationState.INIT) != (channelInput != null && ledgerInput != null));
        if (started) { text(runId, 64); }
        if (complete) {
            hash(resultHash);
            require(matchedCount >= 0 && differenceCount >= 0
                    && (state == ReconciliationState.MATCHED) == (differenceCount == 0));
        }
        if (channelInput != null && ledgerInput != null) {
            require(!channelInput.artifactId().equals(ledgerInput.artifactId())
                    && !channelInput.sourceCaptureId().equals(ledgerInput.sourceCaptureId()));
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
