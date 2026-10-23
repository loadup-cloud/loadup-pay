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
import io.swagger.v3.oas.annotations.media.Schema;

public record PaymentOutcomeAckDTO(
        @Schema(description = "Original target delivery id") String deliveryId,
        @Schema(description = "Original payment business event") String businessEventId,
        @Schema(description = "Original payment id") String paymentId,
        @Schema(description = "Authoritative terminal payment version") long paymentVersion,
        @Schema(description = "Authoritative terminal evidence") String evidenceId,
        @Schema(description = "Order version observed in the committed receipt") long orderVersion,
        @Schema(description = "The original event was durably accepted") boolean accepted) {
    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
