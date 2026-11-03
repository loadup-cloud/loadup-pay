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
package io.github.loadup.pay.payment.infrastructure.config;

import io.github.loadup.commons.json.ToStringAsJson;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentLimitProperties(
        @NotBlank @Size(max = 64) String tenantId,
        @NotBlank @Size(max = 64) String serviceId,
        @NotBlank @Size(max = 128) String audience) {
    public PaymentLimitProperties {
        requireText("tenant-id", tenantId, 64);
        requireText("service-id", serviceId, 64);
        requireText("audience", audience, 128);
    }

    private static void requireText(String field, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("loadup.modules.payment.limitcenter." + field + " is missing or blank");
        }
        if (value.length() > maxLength || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("loadup.modules.payment.limitcenter." + field + " must contain at most "
                    + maxLength + " characters and no control characters");
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
