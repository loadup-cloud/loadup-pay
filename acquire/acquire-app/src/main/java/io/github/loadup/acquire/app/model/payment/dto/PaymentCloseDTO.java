package io.github.loadup.acquire.app.model.payment.dto;

import io.github.loadup.acquire.client.dto.payment.PaymentExecutionDTO;
import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * An absent payment has a durable gate and receipt, never a fabricated zero-amount payment.
 */
public record PaymentCloseDTO(
        @Schema(description = "Payment id") @NotBlank @Size(max = 64)
        String paymentId,

        @Schema(description = "Order id") @NotBlank @Size(max = 64)
        String orderId,

        @Schema(description = "Current persisted fence epoch") @Positive
        long fenceEpoch,

        @Schema(description = "Execution is persistently fenced")
        boolean fenced,

        @Schema(description = "Current payment; null if it was never accepted") @Valid
        PaymentExecutionDTO payment,

        @Schema(description = "Trusted no-collection evidence; null while funds may still be collected") @Size(max = 64)
        String noCollectionEvidenceId) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
