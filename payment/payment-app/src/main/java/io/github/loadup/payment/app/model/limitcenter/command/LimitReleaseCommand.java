package io.github.loadup.payment.app.model.limitcenter.command;

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record LimitReleaseCommand(
        @Schema(description = "Operation id") @NotBlank @Size(max = 64)
        String operationId,

        @Schema(description = "Payment id") @NotBlank @Size(max = 64)
        String paymentId,

        @Schema(description = "Payment version") @Positive long paymentVersion,

        @Schema(description = "Business event id") @NotBlank @Size(max = 64)
        String businessEventId,

        @Schema(description = "No collection evidence id") @NotBlank @Size(max = 64)
        String noCollectionEvidenceId,

        @Schema(description = "Reason code") @NotBlank @Size(max = 64)
        String reasonCode) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
