package io.github.loadup.limitcenter.app.model.payment.dto;


import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.limitcenter.app.model.payment.enums.PaymentActionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;

public record PaymentActionDTO(
        @Schema(description = "Type") PaymentActionType type,
        @Schema(description = "Expires at") OffsetDateTime expiresAt,

        @Schema(description = "Data") @Valid @DiagnosticHidden
        PaymentActionDataDTO data) {
    public PaymentActionDTO {
        if (expiresAt != null) {
            expiresAt = expiresAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
