package io.github.loadup.payment.app.model.acquire.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;

public record PaymentOutcomeAckDTO(
        @Schema(description = "Original target delivery id") String deliveryId,

        @Schema(description = "Original payment business event")
        String businessEventId,

        @Schema(description = "Original payment id") String paymentId,

        @Schema(description = "Authoritative terminal payment version")
        long paymentVersion,

        @Schema(description = "Authoritative terminal evidence")
        String evidenceId,

        @Schema(description = "Order version observed in the committed receipt")
        long orderVersion,

        @Schema(description = "The original event was durably accepted")
        boolean accepted) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
