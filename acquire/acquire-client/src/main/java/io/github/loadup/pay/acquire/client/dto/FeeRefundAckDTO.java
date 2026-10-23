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
import static io.github.loadup.commons.error.DomainViolation.*;
import static io.github.loadup.commons.error.DomainViolation.Reason.*;

public record FeeRefundAckDTO(@Schema(description = "Original delivery id") String deliveryId,
        @Schema(description = "Original business event id") String businessEventId,
        @Schema(description = "Order id") String orderId,
        @Schema(description = "Incoming monetary summary version") long sourceVersion,
        @Schema(description = "Verified monetary summary version accepted; the stored fee may already be newer") long appliedVersion,
        @Schema(description = "Fee basis and semantic receipt committed") boolean accepted) {
    public FeeRefundAckDTO {
        DomainChecks.id(deliveryId); DomainChecks.id(businessEventId); DomainChecks.id(orderId);
        require(sourceVersion > 0 && appliedVersion >= sourceVersion, INVALID_ARGUMENT);
    }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
