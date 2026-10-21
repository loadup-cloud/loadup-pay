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
import io.github.loadup.pay.acquire.client.dto.*;
import io.github.loadup.pay.acquire.client.enums.*;
import io.github.loadup.pay.common.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

public record CheckoutCreateCommand(
        @Schema(description = "Payee merchant id") @NotBlank @Size(max = 64)
        String payeeMerchantId,

        @Schema(description = "Merchant order no") @NotBlank @Size(max = 64)
        String merchantOrderNo,

        @Schema(description = "Product scope key") @NotBlank @Size(max = 128)
        String productScopeKey,

        @Schema(description = "Amount minor") @Positive long amountMinor,

        @Schema(description = "Currency") @Size(max = 64) @Pattern(regexp = "CNY")
        String currency,

        @Schema(description = "Subject") @NotBlank @Size(max = 200)
        String subject,

        @Schema(description = "Requested due at") OffsetDateTime requestedDueAt,

        @Schema(description = "Return target code") @Size(max = 128)
        String returnTargetCode) {
    public CheckoutCreateCommand {
        currency = currency == null ? "CNY" : currency.toUpperCase(Locale.ROOT);
        if (requestedDueAt != null)
            requestedDueAt = requestedDueAt.atZoneSameInstant(BusinessTime.ZONE).toOffsetDateTime();
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
