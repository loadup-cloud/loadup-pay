package io.github.loadup.payment.app.model.limitcenter.command;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record LimitExecutionVerifyCommand(
        @Schema(description = "Original limit operation identifier") @NotBlank @Size(max = 64)
        String operationId,

        @Schema(description = "Authoritative payment identifier") @NotBlank @Size(max = 64)
        String paymentId,

        @Schema(description = "Current channel attempt identifier") @NotBlank @Size(max = 64)
        String attemptId,

        @Schema(description = "Current execution generation") @Positive
        long executionEpoch,

        @Schema(description = "Fixed payment amount in CNY minor units") @Positive
        long amountMinor,

        @Schema(description = "Payment currency") @NotBlank @Pattern(regexp = "CNY")
        String currency,

        @Schema(
                description = "Opaque execution permit",
                accessMode = Schema.AccessMode.WRITE_ONLY,
                example = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        @NotBlank
        @Pattern(regexp = "[0-9a-f]{64}")
        @DiagnosticHidden
        String permit) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
