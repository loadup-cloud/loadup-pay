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
package io.github.loadup.pay.customer.client.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.common.*;
import io.github.loadup.pay.customer.client.enums.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

public record MerchantRelationshipDTO(
        @Schema(description = "Id") @Size(max = 64) String id,

        @Schema(description = "Parent merchant id") @Size(max = 64)
        String parentMerchantId,

        @Schema(description = "Child merchant id") @Size(max = 64)
        String childMerchantId,

        @Schema(description = "Lifecycle state") @NotNull GrantState state,
        @Schema(description = "Valid from") OffsetDateTime validFrom,
        @Schema(description = "Valid to") OffsetDateTime validTo,
        @Schema(description = "Version") long version) {
    public MerchantRelationshipDTO {
        if (validFrom != null)
            validFrom = validFrom.atZoneSameInstant(BusinessTime.ZONE).toOffsetDateTime();
        if (validTo != null)
            validTo = validTo.atZoneSameInstant(BusinessTime.ZONE).toOffsetDateTime();
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
