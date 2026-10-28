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
import io.github.loadup.pay.payment.domain.model.PaymentInputExpiryDisposition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentInputExpiryDTO(
        @Schema(description = "Original payment id") @NotBlank String paymentId,
        @Schema(description = "Original order id") @NotBlank String orderId,
        @Schema(description = "Atomic input/payment resolution, independent of financial success") @NotNull PaymentInputExpiryDisposition disposition,
        @Schema(description = "Durable never-accepted closure proof; present only for EXPIRED_CLOSED") @Valid PaymentCloseDTO closed) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
