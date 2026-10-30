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

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.time.BusinessTime;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record AcquireOrderExtendCommand(
        @Schema(description = "Original order id") @NotBlank @Size(max = 64)
        String id,

        @Schema(description = "Extension request key; retries retain the original parameters") @NotBlank @Size(max = 128)
        String requestKey,

        @Schema(description = "New deadline with explicit offset and at most six fractional second digits",
                type = "string", format = "date-time", example = "2026-10-22T18:30:00.123456+08:00")
        @NotNull OffsetDateTime requestedDueAt,

        @Schema(description = "Original order version precondition; do not replace it on a retry") @PositiveOrZero
        long expectedVersion) {
    public AcquireOrderExtendCommand {
        if (requestedDueAt != null) {
            requestedDueAt = requestedDueAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
