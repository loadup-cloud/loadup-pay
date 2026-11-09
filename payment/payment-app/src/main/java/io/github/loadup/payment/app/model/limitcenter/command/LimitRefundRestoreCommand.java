package io.github.loadup.payment.app.model.limitcenter.command;

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record LimitRefundRestoreCommand(
        @Schema(description = "Operation id") @NotBlank @Size(max = 64)
        String operationId,

        @Schema(description = "Payment id") @NotBlank @Size(max = 64)
        String paymentId,

        @Schema(description = "Refund id") @NotBlank @Size(max = 64)
        String refundId,

        @Schema(description = "Refund version") @Positive long refundVersion,

        @Schema(description = "Business event id") @NotBlank @Size(max = 64)
        String businessEventId,

        @Schema(description = "Refund amount minor") @Positive
        long refundAmountMinor,

        @Schema(description = "Cumulative payment refunded minor") @Positive
        long cumulativePaymentRefundedMinor,

        @Schema(description = "Currency") @NotBlank @Size(max = 64) @Pattern(regexp = "CNY")
        String currency,

        @Schema(description = "Refund evidence id") @NotBlank @Size(max = 64)
        String refundEvidenceId) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
