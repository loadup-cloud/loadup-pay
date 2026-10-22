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

import io.github.loadup.commons.json.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record AlipayAccountIdentityDTO(
        @Schema(description = "Signing application id") @NotBlank @Size(max = 32) String appId,
        @Schema(description = "Actual collecting seller id") @NotBlank @Size(max = 32) String sellerId,
        @Schema(description = "JSAPI operating application id when distinct") @Size(max = 32) String operatorAppId,
        @Schema(description = "Versioned merchant authorization resource reference", accessMode = Schema.AccessMode.WRITE_ONLY)
        @Size(max = 128) @DiagnosticHidden String authorizationReference,
        @Schema(description = "Immutable authorization resource version", accessMode = Schema.AccessMode.WRITE_ONLY)
        @Size(max = 64) @DiagnosticHidden String authorizationVersion) implements ChannelAccountIdentityDTO {
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
