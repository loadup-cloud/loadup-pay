package io.github.loadup.productcenter.app.model.merchantcenter.dto;


import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record MerchantEligibilityDTO(
        @Schema(description = "Allowed") boolean allowed,
        @Schema(description = "Reason code") @Size(max = 64) String reasonCode,
        @Schema(description = "Merchant") @Valid MerchantProfileDTO merchant,
        @Schema(description = "Merchant fact version") long merchantFactVersion,
        @Schema(description = "Grant id") @Size(max = 64) String grantId,
        @Schema(description = "Grant version") Long grantVersion) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
