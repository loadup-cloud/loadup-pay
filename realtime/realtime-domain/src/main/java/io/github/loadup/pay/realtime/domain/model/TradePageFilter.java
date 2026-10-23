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
package io.github.loadup.pay.realtime.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.common.DomainChecks;
import io.github.loadup.pay.common.DomainViolation;

import java.time.LocalDateTime;

public record TradePageFilter(String payeeMerchantId, String orderId, String merchantOrderNo, String refundRequestId,
                              String state, LocalDateTime createdFrom, LocalDateTime createdTo, int page, int size) {
    public TradePageFilter {
        DomainChecks.id(payeeMerchantId);
        if (orderId != null) DomainChecks.id(orderId);
        if (merchantOrderNo != null) DomainChecks.text(merchantOrderNo, 64);
        if (refundRequestId != null) DomainChecks.id(refundRequestId);
        if (state != null) DomainChecks.text(state, 32);
        DomainViolation.require(page > 0 && size > 0 && size <= 100 && (createdFrom == null || createdTo == null
                || createdFrom.isBefore(createdTo)), DomainViolation.Reason.INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
