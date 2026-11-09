package io.github.loadup.acquire.app.model.payment.dto;

import io.github.loadup.acquire.client.dto.payment.PaymentExecutionDTO;
import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * A consistent authoritative payment and its persistent execution gate.
 */
public record PaymentOutcomeDTO(
        @Schema(description = "Authoritative persisted payment") @NotNull @Valid
        PaymentExecutionDTO payment,

        @Schema(description = "The payment execution gate is persistently fenced")
        boolean executionFenced) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
