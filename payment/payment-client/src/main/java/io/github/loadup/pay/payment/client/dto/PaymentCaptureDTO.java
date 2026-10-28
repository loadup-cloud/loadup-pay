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
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureBlocker;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureState;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Frozen source result only; blocked captures have no exportable lines, totals or content hash.
 */
@Schema(description = "Immutable reconciliation source capture; unrelated to a payment authorization/capture flow")
public record PaymentCaptureDTO(
        @Schema(description = "Original source capture id") String captureId,
        @Schema(description = "Statement channel; WECHAT in the current source profile") Channel channel,
        @Schema(description = "Actual collecting-account SHA-256") String accountScopeHash,
        @Schema(description = "Channel statement date", format = "date") LocalDate billDate,
        @Schema(description = "Fixed provider-owned statement profile revision") String statementProfile,
        @Schema(description = "System zone frozen at source registration") String systemZone,
        @Schema(description = "INIT, SEALED or BLOCKED") PaymentCaptureState state,
        @Schema(description = "Source RR read start; null while INIT", format = "date-time") OffsetDateTime startedAt,
        @Schema(description = "Source RR read finish; null while INIT", format = "date-time") OffsetDateTime finishedAt,
        @Schema(description = "Payment count; explicit zero only when SEALED") Long paymentCount,
        @Schema(description = "Gross payment amount in CNY minor units; null unless SEALED") Long paymentAmountMinor,
        @Schema(description = "Accepted refund count, including unresolved statuses; null unless SEALED") Long refundCount,
        @Schema(description = "Gross requested refund amount in CNY minor units; null unless SEALED") Long refundAmountMinor,
        @Schema(description = "SHA-256 of the complete versioned source archive; null unless SEALED") String contentHash,
        @Schema(description = "Coverage blocker; present only when BLOCKED") PaymentCaptureBlocker blocker,
        @Schema(description = "One blocker sample id; not an exhaustive difference list") String blockerSubjectId,
        @Schema(description = "Logical capture version, INIT=0 and terminal=1") long version,
        @Schema(description = "Original native task identity") String nativeTaskId,
        @Schema(description = "Independent task state; a sealed capture remains immutable after a lost task ACK") String nativeStatus,
        @Schema(description = "Current claimed attempts") Integer attempts,
        @Schema(description = "Configured maximum attempts") Integer maxAttempts) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
