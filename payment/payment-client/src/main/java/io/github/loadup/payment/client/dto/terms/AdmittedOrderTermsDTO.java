package io.github.loadup.payment.client.dto.terms;


import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AdmittedOrderTermsDTO(
        @Schema(description = "Admission id") @NotBlank @Size(max = 64)
        String admissionId,

        @Schema(description = "Order id") @NotBlank @Size(max = 64)
        String orderId,

        @Schema(description = "Payee merchant id") @NotBlank @Size(max = 64)
        String payeeMerchantId,

        @Schema(description = "Order amount minor") @Positive
        long orderAmountMinor,

        @Schema(description = "Currency") @NotBlank @Size(max = 64) @Pattern(regexp = "CNY")
        String currency,

        @Schema(description = "Contract id") @NotBlank @Size(max = 64)
        String contractId,

        @Schema(description = "Revision") @Positive int revision,
        @Schema(description = "Generation") @Positive long generation,

        @Schema(description = "Snapshot hash") @NotBlank @Size(max = 128)
        String snapshotHash,

        @Schema(description = "Compiled terms hash") @NotBlank @Pattern(regexp = "[0-9a-f]{64}")
        String compiledTermsHash,

        @Schema(description = "Terms") @NotNull @Valid AcquiringTermsDTO terms) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
