package io.github.loadup.acquire.app.model.payment.query;

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ParentFenceStatusQuery(
        @Schema(description = "Order id") @NotBlank @Size(max = 64)
        String orderId,

        @Schema(description = "Close epoch") @Positive long closeEpoch) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
