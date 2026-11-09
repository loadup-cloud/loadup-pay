package io.github.loadup.acquire.client.dto.payment;


import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.components.payment.channel.api.model.H5SceneType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record H5SceneInteractionDTO(
        @Schema(
                description =
                        "Actual payer terminal IPv4 or IPv6 literal supplied by the authenticated merchant server; not"
                                + " a forwarding-header chain",
                accessMode = Schema.AccessMode.WRITE_ONLY,
                example = "203.0.113.10")
        @NotBlank
        @Size(max = 45)
        @DiagnosticHidden
        String payerClientIp,

        @Schema(description = "Payer environment", example = "WAP") @NotNull
        H5SceneType sceneType)
        implements PaymentInteractionDTO {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
