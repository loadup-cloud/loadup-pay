package io.github.loadup.acquire.app.model.productcenter.dto.terms;

import io.github.loadup.acquire.app.model.productcenter.enums.RefundAmountRestorePolicy;
import io.github.loadup.acquire.app.model.productcenter.enums.RefundCountRestorePolicy;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record LimitPolicyRefDTO(
        @Schema(description = "Policy id") @Size(max = 64) @NotBlank
        String policyId,

        @Schema(description = "Policy version") @Positive int policyVersion,

        @Schema(description = "Canonical lower-case SHA-256 policy hash") @Pattern(regexp = "[0-9a-f]{64}") @NotBlank
        String policyHash,

        @Schema(description = "Parent aggregation enabled") boolean parentAggregationEnabled,

        @Schema(description = "Versioned parent policy reference") @Size(max = 128)
        String parentPolicyRef,

        @Schema(description = "Amount restore") @NotNull RefundAmountRestorePolicy amountRestore,
        @Schema(description = "Count restore") @NotNull RefundCountRestorePolicy countRestore) {
    public LimitPolicyRefDTO {
        if (parentAggregationEnabled) {
            DomainChecks.text(parentPolicyRef, 128);
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
