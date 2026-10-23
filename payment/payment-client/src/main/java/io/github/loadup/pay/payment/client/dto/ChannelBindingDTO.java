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
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.pay.payment.client.enums.ChannelBindingState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

public record ChannelBindingDTO(
        @Schema(description = "Binding id") String bindingId,
        @Schema(description = "Collecting merchant id") String payeeMerchantId,
        @Schema(description = "Merchant-local code") String bindingCode,
        @Schema(description = "Channel") Channel channel,
        @Schema(description = "Access mode") AccessMode accessMode,
        @Schema(description = "Current routing state") ChannelBindingState state,
        @Schema(description = "Current immutable revision id") String bindingRevisionId,
        @Schema(description = "Current revision number") int revision,
        @Schema(description = "Onboarded methods declared by trusted operations") Set<PaymentMethod> capabilities,
        @Schema(description = "Current priority; lower values win") int priority,
        @Schema(description = "Current head version") long version) {
    public ChannelBindingDTO {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
