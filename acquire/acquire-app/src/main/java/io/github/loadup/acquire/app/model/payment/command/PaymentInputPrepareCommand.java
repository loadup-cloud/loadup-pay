package io.github.loadup.acquire.app.model.payment.command;

import io.github.loadup.acquire.client.dto.payment.PaymentChoiceDTO;
import io.github.loadup.acquire.client.dto.payment.PaymentInteractionDTO;
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Internal preparation after acquire has assigned stable order, allocation and payment identities.
 */
public record PaymentInputPrepareCommand(
        @Schema(description = "Fixed payment id") @NotBlank @Size(max = 64)
        String paymentId,

        @Schema(description = "Original order id") @NotBlank @Size(max = 64)
        String orderId,

        @Schema(description = "Fixed acquiring allocation id") @NotBlank @Size(max = 64)
        String allocationId,

        @Schema(description = "Collecting merchant id") @NotBlank @Size(max = 64)
        String payeeMerchantId,

        @Schema(description = "Payment amount in fen", example = "100") @Positive
        long amountMinor,

        @Schema(description = "Currency", example = "CNY") @NotBlank @Pattern(regexp = "CNY")
        String currency,

        @Schema(description = "Original action input key") @NotBlank @Size(max = 128)
        String inputKey,

        @Schema(description = "Channel and payment method") @NotNull @Valid
        PaymentChoiceDTO choice,

        @Schema(description = "Sensitive channel input", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotNull
        @Valid
        @DiagnosticHidden
        PaymentInteractionDTO interaction) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
