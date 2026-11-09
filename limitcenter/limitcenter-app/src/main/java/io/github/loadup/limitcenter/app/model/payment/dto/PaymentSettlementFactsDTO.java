package io.github.loadup.limitcenter.app.model.payment.dto;


import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PaymentSettlementFactsDTO(
        @Schema(description = "Authoritative payment snapshot") @NotNull @Valid
        PaymentExecutionDTO payment,

        @Schema(description = "Original merchant limit operation") @NotBlank @Size(max = 64)
        String limitOperationId,

        @Schema(description = "Collecting merchant") @NotBlank @Size(max = 64)
        String payeeMerchantId,

        @Schema(description = "The persistent single-payment execution gate is fenced")
        boolean executionFenced) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
