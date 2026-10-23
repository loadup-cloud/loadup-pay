/*
 * Copyright 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Channel-specific invocation fields, never an untyped browser-supplied map.
 */
public record WechatJsapiActionDTO(
        @Schema(description = "WeChat application id") @NotBlank @Size(max = 64) String appId,
        @Schema(description = "Payment signature timestamp in epoch seconds") @Pattern(regexp = "[0-9]{1,20}") @NotNull String timeStamp,
        @Schema(description = "Payment signature nonce") @NotBlank @Size(max = 32) String nonceStr,
        @Schema(description = "Prepay package returned by the trusted channel") @NotBlank @Size(max = 256)
        @JsonProperty("package") @DiagnosticHidden String packageValue,
        @Schema(description = "Payment signature algorithm") @NotNull @Pattern(regexp = "RSA") String signType,
        @Schema(description = "Server-generated payment signature") @NotBlank @Size(max = 1024) @DiagnosticHidden String paySign)
        implements PaymentActionDataDTO {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
