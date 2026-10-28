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
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.acquire.client.enums.CheckoutSessionState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * Issuer-only metadata; credentials and reconstruction inputs are excluded.
 */
public record CheckoutSessionStatusDTO(
        @Schema(description = "Session id") String id,
        @Schema(description = "Original order id") String orderId,
        @Schema(description = "Collecting merchant id") String payeeMerchantId,
        @Schema(description = "Business lifecycle, independent of revocation") CheckoutSessionState state,
        @Schema(description = "Original capability deadline") OffsetDateTime expiresAt,
        @Schema(description = "Permanent security revocation flag") @JsonInclude(JsonInclude.Include.ALWAYS) boolean revoked,
        @Schema(description = "Original security revocation time") OffsetDateTime revokedAt,
        @Schema(description = "Session row version for revocation preconditions") @JsonInclude(JsonInclude.Include.ALWAYS) long rowVersion) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
