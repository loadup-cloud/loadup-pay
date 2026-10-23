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
package io.github.loadup.pay.payment.client.command;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.common.AccessMode;
import io.github.loadup.pay.common.Channel;
import io.github.loadup.pay.common.PaymentMethod;
import io.github.loadup.pay.payment.client.dto.ChannelAccountIdentityDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Trusted operations entry; credentials and merchant authorization tokens remain in their secret stores.
 */
public record ChannelBindingPublishCommand(
        @Schema(description = "Collecting merchant or sub-merchant id") @NotBlank @Size(max = 64) String payeeMerchantId,
        @Schema(description = "Stable merchant-local binding code") @NotBlank @Size(max = 64) String bindingCode,
        @Schema(description = "Channel") @NotNull Channel channel,
        @Schema(description = "Direct or service-provider mode") @NotNull AccessMode accessMode,
        @Schema(description = "Immutable typed channel identity", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotNull @Valid @DiagnosticHidden ChannelAccountIdentityDTO identity,
        @Schema(description = "Signing credential resource reference", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(max = 128) @DiagnosticHidden String credentialReference,
        @Schema(description = "Immutable signing credential version", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(max = 64) @DiagnosticHidden String credentialVersion,
        @Schema(description = "Onboarded payment methods") @NotEmpty Set<@NotNull PaymentMethod> capabilities,
        @Schema(description = "Routing priority; lower values win", example = "100") @Min(0) @Max(1000000) int priority,
        @Schema(description = "Publish idempotency key") @NotBlank @Size(max = 128) String requestKey,
        @Schema(description = "Expected head version; zero creates a binding") @PositiveOrZero long expectedVersion) {
    public ChannelBindingPublishCommand {
        capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
