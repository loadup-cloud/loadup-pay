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

import io.github.loadup.commons.json.ToStringAsJson;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.loadup.pay.payment.client.dto.PaymentActionDTO;
import io.github.loadup.pay.payment.client.enums.PaymentState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

/** Session-safe projection; accepted=false and absent state/version require a trusted non-acceptance receipt. */
public record CashierPaymentDTO(
        @Schema(description = "Original order id") String orderId,
        @Schema(description = "Original payment id") String paymentId,
        @Schema(description = "Whether payment was accepted; false requires a trusted non-acceptance receipt")
        @JsonInclude(JsonInclude.Include.ALWAYS) boolean accepted,
        @Schema(description = "Payment state; null only for proven non-acceptance") PaymentState state,
        @Schema(description = "Verified launch action, if ready") @Valid PaymentActionDTO action,
        @Schema(description = "Payment source version, absent for proven non-acceptance") Long version,
        @Schema(description = "Current safe order summary") @Valid CashierOrderDTO order) {
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
