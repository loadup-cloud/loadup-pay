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

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/** Immutable operation receipt, not the latest delivery state or proof of merchant receipt. */
public record MerchantNotificationWakeDTO(
        @Schema(description = "Audit receipt id") String id,
        @Schema(description = "Original delivery id") String deliveryId,
        @Schema(description = "Original merchant event id") String eventId,
        @Schema(description = "Original operation key") String requestKey,
        @Schema(description = "Authenticated operator or service type") String principalType,
        @Schema(description = "Authenticated operator or service subject") String principalId,
        @Schema(description = "Audited reason") @DiagnosticHidden String reason,
        @Schema(description = "REGISTERED, RETRIED, RESCHEDULED, PENDING, RUNNING, WAITING, ACKED, EXHAUSTED or EXPIRED") String result,
        @Schema(description = "Business attempts observed by this operation") int attemptCount,
        @Schema(description = "Delivery row version observed by this operation") long deliveryVersion,
        @Schema(description = "Audit acceptance time") OffsetDateTime acceptedAt) {
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
