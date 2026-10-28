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

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Immutable acknowledgement of the original operation, not a command to alter payment state.
 */
public record CheckoutSessionRevocationDTO(
        @Schema(description = "Revocation audit receipt id") String id,
        @Schema(description = "Original session id") String sessionId,
        @Schema(description = "Original order id") String orderId,
        @Schema(description = "Original request key") String requestKey,
        @Schema(description = "Audited reason") @DiagnosticHidden String reason,
        @Schema(description = "Whether this operation first revoked the capability") @JsonInclude(JsonInclude.Include.ALWAYS) boolean newlyRevoked,
        @Schema(description = "Observed row version") @JsonInclude(JsonInclude.Include.ALWAYS) long beforeVersion,
        @Schema(description = "Resulting row version") long sessionVersion,
        @Schema(description = "Permanent first-revocation time") OffsetDateTime revokedAt,
        @Schema(description = "This operation's acceptance time") OffsetDateTime acceptedAt) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
