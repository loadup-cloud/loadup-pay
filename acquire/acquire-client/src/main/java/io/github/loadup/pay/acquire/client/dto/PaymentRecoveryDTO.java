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
package io.github.loadup.pay.acquire.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.acquire.client.enums.PaymentRecoveryState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

public record PaymentRecoveryDTO(
        @Schema(description = "Original order id") String orderId,
        @Schema(description = "Original fixed merchant action key") String actionKey,
        @Schema(description = "Intent recovery status; absence is not financial evidence") PaymentRecoveryState state,
        @Schema(description = "Original payment identity; present only when resolved") String paymentId,
        @Schema(description = "Null while unresolved; false only after a trusted never-accepted receipt converged")
        @JsonInclude(JsonInclude.Include.ALWAYS) Boolean accepted,
        @Schema(description = "Converged original payment and order; present only when resolved") @Valid PaymentProgressDTO progress) {
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
