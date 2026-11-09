package io.github.loadup.acquire.app.model.payment.dto;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.time.BusinessTime;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record PaymentInputDTO(
        @Schema(description = "Input ref") @Size(max = 64) @DiagnosticHidden
        String inputRef,

        @Schema(description = "Input digest") @Size(max = 64) @DiagnosticHidden
        String inputDigest,

        @Schema(description = "Expires at") OffsetDateTime expiresAt) {
    public PaymentInputDTO {
        if (expiresAt != null) {
            expiresAt = expiresAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
