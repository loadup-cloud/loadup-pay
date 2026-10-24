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

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.channel.api.model.H5SceneType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record H5SceneInteractionDTO(
        @Schema(description = "Actual payer terminal IPv4 or IPv6 literal supplied by the authenticated merchant server; not a forwarding-header chain",
                accessMode = Schema.AccessMode.WRITE_ONLY, example = "203.0.113.10")
        @NotBlank @Size(max = 45) @DiagnosticHidden String payerClientIp,
        @Schema(description = "Payer environment", example = "WAP") @NotNull H5SceneType sceneType)
        implements PaymentInteractionDTO {
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
