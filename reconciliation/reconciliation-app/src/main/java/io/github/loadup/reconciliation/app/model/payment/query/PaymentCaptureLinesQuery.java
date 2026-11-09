package io.github.loadup.reconciliation.app.model.payment.query;

import io.github.loadup.commons.dto.PageQuery;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.reconciliation.app.model.payment.error.PaymentResultCodeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentCaptureLinesQuery(
        @Schema(description = "Original sealed capture") @NotBlank @Size(max = 64)
        String captureId,

        @Schema(description = "One-based immutable page") Integer page,
        @Schema(description = "Page size, at most 100") Integer size) {
    public PaymentCaptureLinesQuery {
        var normalized = PageQuery.normalized(page, size, PaymentResultCodeEnum.INVALID_ARGUMENT);
        page = normalized.page();
        size = normalized.size();
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
