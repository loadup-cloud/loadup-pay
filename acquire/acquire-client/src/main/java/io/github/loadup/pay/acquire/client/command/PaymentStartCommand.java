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
package io.github.loadup.pay.acquire.client.command;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.payment.client.dto.NoInteractionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInteractionDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PaymentStartCommand(
        @Schema(description = "Order id") @NotBlank @Size(max = 64)
        String orderId,

        @Schema(description = "Action key") @NotBlank @Size(max = 128)
        String actionKey,

        @Schema(description = "Amount minor") @Positive long amountMinor,
        @Schema(description = "Choice") @NotNull @Valid PaymentChoiceDTO choice,

        @Schema(description = "Payer input: scanner code for PAYMENT_CODE, resolved channel identity for JSAPI, H5_SCENE with actual terminal IP and scene for WeChat H5", accessMode = Schema.AccessMode.WRITE_ONLY) @Valid @DiagnosticHidden
        PaymentInteractionDTO interaction) {
    public PaymentStartCommand {
        if (interaction == null) {
            interaction = new NoInteractionDTO();
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
