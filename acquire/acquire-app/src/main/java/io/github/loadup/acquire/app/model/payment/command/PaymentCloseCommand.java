package io.github.loadup.acquire.app.model.payment.command;

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PaymentCloseCommand(
        @Schema(description = "Payment id") @NotBlank @Size(max = 64)
        String paymentId,

        @Schema(description = "Order id") @NotBlank @Size(max = 64)
        String orderId,

        @Schema(description = "Payee merchant id") @NotBlank @Size(max = 64)
        String payeeMerchantId,

        @Schema(description = "Request key") @NotBlank @Size(max = 128)
        String requestKey,

        @Schema(description = "Fence epoch") @Positive long fenceEpoch,

        @Schema(description = "Reason code") @NotBlank @Size(max = 64)
        String reasonCode) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
