package io.github.loadup.payment.app.model.limitcenter.dto;


import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

public record LimitReleaseDTO(
        @Schema(description = "Original operation id") String operationId,
        @Schema(description = "Original payment id") String paymentId,

        @Schema(description = "The operation is persistently fenced")
        boolean fenced,

        @Schema(description = "Current reservation; null when release preceded reserve") @Valid
        LimitReservationDTO reservation,

        @Schema(description = "Authoritative terminal payment version")
        long paymentVersion,

        @Schema(description = "Authoritative no-collection evidence")
        String noCollectionEvidenceId) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
