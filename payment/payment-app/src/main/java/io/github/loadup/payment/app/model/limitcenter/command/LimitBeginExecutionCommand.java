package io.github.loadup.payment.app.model.limitcenter.command;

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LimitBeginExecutionCommand(
        @Schema(description = "Operation id") @NotBlank @Size(max = 64)
        String operationId,

        @Schema(description = "Attempt id") @NotBlank @Size(max = 64)
        String attemptId,

        @Schema(description = "Request key") @NotBlank @Size(max = 128)
        String requestKey,

        @Schema(description = "Previous attempt evidence id") @Size(max = 64)
        String previousAttemptEvidenceId) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
