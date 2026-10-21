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
package io.github.loadup.pay.realtime.client.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.common.*;
import io.github.loadup.pay.realtime.client.enums.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

public record TradeDetailDTO(
        @Schema(description = "Order") @Valid TradeOrderDTO order,
        @Schema(description = "Payments") @Valid List<TradePaymentDTO> payments,
        @Schema(description = "Refunds") @Valid List<TradeRefundDTO> refunds,
        @Schema(description = "Snapshot at") OffsetDateTime snapshotAt) {
    public TradeDetailDTO {
        payments = payments == null ? List.of() : List.copyOf(payments);
        refunds = refunds == null ? List.of() : List.copyOf(refunds);
        if (snapshotAt != null)
            snapshotAt = snapshotAt.atZoneSameInstant(BusinessTime.ZONE).toOffsetDateTime();
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
