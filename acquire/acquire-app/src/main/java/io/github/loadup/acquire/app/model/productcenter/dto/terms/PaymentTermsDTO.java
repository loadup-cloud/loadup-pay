package io.github.loadup.acquire.app.model.productcenter.dto.terms;

import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

public record PaymentTermsDTO(
        @Schema(description = "Min amount minor") @Positive long minAmountMinor,
        @Schema(description = "Max amount minor") @Positive long maxAmountMinor,

        @Schema(description = "Max successful payments") @Positive
        Integer maxSuccessfulPayments,

        @Schema(description = "Allow exact remainder below min")
        boolean allowExactRemainderBelowMin) {
    public PaymentTermsDTO {
        DomainViolation.require(
                minAmountMinor > 0
                        && maxAmountMinor >= minAmountMinor
                        && (maxSuccessfulPayments == null || maxSuccessfulPayments > 0),
                DomainViolation.Reason.INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
