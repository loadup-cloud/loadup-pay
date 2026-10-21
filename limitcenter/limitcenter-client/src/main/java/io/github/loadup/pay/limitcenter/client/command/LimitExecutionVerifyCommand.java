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
package io.github.loadup.pay.limitcenter.client.command;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record LimitExecutionVerifyCommand(
        @Schema(description = "Original limit operation identifier") @NotBlank @Size(max = 64)
        String operationId,

        @Schema(description = "Authoritative payment identifier") @NotBlank @Size(max = 64)
        String paymentId,

        @Schema(description = "Current channel attempt identifier") @NotBlank @Size(max = 64)
        String attemptId,

        @Schema(description = "Current execution generation") @Positive
        long executionEpoch,

        @Schema(description = "Fixed payment amount in CNY minor units") @Positive
        long amountMinor,

        @Schema(description = "Payment currency") @NotBlank @Pattern(regexp = "CNY")
        String currency,

        @Schema(
                description = "Opaque execution permit",
                accessMode = Schema.AccessMode.WRITE_ONLY,
                example = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        @NotBlank
        @Pattern(regexp = "[0-9a-f]{64}")
        @DiagnosticHidden
        String permit) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
