package io.github.loadup.acquire.client.dto.payment;


import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.contracts.event.payment.PaymentState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record PaymentExecutionDTO(
        @Schema(description = "Payment id") @Size(max = 64) String paymentId,
        @Schema(description = "Order id") @Size(max = 64) String orderId,

        @Schema(description = "Allocation id") @Size(max = 64)
        String allocationId,

        @Schema(description = "Amount minor") long amountMinor,

        @Schema(description = "Currency") @Size(max = 64) @Pattern(regexp = "CNY")
        String currency,

        @Schema(description = "State") PaymentState state,

        @Schema(description = "Active attempt id") @Size(max = 64)
        String activeAttemptId,

        @Schema(description = "Action") @Valid PaymentActionDTO action,

        @Schema(description = "Terminal evidence id") @Size(max = 64)
        String terminalEvidenceId,

        @Schema(description = "Version") long version,
        @Schema(description = "Initiated at") OffsetDateTime initiatedAt) {
    public PaymentExecutionDTO {
        if (initiatedAt != null) {
            initiatedAt = initiatedAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
