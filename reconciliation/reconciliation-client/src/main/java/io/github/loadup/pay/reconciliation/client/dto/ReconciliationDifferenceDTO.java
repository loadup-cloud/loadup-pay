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

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationDifferenceReason;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationRecordKind;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

public record ReconciliationDifferenceDTO(
        @Schema(description = "Stored difference id") String differenceId,
        @Schema(description = "Owning batch id") String batchId,
        @Schema(description = "Payment or child refund") ReconciliationRecordKind kind,
        @Schema(description = "Original channel payment transaction id") @DiagnosticHidden String channelTransactionNo,
        @Schema(description = "Original merchant child-refund request number; absent for payment") @DiagnosticHidden String channelRefundNo,
        @Schema(description = "All observed difference reasons") Set<ReconciliationDifferenceReason> reasons,
        @Schema(description = "Channel-side evidence; absent only for ledger-only records") ReconciliationLineDTO channel,
        @Schema(description = "Local authoritative evidence; absent only for channel-only records") ReconciliationLineDTO ledger) {
    public ReconciliationDifferenceDTO {
        reasons = Set.copyOf(reasons);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
