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
import io.github.loadup.commons.money.Money;
import io.github.loadup.pay.payment.client.enums.PaymentRecoveryKind;
import io.github.loadup.pay.payment.client.enums.PaymentRecoveryAttentionLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record PaymentUnknownRecoveryDTO(
        PaymentRecoveryKind kind,
        @Schema(description = "Original payment id or individual child refund id; keyset tie breaker") String recordId,
        @Schema(description = "Original payment id or refund REQUEST id; use with recoveryGeneration for task detail") String subjectId,
        String orderId, String payeeMerchantId, Money amount, OffsetDateTime firstUnknownAt,
        long ageSeconds, PaymentRecoveryAttentionLevel attentionLevel, long rowVersion,
        @Schema(description = "Observed cursor generation; null means missing cursor, never permission to recreate it") Long recoveryGeneration,
        @Schema(description = "Observed cursor completion; null means missing cursor; UNKNOWN plus completed requires investigation") Boolean recoveryCompleted) {
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
