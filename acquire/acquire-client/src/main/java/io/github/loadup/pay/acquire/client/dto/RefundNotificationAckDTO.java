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
import io.github.loadup.commons.validation.DomainChecks;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Acknowledges durable notification registration or the original disabled policy, never merchant delivery.
 */
public record RefundNotificationAckDTO(
        @Schema(description = "Original internal delivery id") String deliveryId,
        @Schema(description = "Original source business event id") String businessEventId,
        @Schema(description = "Original child or request aggregate id") String aggregateId,
        @Schema(description = "Exact terminal source version") long sourceVersion,
        @Schema(description = "Stable merchant event id") String merchantEventId,
        @Schema(description = "ENQUEUED or DISABLED") String disposition) {
    public RefundNotificationAckDTO {
        DomainChecks.id(deliveryId);
        DomainChecks.id(businessEventId);
        DomainChecks.id(aggregateId);
        require(sourceVersion > 0 && merchantEventId != null && merchantEventId.matches("[0-9a-f]{64}")
                && disposition != null && Set.of("ENQUEUED", "DISABLED").contains(disposition), INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
