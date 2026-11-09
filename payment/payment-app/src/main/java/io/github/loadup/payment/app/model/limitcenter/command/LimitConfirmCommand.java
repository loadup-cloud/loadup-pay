package io.github.loadup.payment.app.model.limitcenter.command;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.time.BusinessTime;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record LimitConfirmCommand(
        @Schema(description = "Operation id") @NotBlank @Size(max = 64)
        String operationId,

        @Schema(description = "Payment id") @NotBlank @Size(max = 64)
        String paymentId,

        @Schema(description = "Payment version") @Positive long paymentVersion,

        @Schema(description = "Business event id") @NotBlank @Size(max = 64)
        String businessEventId,

        @Schema(description = "Success evidence id") @NotBlank @Size(max = 64)
        String successEvidenceId,

        @Schema(description = "Amount minor") @Positive long amountMinor,

        @Schema(description = "Currency") @NotBlank @Size(max = 64) @Pattern(regexp = "CNY")
        String currency,

        @Schema(description = "Succeeded at") @NotNull OffsetDateTime succeededAt) {
    public LimitConfirmCommand {
        if (succeededAt != null) {
            succeededAt = succeededAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
