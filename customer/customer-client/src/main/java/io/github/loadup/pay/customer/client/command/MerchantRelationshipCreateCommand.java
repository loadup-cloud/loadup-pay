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
package io.github.loadup.pay.customer.client.command;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.time.BusinessTime;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record MerchantRelationshipCreateCommand(
        @Schema(description = "Parent merchant id") @NotBlank @Size(max = 64)
        String parentMerchantId,

        @Schema(description = "Child merchant id") @NotBlank @Size(max = 64)
        String childMerchantId,

        @Schema(description = "Valid from") @NotNull OffsetDateTime validFrom,
        @Schema(description = "Valid to") OffsetDateTime validTo,

        @Schema(description = "Request key") @NotBlank @Size(max = 128)
        String requestKey) {
    public MerchantRelationshipCreateCommand {
        if (validFrom != null)
            validFrom = validFrom.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
        if (validTo != null)
            validTo = validTo.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
