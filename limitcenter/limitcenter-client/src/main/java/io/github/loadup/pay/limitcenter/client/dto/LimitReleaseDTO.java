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
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

public record LimitReleaseDTO(
        @Schema(description = "Original operation id") String operationId,
        @Schema(description = "Original payment id") String paymentId,
        @Schema(description = "The operation is persistently fenced") boolean fenced,
        @Schema(description = "Current reservation; null when release preceded reserve") @Valid LimitReservationDTO reservation,
        @Schema(description = "Authoritative terminal payment version") long paymentVersion,
        @Schema(description = "Authoritative no-collection evidence") String noCollectionEvidenceId) {
    @Override
    public String toString() { return ToStringAsJson.reflectionToString(this); }
}
