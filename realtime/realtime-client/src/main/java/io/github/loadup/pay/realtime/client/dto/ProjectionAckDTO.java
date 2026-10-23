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
package io.github.loadup.pay.realtime.client.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;

public record ProjectionAckDTO(
        @Schema(description = "Original target delivery") String deliveryId,
        @Schema(description = "Original business event") String businessEventId,
        @Schema(description = "Original producer") String producer,
        @Schema(description = "Source aggregate id") String aggregateId,
        @Schema(description = "Received source version") long sourceVersion,
        @Schema(description = "Projected version at acceptance; may exceed a stale received version") long projectedVersion,
        @Schema(description = "APPLIED, DUPLICATE or STALE") String disposition,
        @Schema(description = "Source event was durably accepted") boolean accepted) {
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
