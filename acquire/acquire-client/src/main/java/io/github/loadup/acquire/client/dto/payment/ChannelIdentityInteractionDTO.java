package io.github.loadup.acquire.client.dto.payment;


import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.components.payment.channel.api.model.PayerIdentityType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChannelIdentityInteractionDTO(
        @Schema(description = "Channel") @NotNull Channel channel,

        @Schema(
                description = "Resolved channel payer identifier; never an OAuth authorization code",
                accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank
        @Size(max = 128)
        @DiagnosticHidden
        String identifier,

        @Schema(description = "App scope") @NotBlank @Size(max = 64)
        String appScope,

        @Schema(description = "Resolved payer identifier type") @NotNull
        PayerIdentityType identityType)
        implements PaymentInteractionDTO {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
