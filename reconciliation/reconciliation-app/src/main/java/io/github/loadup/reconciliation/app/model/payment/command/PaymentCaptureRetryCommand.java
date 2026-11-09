package io.github.loadup.reconciliation.app.model.payment.command;

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentCaptureRetryCommand(
        @Schema(description = "Original capture id") @NotBlank @Size(max = 64)
        String captureId,

        @Schema(description = "Expected original native task id") @NotBlank @Size(max = 64)
        String expectedTaskId) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
