package io.github.loadup.productcenter.app.model.limitcenter.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.productcenter.app.model.limitcenter.enums.LimitMetric;
import io.github.loadup.productcenter.app.model.limitcenter.enums.LimitWindowType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record LimitRuleDTO(
        @Schema(description = "Rule scope id") @Size(max = 64) @NotBlank
        String ruleScopeId,

        @Schema(description = "Merchant id") @Size(max = 64) @NotBlank
        String merchantId,

        @Schema(description = "Metric") @NotNull LimitMetric metric,

        @Schema(description = "Currency") @Size(max = 64) @Pattern(regexp = "CNY") @NotBlank
        String currency,

        @Schema(description = "Window") @NotNull LimitWindowType window,
        @Schema(description = "Week start") @Min(1) @Max(7) int weekStart,
        @Schema(description = "Threshold") @PositiveOrZero long threshold,
        @Schema(description = "Enforcement enabled") boolean enforcementEnabled,

        @Schema(description = "Metering enabled") @AssertTrue
        boolean meteringEnabled) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
