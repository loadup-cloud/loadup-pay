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
import io.github.loadup.pay.payment.client.enums.PaymentRecoveryAttentionLevel;
import io.github.loadup.pay.payment.client.enums.PaymentRecoveryKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * Tenant is exclusively resolved from the verified operations SERVICE.
 */
public record PaymentUnknownRecoveryQuery(
        @NotNull PaymentRecoveryKind kind,
        @Schema(description = "Minimum operational attention band, inclusive") @NotNull PaymentRecoveryAttentionLevel minimumLevel,
        @Schema(description = "Previous page cursor time; supplied together with afterRecordId") OffsetDateTime afterFirstUnknownAt,
        @Size(max = 64) String afterRecordId,
        @NotNull @Min(1) @Max(100) Integer limit) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
