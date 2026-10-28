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
package io.github.loadup.pay.reconciliation.client.query;

import io.github.loadup.commons.dto.PageQuery;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.reconciliation.client.enums.ReconciliationDifferenceReason;
import io.github.loadup.pay.reconciliation.client.enums.ReconciliationRecordKind;
import io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReconciliationDifferenceQuery(
        @Schema(description = "Owning batch id") @NotBlank @Size(max = 64) String batchId,
        @Schema(description = "Optional payment/refund filter") ReconciliationRecordKind kind,
        @Schema(description = "Optional difference reason filter") ReconciliationDifferenceReason reason,
        @Schema(description = "Page number, default 1", minimum = "1") Integer page,
        @Schema(description = "Page size, default 20", minimum = "1", maximum = "100") Integer size) {
    public ReconciliationDifferenceQuery {
        var normalized = PageQuery.normalized(page, size, ReconciliationResultCodeEnum.INVALID_PAGE);
        page = normalized.page();
        size = normalized.size();
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
