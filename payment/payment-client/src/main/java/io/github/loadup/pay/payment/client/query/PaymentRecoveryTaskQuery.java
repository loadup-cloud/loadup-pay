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
package io.github.loadup.pay.payment.client.query;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.payment.domain.model.PaymentRecoveryKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record PaymentRecoveryTaskQuery(
        @Schema(description = "Allow-listed payment recovery task kind") @NotNull PaymentRecoveryKind kind,
        @Schema(description = "Original payment id for COLLECTION, or original refund request id for REFUND_REQUEST") @NotBlank @Size(max = 64) String subjectId,
        @Schema(description = "Original recovery generation, beginning at zero") @NotNull @PositiveOrZero Long generation) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
