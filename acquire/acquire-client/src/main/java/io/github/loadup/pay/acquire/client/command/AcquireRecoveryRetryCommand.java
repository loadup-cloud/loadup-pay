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
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.acquire.client.enums.AcquireRecoveryKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record AcquireRecoveryRetryCommand(
        @Schema(description = "Allow-listed acquiring recovery task kind") @NotNull AcquireRecoveryKind kind,
        @Schema(description = "Original payment id for PAYMENT_ALLOCATION, otherwise original order id") @NotBlank @Size(max = 64) String subjectId,
        @Schema(description = "Observed business generation; deadline versions begin at one") @NotNull @PositiveOrZero Long generation,
        @Schema(description = "Observed native task id; replacement task ids are not accepted") @NotBlank @Size(max = 64) String expectedTaskId,
        @Schema(description = "Observed native failed-task attempts") @NotNull @Positive Integer expectedAttempts,
        @Schema(description = "Observed native nextExecuteAt with offset, microsecond precision", type = "string", format = "date-time")
        @NotNull OffsetDateTime expectedNextExecuteAt,
        @Schema(description = "Stable operation key; retries preserve all original parameters") @NotBlank @Size(max = 128) String requestKey,
        @Schema(description = "Audited operational reason") @NotBlank @Size(max = 500) @DiagnosticHidden String reason) {
    public AcquireRecoveryRetryCommand {
        if (expectedNextExecuteAt != null)
            expectedNextExecuteAt = expectedNextExecuteAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
