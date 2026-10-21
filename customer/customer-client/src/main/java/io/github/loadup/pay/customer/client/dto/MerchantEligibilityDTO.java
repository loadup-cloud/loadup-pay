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
import io.github.loadup.modules.merchant.client.dto.MerchantProfileDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record MerchantEligibilityDTO(
        @Schema(description = "Allowed") boolean allowed,
        @Schema(description = "Reason code") @Size(max = 64) String reasonCode,
        @Schema(description = "Merchant") @Valid MerchantProfileDTO merchant,
        @Schema(description = "Merchant fact version") long merchantFactVersion,
        @Schema(description = "Grant id") @Size(max = 64) String grantId,
        @Schema(description = "Grant version") Long grantVersion) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
