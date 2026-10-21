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
package io.github.loadup.pay.product.client.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.common.*;
import io.github.loadup.pay.product.client.enums.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;

public record AcquiringTermsDTO(
        @Schema(description = "Schema version") @Positive int schemaVersion,
        @Schema(description = "Order") @Valid @NotNull OrderTermsDTO order,
        @Schema(description = "Payment") @Valid @NotNull PaymentTermsDTO payment,
        @Schema(description = "Fee") @Valid @NotNull FeeTermsDTO fee,
        @Schema(description = "Refund") @Valid @NotNull RefundTermsDTO refund,

        @Schema(description = "Notification") @Valid @NotNull
        NotificationTermsDTO notification,

        @Schema(description = "Limits") @Valid @NotNull LimitPolicyRefDTO limits,
        @Schema(description = "Capabilities") @Valid List<ChannelCapabilityDTO> capabilities) {
    public AcquiringTermsDTO {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
