package io.github.loadup.acquire.app.model.payment.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record ParentFenceDTO(
        @Schema(description = "Order id") @Size(max = 64) String orderId,
        @Schema(description = "Close epoch") long closeEpoch,
        @Schema(description = "Fenced") boolean fenced,
        @Schema(description = "All children converged") boolean allChildrenConverged,
        @Schema(description = "Unknown count") int unknownCount) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
