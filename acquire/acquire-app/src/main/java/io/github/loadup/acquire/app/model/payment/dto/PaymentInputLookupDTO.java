package io.github.loadup.acquire.app.model.payment.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

/**
 * Missing input is not evidence that no payment exists or can be accepted later.
 */
public record PaymentInputLookupDTO(
        @Schema(description = "Whether the exact original input was observed") @JsonInclude(JsonInclude.Include.ALWAYS)
        boolean found,

        @Schema(description = "Original opaque reference, fingerprint and expiry; absent only when found is false")
        @Valid
        PaymentInputDTO input) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
