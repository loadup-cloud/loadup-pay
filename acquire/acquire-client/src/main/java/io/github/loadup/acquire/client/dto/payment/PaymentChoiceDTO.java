package io.github.loadup.acquire.client.dto.payment;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record PaymentChoiceDTO(
        @Schema(description = "Channel") @NotNull Channel channel,
        @Schema(description = "Method") @NotNull PaymentMethod method) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
