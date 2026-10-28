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

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerKind;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(description = "One immutable line from a source-owned capture; gross payments and refunds stay separate")
public record PaymentLedgerLineDTO(
        @Schema(description = "Immutable one-based contiguous source ordinal") long ordinal,
        @Schema(description = "PAYMENT or REFUND") PaymentLedgerKind kind,
        @Schema(description = "Original channel transaction number") @DiagnosticHidden String channelTransactionNo,
        @Schema(description = "Original merchant child-refund request number, present only for REFUND") @DiagnosticHidden String channelRefundNo,
        @Schema(description = "Original channel merchant-payment request number") @DiagnosticHidden String merchantPaymentNo,
        @Schema(description = "Positive gross amount in CNY minor units") long amountMinor,
        @Schema(description = "Currency, CNY") String currency,
        @Schema(description = "Observed local financial state; PAYMENT requires SUCCEEDED") PaymentLedgerStatus status,
        @Schema(description = "Payment success or channel refund acceptance instant", format = "date-time") OffsetDateTime sourceAt,
        @Schema(description = "Original payment or child-refund id") String sourceId,
        @Schema(description = "Winning payment receipt or first refund acceptance receipt") String evidenceId,
        @Schema(description = "Financial row version observed in the source RR snapshot") long sourceVersion) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
