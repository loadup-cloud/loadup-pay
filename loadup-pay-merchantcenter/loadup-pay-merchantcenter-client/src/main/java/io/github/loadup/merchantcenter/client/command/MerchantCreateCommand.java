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
package io.github.loadup.merchantcenter.client.command;

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MerchantCreateCommand(
        @Schema(description = "Merchant code") @NotBlank @Size(max = 64) String merchantCode,

        @Schema(description = "Name") @NotBlank @Size(max = 200) String name,

        @Schema(description = "Short name") @Size(max = 100) String shortName,

        @Schema(description = "Type") @NotBlank @Size(max = 32) String type,

        @Schema(description = "Industry") @NotBlank @Size(max = 64) String industry,

        @Schema(description = "ISO 3166-1 alpha-2 country code") @NotBlank @Size(max = 2) String country,

        @Schema(description = "Province") @Size(max = 64) String province,
        @Schema(description = "City") @Size(max = 64) String city,

        @Schema(description = "Merchant business address") @Size(max = 500) String address,

        @Schema(description = "Registration no") @Size(max = 64) String registrationNo,

        @Schema(description = "Contact name") @Size(max = 100) String contactName,

        @Schema(description = "Contact phone") @Size(max = 32) String contactPhone,

        @Schema(description = "Contact email") @Size(max = 200) String contactEmail) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
