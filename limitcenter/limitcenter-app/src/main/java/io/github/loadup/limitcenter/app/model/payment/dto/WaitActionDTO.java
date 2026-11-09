package io.github.loadup.limitcenter.app.model.payment.dto;


import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;

public record WaitActionDTO(
        @Schema(description = "Recommended poll after millis")
        long recommendedPollAfterMillis) implements PaymentActionDataDTO {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
