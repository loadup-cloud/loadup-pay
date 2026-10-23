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
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.pay.product.client.enums.UnpaidOrderAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record OrderTermsDTO(
        @Schema(description = "Wait seconds") @Positive long waitSeconds,
        @Schema(description = "Max wait seconds") @Positive long maxWaitSeconds,
        @Schema(description = "Unpaid action") @NotNull UnpaidOrderAction unpaidAction,
        @Schema(description = "Extension allowed") boolean extensionAllowed,

        @Schema(description = "Extension max seconds") @PositiveOrZero
        long extensionMaxSeconds,

        @Schema(description = "Session seconds") @Positive long sessionSeconds) {
    public OrderTermsDTO {
        DomainViolation.require(
                waitSeconds > 0
                        && maxWaitSeconds >= waitSeconds
                        && sessionSeconds > 0
                        && extensionMaxSeconds >= 0
                        && (extensionAllowed ? extensionMaxSeconds > 0 : extensionMaxSeconds == 0)
                        && unpaidAction != null,
                DomainViolation.Reason.INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
