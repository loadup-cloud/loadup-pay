package io.github.loadup.acquire.app.model.payment.dto;


import io.github.loadup.acquire.app.model.payment.enums.PaymentInputExpiryDisposition;
import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentInputExpiryDTO(
        @Schema(description = "Original payment id") @NotBlank
        String paymentId,

        @Schema(description = "Original order id") @NotBlank String orderId,

        @Schema(description = "Atomic input/payment resolution, independent of financial success") @NotNull
        PaymentInputExpiryDisposition disposition,

        @Schema(description = "Durable never-accepted closure proof; present only for EXPIRED_CLOSED") @Valid
        PaymentCloseDTO closed) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
