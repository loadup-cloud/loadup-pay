package io.github.loadup.payment.app.model.limitcenter.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.payment.app.model.limitcenter.enums.LimitReservationState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record LimitReservationDTO(
        @Schema(description = "Reservation id") @Size(max = 64)
        String reservationId,

        @Schema(description = "Operation id") @Size(max = 64)
        String operationId,

        @Schema(description = "Payment id") @Size(max = 64) String paymentId,
        @Schema(description = "State") LimitReservationState state,
        @Schema(description = "Policy id") @Size(max = 64) String policyId,
        @Schema(description = "Policy version") int policyVersion,
        @Schema(description = "Initiated at") OffsetDateTime initiatedAt,
        @Schema(description = "Version") long version) {
    public LimitReservationDTO {
        if (initiatedAt != null) {
            initiatedAt = initiatedAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
