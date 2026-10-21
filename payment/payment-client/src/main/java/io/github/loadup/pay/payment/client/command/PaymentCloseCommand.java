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
import io.github.loadup.pay.common.*;
import io.github.loadup.pay.payment.client.dto.*;
import io.github.loadup.pay.payment.client.enums.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

public record PaymentCloseCommand(
        @Schema(description = "Payment id") @NotBlank @Size(max = 64)
        String paymentId,

        @Schema(description = "Order id") @NotBlank @Size(max = 64)
        String orderId,

        @Schema(description = "Request key") @NotBlank @Size(max = 128)
        String requestKey,

        @Schema(description = "Fence epoch") @PositiveOrZero long fenceEpoch,

        @Schema(description = "Reason code") @NotBlank @Size(max = 64)
        String reasonCode) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
