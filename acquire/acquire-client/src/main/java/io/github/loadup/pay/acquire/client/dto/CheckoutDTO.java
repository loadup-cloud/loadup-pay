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
import io.github.loadup.pay.common.BusinessTime;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record CheckoutDTO(
        @Schema(description = "Order") @Valid AcquireOrderDTO order,
        @Schema(description = "Session id") @Size(max = 64) String sessionId,

        @Schema(description = "Checkout URL with capability only in its fragment") @Size(max = 2048) @DiagnosticHidden
        String checkoutUrl,

        @Schema(description = "Session expires at") OffsetDateTime sessionExpiresAt) {
    public CheckoutDTO {
        if (sessionExpiresAt != null)
            sessionExpiresAt =
                    sessionExpiresAt.atZoneSameInstant(BusinessTime.ZONE).toOffsetDateTime();
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
