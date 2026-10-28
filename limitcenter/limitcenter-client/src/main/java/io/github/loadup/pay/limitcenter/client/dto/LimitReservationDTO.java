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
package io.github.loadup.pay.limitcenter.client.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservationState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record LimitReservationDTO(
        @Schema(description = "Reservation id") @Size(max = 64)
        String reservationId,

        @Schema(description = "Operation id") @Size(max = 64)
        String operationId,

        @Schema(description = "Payment id") @Size(max = 64) String paymentId,
        @Schema(description = "State") LimitReservationState state,
        @Schema(description = "Policy id") @Size(max = 64) String policyId,
        @Schema(description = "Policy version") int policyVersion,
        @Schema(description = "Initiated at") OffsetDateTime initiatedAt,
        @Schema(description = "Version") long version) {
    public LimitReservationDTO {
        if (initiatedAt != null)
            initiatedAt = initiatedAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
