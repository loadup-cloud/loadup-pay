package io.github.loadup.payment.app.model.limitcenter.dto;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record LimitExecutionPermitDTO(
        @Schema(description = "Operation id") @Size(max = 64)
        String operationId,

        @Schema(description = "Payment id") @Size(max = 64) String paymentId,
        @Schema(description = "Attempt id") @Size(max = 64) String attemptId,
        @Schema(description = "Execution epoch") long executionEpoch,

        @Schema(description = "Permit") @Size(max = 64) @DiagnosticHidden
        String permit) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
