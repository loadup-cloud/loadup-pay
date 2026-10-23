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
package io.github.loadup.pay.payment.client.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.common.AccessMode;
import io.github.loadup.pay.common.Channel;
import io.github.loadup.pay.common.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RoutingRuleDTO(
        @Schema(description = "Merchant-local binding code") @NotBlank @Size(max = 64) String bindingCode,
        @Schema(description = "Channel") @NotNull Channel channel,
        @Schema(description = "Direct or service-provider mode") @NotNull AccessMode accessMode,
        @Schema(description = "Payment method") @NotNull PaymentMethod method) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
