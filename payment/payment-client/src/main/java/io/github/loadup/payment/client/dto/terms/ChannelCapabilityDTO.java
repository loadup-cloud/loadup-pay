package io.github.loadup.payment.client.dto.terms;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record ChannelCapabilityDTO(
        @Schema(description = "Channel") @NotNull Channel channel,
        @Schema(description = "Access mode") @NotNull AccessMode accessMode,
        @Schema(description = "Methods") @Valid @NotEmpty Set<PaymentMethod> methods) {
    public ChannelCapabilityDTO {
        methods = methods == null ? Set.of() : Set.copyOf(methods);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
