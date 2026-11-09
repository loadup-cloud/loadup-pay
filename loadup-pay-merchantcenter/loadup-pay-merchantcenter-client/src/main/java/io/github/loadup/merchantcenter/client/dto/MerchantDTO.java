/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.merchantcenter.client.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.masking.MaskType;
import io.github.loadup.commons.masking.Masked;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record MerchantDTO(
        @Schema(description = "Resource identifier") String id,
        @Schema(description = "Merchant code") String merchantCode,
        @Schema(description = "Name") String name,
        @Schema(description = "Short name") String shortName,
        @Schema(description = "Type") String type,
        @Schema(description = "Industry") String industry,

        @Schema(description = "ISO 3166-1 alpha-2 country code")
        String country,

        @Schema(description = "Province") String province,
        @Schema(description = "City") String city,

        @Schema(description = "Merchant business address") @Masked(MaskType.FULL)
        String address,

        @Schema(description = "Registration no") @Masked(MaskType.FULL)
        String registrationNo,

        @Schema(description = "Contact name") @Masked(MaskType.NAME)
        String contactName,

        @Schema(description = "Contact phone") @Masked(MaskType.PHONE)
        String contactPhone,

        @Schema(description = "Contact email") @Masked(MaskType.EMAIL)
        String contactEmail,

        @Schema(description = "Current lifecycle status") String status,

        @Schema(description = "Current optimistic concurrency version")
        long rowVersion,

        @Schema(description = "Created by") String createdBy,
        @Schema(description = "Updated by") String updatedBy,

        @Schema(description = "Creation time in the application local time zone")
        LocalDateTime createdAt,

        @Schema(description = "Last update time in the application local time zone")
        LocalDateTime updatedAt) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
