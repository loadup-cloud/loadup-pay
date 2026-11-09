package io.github.loadup.productcenter.app.model.merchantcenter.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;

public record MerchantProfileDTO(
        @Schema(description = "Resource identifier") String id,

        @Schema(description = "Merchant code") String merchantCode,
        @Schema(description = "Type") String type,
        @Schema(description = "Industry") String industry,

        @Schema(description = "ISO 3166-1 alpha-2 country code")
        String country,

        @Schema(description = "Province") String province,
        @Schema(description = "City") String city,
        @Schema(description = "Active") boolean active) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
