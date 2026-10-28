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

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.payment.Channel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PaymentCaptureCreateCommand(
        @Schema(description = "Stable source capture request key") @NotBlank @Size(max = 128) String requestKey,
        @Schema(description = "Statement channel") @NotNull Channel channel,
        @Schema(description = "Actual collecting-account SHA-256") @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String accountScopeHash,
        @Schema(description = "Channel statement date") @NotNull LocalDate billDate,
        @Schema(description = "Provider-owned statement profile") @NotBlank @Size(max = 64) String statementProfile) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
