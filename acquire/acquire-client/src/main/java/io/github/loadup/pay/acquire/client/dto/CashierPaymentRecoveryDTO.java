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
import io.github.loadup.pay.acquire.client.enums.PaymentRecoveryState;
import io.swagger.v3.oas.annotations.media.Schema;

public record CashierPaymentRecoveryDTO(
        @Schema(description = "Original order id") String orderId,
        @Schema(description = "Original fixed action key") String actionKey,
        @Schema(description = "Intent lookup result; NOT_OBSERVED is not evidence of non-payment") PaymentRecoveryState state,
        @Schema(description = "Original payment after result convergence; present only for RESOLVED") CashierPaymentDTO payment) {
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
