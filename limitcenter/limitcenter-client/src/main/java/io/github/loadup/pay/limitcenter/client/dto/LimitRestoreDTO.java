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
package io.github.loadup.pay.limitcenter.client.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.limitcenter.client.enums.LimitRestoreResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record LimitRestoreDTO(
        @Schema(description = "Original operation id") String operationId,
        @Schema(description = "Original payment id") String paymentId,
        @Schema(description = "Refund id") @Size(max = 64) String refundId,
        @Schema(description = "Original successful refund version") long refundVersion,
        @Schema(description = "Original business event id") String businessEventId,
        @Schema(description = "Original refund evidence id") String refundEvidenceId,
        @Schema(description = "Originally delivered cumulative payment refund") long cumulativePaymentRefundedMinor,
        @Schema(description = "Verified cumulative payment refund accepted") long verifiedPaymentRefundedMinor,
        @Schema(description = "Verified refund-capacity source version") long verifiedPaymentRefundVersion,
        @Schema(description = "Result") LimitRestoreResult result,
        @Schema(description = "Logical payment amount restored; not the sum across time windows") long restoredAmountMinor,
        @Schema(description = "Restored payment count") boolean restoredPaymentCount) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
