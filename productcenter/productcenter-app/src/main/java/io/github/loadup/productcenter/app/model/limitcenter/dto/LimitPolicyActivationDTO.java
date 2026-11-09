package io.github.loadup.productcenter.app.model.limitcenter.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.time.BusinessTime;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public record LimitPolicyActivationDTO(
        @Schema(description = "Policy id") @Size(max = 64) String policyId,
        @Schema(description = "Policy version") int policyVersion,
        @Schema(description = "Policy hash") @Size(max = 64) String policyHash,
        @Schema(description = "Activated at") OffsetDateTime activatedAt) {
    public LimitPolicyActivationDTO {
        if (activatedAt != null) {
            activatedAt = activatedAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
