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
package io.github.loadup.pay.acquire.client.command;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/** Resumes only the current native wake-up; never grants a new business attempt budget. */
public record MerchantNotificationWakeCommand(
        @Schema(description = "Original notification delivery id") @NotBlank @Size(max = 64) String id,
        @Schema(description = "Stable operation key; retries retain all original parameters") @NotBlank @Size(max = 128) String requestKey,
        @Schema(description = "Observed delivery rowVersion precondition") @NotNull @PositiveOrZero Long expectedVersion,
        @Schema(description = "Audited operational reason") @NotBlank @Size(max = 500) @DiagnosticHidden String reason) {
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
