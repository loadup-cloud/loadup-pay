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
package io.github.loadup.pay.acquire.domain.model;

import io.github.loadup.commons.dto.PageQuery;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import java.time.LocalDateTime;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

public record MerchantNotificationFilter(String payeeMerchantId, String orderId, String eventId,
                                         MerchantNotificationState state,
                                         LocalDateTime occurredFrom, LocalDateTime occurredTo, int page, int size) {
    public MerchantNotificationFilter {
        DomainChecks.id(payeeMerchantId);
        if (orderId != null) {
            DomainChecks.id(orderId);
        }
        if (eventId != null) {
            DomainChecks.id(eventId);
        }
        PageQuery.of(page, size);
        require(occurredFrom == null || occurredFrom.getYear() >= 1970 && occurredFrom.getYear() <= 9998 && occurredFrom.getNano() % 1000 == 0,
                INVALID_ARGUMENT);
        require(occurredTo == null || occurredTo.getYear() >= 1970 && occurredTo.getYear() <= 9998 && occurredTo.getNano() % 1000 == 0,
                INVALID_ARGUMENT);
        require(occurredFrom == null || occurredTo == null || occurredFrom.isBefore(occurredTo), INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
