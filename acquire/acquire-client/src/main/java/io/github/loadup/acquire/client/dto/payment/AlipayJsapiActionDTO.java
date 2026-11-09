package io.github.loadup.acquire.client.dto.payment;


import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlipayJsapiActionDTO(
        @Schema(description = "Alipay trade number returned by the trusted channel")
        @NotBlank
        @Size(max = 128)
        @DiagnosticHidden
        String tradeNo)
        implements PaymentActionDataDTO {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
