package io.github.loadup.productcenter.app.model.merchantcenter.command;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.productcenter.app.model.merchantcenter.enums.MerchantAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MerchantActionAuthorizeCommand(
        @Schema(description = "Payee merchant id") @NotBlank @Size(max = 64)
        String payeeMerchantId,

        @Schema(description = "Action") @NotNull MerchantAction action) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
