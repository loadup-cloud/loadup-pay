package io.github.loadup.reconciliation.app.model.payment.command;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.payment.Channel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PaymentCaptureCreateCommand(
        @Schema(description = "Stable source capture request key") @NotBlank @Size(max = 128)
        String requestKey,

        @Schema(description = "Statement channel") @NotNull Channel channel,

        @Schema(description = "Actual collecting-account SHA-256") @NotBlank @Pattern(regexp = "[0-9a-f]{64}")
        String accountScopeHash,

        @Schema(description = "Channel statement date") @NotNull
        LocalDate billDate,

        @Schema(description = "Provider-owned statement profile") @NotBlank @Size(max = 64)
        String statementProfile) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
