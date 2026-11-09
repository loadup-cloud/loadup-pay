package io.github.loadup.acquire.app.model.productcenter.dto;

import io.github.loadup.acquire.app.model.productcenter.dto.terms.AcquiringTermsDTO;
import io.github.loadup.acquire.app.model.productcenter.enums.AdmissionDecision;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.time.BusinessTime;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record OrderAdmissionDTO(
        @Schema(description = "Admission id") @Size(max = 64)
        String admissionId,

        @Schema(description = "Order id") @Size(max = 64) String orderId,

        @Schema(description = "Payee merchant id") @Size(max = 64)
        String payeeMerchantId,

        @Schema(description = "Decision") AdmissionDecision decision,
        @Schema(description = "Reason code") @Size(max = 64) String reasonCode,

        @Schema(description = "Request digest") @Size(max = 64)
        String requestDigest,

        @Schema(description = "Contract id") @Size(max = 64) String contractId,
        @Schema(description = "Revision") Integer revision,
        @Schema(description = "Generation") Long generation,

        @Schema(description = "Snapshot hash") @Size(max = 128)
        String snapshotHash,

        @Schema(description = "Compiled terms hash") @Size(max = 64)
        String compiledTermsHash,

        @Schema(description = "Admitted at") OffsetDateTime admittedAt,
        @Schema(description = "Materialize before") OffsetDateTime materializeBefore,
        @Schema(description = "Terms") @Valid AcquiringTermsDTO terms) {
    public OrderAdmissionDTO {
        if (admittedAt != null) {
            admittedAt = admittedAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
        }
        if (materializeBefore != null) {
            materializeBefore =
                    materializeBefore.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
