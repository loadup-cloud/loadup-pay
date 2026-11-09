/*
 * Copyright (C) 2026 LoadUp Cloud
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
package io.github.loadup.productcenter.app.model.merchantcenter.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;

public record MerchantProfileDTO(
        @Schema(description = "Resource identifier") String id,

        @Schema(description = "Merchant code") String merchantCode,
        @Schema(description = "Type") String type,
        @Schema(description = "Industry") String industry,

        @Schema(description = "ISO 3166-1 alpha-2 country code")
        String country,

        @Schema(description = "Province") String province,
        @Schema(description = "City") String city,
        @Schema(description = "Active") boolean active) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
