package io.github.loadup.payment.client.dto.terms;

import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.payment.client.enums.UnpaidOrderAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record OrderTermsDTO(
        @Schema(description = "Wait seconds") @Positive long waitSeconds,
        @Schema(description = "Max wait seconds") @Positive long maxWaitSeconds,
        @Schema(description = "Unpaid action") @NotNull UnpaidOrderAction unpaidAction,
        @Schema(description = "Extension allowed") boolean extensionAllowed,

        @Schema(description = "Extension max seconds") @PositiveOrZero
        long extensionMaxSeconds,

        @Schema(description = "Session seconds") @Positive long sessionSeconds) {
    public OrderTermsDTO {
        DomainViolation.require(
                waitSeconds > 0
                        && maxWaitSeconds >= waitSeconds
                        && sessionSeconds > 0
                        && extensionMaxSeconds >= 0
                        && (extensionAllowed ? extensionMaxSeconds > 0 : extensionMaxSeconds == 0)
                        && unpaidAction != null,
                DomainViolation.Reason.INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
