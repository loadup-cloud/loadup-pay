package io.github.loadup.acquire.app.model.realtime.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;

public record ProjectionAckDTO(
        @Schema(description = "Original target delivery") String deliveryId,
        @Schema(description = "Original business event") String businessEventId,
        @Schema(description = "Original producer") String producer,
        @Schema(description = "Source aggregate id") String aggregateId,
        @Schema(description = "Received source version") long sourceVersion,

        @Schema(description = "Projected version at acceptance; may exceed a stale received version")
        long projectedVersion,

        @Schema(description = "APPLIED, DUPLICATE or STALE") String disposition,

        @Schema(description = "Source event was durably accepted")
        boolean accepted) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
