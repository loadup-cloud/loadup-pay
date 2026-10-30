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
package io.github.loadup.pay.acquire.client.query;

import io.github.loadup.commons.dto.PageQuery;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record MerchantNotificationPageQuery(
        @Schema(description = "Collecting merchant; must match merchant JWT scope") @NotBlank @Size(max = 64) String payeeMerchantId,
        @Schema(description = "Original order filter") @Size(max = 64) String orderId,
        @Schema(description = "Stable merchant event identity filter") @Size(max = 64) String eventId,
        @Schema(description = "Delivery state filter") MerchantNotificationState state,
        @Schema(description = "Inclusive source event time") OffsetDateTime occurredFrom,
        @Schema(description = "Exclusive source event time") OffsetDateTime occurredTo,
        @Schema(description = "Page number starting at 1") Integer page,
        @Schema(description = "Page size, up to 100") Integer size) {
    public MerchantNotificationPageQuery {
        var bounds = PageQuery.normalized(page, size, AcquireResultCodeEnum.INVALID_PAGE);
        page = bounds.page();
        size = bounds.size();
        if (occurredFrom != null) {
            occurredFrom = occurredFrom.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
        }
        if (occurredTo != null) {
            occurredTo = occurredTo.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
