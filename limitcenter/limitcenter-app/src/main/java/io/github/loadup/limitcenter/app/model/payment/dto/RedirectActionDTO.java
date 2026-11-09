package io.github.loadup.limitcenter.app.model.payment.dto;


import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RedirectActionDTO(
        @Schema(description = "Redirect url") @NotBlank @Size(max = 8192) @DiagnosticHidden
        String redirectUrl) implements PaymentActionDataDTO {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
