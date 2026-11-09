package io.github.loadup.acquire.client.dto.payment;


import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A short-lived code read by the merchant's scanner and supplied through the merchant API.
 */
public record PaymentCodeInteractionDTO(
        @Schema(
                description = "Payer payment code captured by the merchant; never returned in responses",
                accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank
        @Size(max = 4096)
        @DiagnosticHidden
        String paymentCode)
        implements PaymentInteractionDTO {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
