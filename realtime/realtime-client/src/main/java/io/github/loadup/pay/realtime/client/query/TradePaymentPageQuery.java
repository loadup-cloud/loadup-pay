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
package io.github.loadup.pay.realtime.client.query;

import io.github.loadup.commons.dto.PageQuery;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.payment.client.enums.PaymentState;
import io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record TradePaymentPageQuery(
        @Schema(description = "Payee merchant id") @NotBlank @Size(max = 64)
        String payeeMerchantId,

        @Schema(description = "Order id") @Size(max = 64) String orderId,
        @Schema(description = "State") PaymentState state,
        @Schema(description = "Created from") OffsetDateTime createdFrom,
        @Schema(description = "Created to") OffsetDateTime createdTo,
        @Schema(description = "Page") Integer page,
        @Schema(description = "Size") Integer size) {
    public TradePaymentPageQuery {
        if (createdFrom != null)
            createdFrom = createdFrom.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
        if (createdTo != null)
            createdTo = createdTo.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
        var bounds = PageQuery.normalized(page, size, RealtimeResultCodeEnum.INVALID_PAGE);
        page = bounds.page();
        size = bounds.size();
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
