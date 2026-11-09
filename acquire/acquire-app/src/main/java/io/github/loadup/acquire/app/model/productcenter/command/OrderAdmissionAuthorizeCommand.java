package io.github.loadup.acquire.app.model.productcenter.command;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.time.BusinessTime;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record OrderAdmissionAuthorizeCommand(
        @Schema(description = "Order id") @NotBlank @Size(max = 64)
        String orderId,

        @Schema(description = "Payee merchant id") @NotBlank @Size(max = 64)
        String payeeMerchantId,

        @Schema(description = "Product scope key") @NotBlank @Size(max = 128)
        String productScopeKey,

        @Schema(description = "Canonical lower-case SHA-256 request digest") @NotBlank @Pattern(regexp = "[0-9a-f]{64}")
        String requestDigest,

        @Schema(description = "Fixed total order amount in CNY fen") @Positive
        long amountMinor,

        @Schema(description = "Currency") @NotBlank @Size(max = 64) @Pattern(regexp = "CNY")
        String currency,

        @Schema(description = "Requested order checkpoint with an explicit offset and at most microsecond precision")
        OffsetDateTime requestedDueAt) {
    public OrderAdmissionAuthorizeCommand {
        if (requestedDueAt != null) {
            requestedDueAt =
                    requestedDueAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
