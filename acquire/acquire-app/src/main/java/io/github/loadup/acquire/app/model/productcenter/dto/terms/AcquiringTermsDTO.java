package io.github.loadup.acquire.app.model.productcenter.dto.terms;


import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AcquiringTermsDTO(
        @Schema(description = "Schema version") @Positive int schemaVersion,
        @Schema(description = "Order") @Valid @NotNull OrderTermsDTO order,
        @Schema(description = "Payment") @Valid @NotNull PaymentTermsDTO payment,
        @Schema(description = "Fee") @Valid @NotNull FeeTermsDTO fee,
        @Schema(description = "Refund") @Valid @NotNull RefundTermsDTO refund,

        @Schema(description = "Notification") @Valid @NotNull
        NotificationTermsDTO notification,

        @Schema(description = "Limits") @Valid @NotNull LimitPolicyRefDTO limits,

        @Schema(description = "Capabilities") @Valid @NotEmpty
        List<ChannelCapabilityDTO> capabilities,

        @Schema(description = "Versioned routing profile reference") @NotBlank @Size(max = 128)
        String routingProfileRef,

        @Schema(description = "Approved cashier return target code") @NotBlank @Size(max = 128)
        String returnTargetCode) {
    public AcquiringTermsDTO {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
