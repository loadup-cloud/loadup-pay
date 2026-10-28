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
package io.github.loadup.pay.acquire.client.dto;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.acquire.client.enums.AcquireRecoveryKind;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * The first immutable retry decision; never payment or closure completion evidence.
 */
public record AcquireRecoveryRetryDTO(
        @Schema(description = "Immutable operation receipt id") String id,
        @Schema(description = "Original operation key") String requestKey,
        @Schema(description = "Recovery task kind") AcquireRecoveryKind kind,
        @Schema(description = "Original payment or order id") String subjectId,
        @Schema(description = "Original task generation") long generation,
        @Schema(description = "Original native task id") String nativeTaskId,
        @Schema(description = "Authenticated operations service") String callerServiceId,
        @Schema(description = "RETRIED or NO_LONGER_NEEDED; neither is a financial outcome") String result,
        @Schema(description = "Audited operational reason") @DiagnosticHidden String reason,
        @Schema(description = "First accepted operation time") OffsetDateTime acceptedAt) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
