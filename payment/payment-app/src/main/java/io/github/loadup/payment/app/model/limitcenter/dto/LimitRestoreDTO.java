package io.github.loadup.payment.app.model.limitcenter.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.payment.app.model.limitcenter.enums.LimitRestoreResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record LimitRestoreDTO(
        @Schema(description = "Original operation id") String operationId,
        @Schema(description = "Original payment id") String paymentId,
        @Schema(description = "Refund id") @Size(max = 64) String refundId,

        @Schema(description = "Original successful refund version")
        long refundVersion,

        @Schema(description = "Original business event id") String businessEventId,
        @Schema(description = "Original refund evidence id") String refundEvidenceId,

        @Schema(description = "Originally delivered cumulative payment refund")
        long cumulativePaymentRefundedMinor,

        @Schema(description = "Verified cumulative payment refund accepted")
        long verifiedPaymentRefundedMinor,

        @Schema(description = "Verified refund-capacity source version")
        long verifiedPaymentRefundVersion,

        @Schema(description = "Result") LimitRestoreResult result,

        @Schema(description = "Logical payment amount restored; not the sum across time windows")
        long restoredAmountMinor,

        @Schema(description = "Restored payment count") boolean restoredPaymentCount) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
