package io.github.loadup.payment.app.model.limitcenter.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;

public record LimitExecutionVerificationDTO(
        @Schema(description = "Whether the stored execution and permit match at this observation")
        boolean valid,

        @Schema(description = "VALID, NOT_EXECUTING or PERMIT_MISMATCH")
        String reasonCode) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
