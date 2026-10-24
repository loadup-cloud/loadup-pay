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
import io.github.loadup.pay.acquire.client.enums.MerchantNotificationState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/** Public delivery metadata excludes body, routing references, signing identities and native errors. */
public record MerchantNotificationDTO(
        @Schema(description = "Delivery id") String id,
        @Schema(description = "Stable merchant event id") String eventId,
        @Schema(description = "Original order id") String orderId,
        @Schema(description = "Collecting merchant id") String payeeMerchantId,
        @Schema(description = "Merchant event type") String eventType,
        @Schema(description = "Original source aggregate id") String aggregateId,
        @Schema(description = "Original source version") long sourceVersion,
        @Schema(description = "Delivery state; ACKED requires merchant semantic acknowledgement") MerchantNotificationState state,
        @Schema(description = "Business attempts already started") int attemptCount,
        @Schema(description = "Frozen maximum business attempts") int maxAttempts,
        @Schema(description = "Original source event time") OffsetDateTime occurredAt,
        @Schema(description = "Frozen delivery window deadline") OffsetDateTime expiresAt,
        @Schema(description = "Business next-attempt time; terminal rows retain the historical value") OffsetDateTime nextAttemptAt,
        @Schema(description = "Merchant acknowledgement time, if received") OffsetDateTime acknowledgedAt,
        @Schema(description = "Delivery row version") long rowVersion) {
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
