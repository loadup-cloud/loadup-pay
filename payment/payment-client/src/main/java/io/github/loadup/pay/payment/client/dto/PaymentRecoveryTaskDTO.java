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
import io.github.loadup.pay.payment.client.enums.PaymentRecoveryKind;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public record PaymentRecoveryTaskDTO(
        @Schema(description = "Payment recovery task kind") PaymentRecoveryKind kind,
        @Schema(description = "Original payment or refund request id") String subjectId,
        @Schema(description = "Original business generation") long generation,
        @Schema(description = "Native task id") String nativeTaskId,
        @Schema(description = "PENDING, RUNNING, SUCCEEDED, FAILED or CANCELLED") String nativeStatus,
        @Schema(description = "Observed native attempts") int attempts,
        @Schema(description = "Native attempts allowed per retry cycle") int maxAttempts,
        @Schema(description = "Original business wake-up time") OffsetDateTime executeAt,
        @Schema(description = "Observed native next execution time; use this exact value as a retry precondition") OffsetDateTime nextExecuteAt,
        @Schema(description = "This generation still has payment work; not a financial status") boolean businessCurrent,
        @Schema(description = "Native FAILED and current business work due now; this snapshot can change") boolean retryable) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
