package io.github.loadup.acquire.app.model.payment.command;

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ParentPaymentFenceCommand(
        @Schema(description = "Order id") @NotBlank @Size(max = 64)
        String orderId,

        @Schema(description = "Payee merchant id") @NotBlank @Size(max = 64)
        String payeeMerchantId,

        @Schema(description = "Close epoch") @Positive long closeEpoch,

        @Schema(description = "Request key") @NotBlank @Size(max = 128)
        String requestKey) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
