package io.github.loadup.acquire.app.model.productcenter.dto.terms;

import io.github.loadup.acquire.app.model.productcenter.enums.RefundAllocationPolicy;
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RefundTermsDTO(
        @Schema(description = "Enabled") boolean enabled,
        @Schema(description = "Partial allowed") boolean partialAllowed,
        @Schema(description = "Window seconds") @Positive long windowSeconds,
        @Schema(description = "Allocation policy") @NotNull RefundAllocationPolicy allocationPolicy) {
    public RefundTermsDTO {
        DomainViolation.require(windowSeconds > 0 && allocationPolicy != null, DomainViolation.Reason.INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
