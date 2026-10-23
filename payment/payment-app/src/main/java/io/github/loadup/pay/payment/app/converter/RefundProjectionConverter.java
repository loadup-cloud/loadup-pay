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
package io.github.loadup.pay.payment.app.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.payment.app.model.RefundSnapshotFacts;
import io.github.loadup.pay.payment.client.event.OrderRefundSummaryEventDTO;
import io.github.loadup.pay.payment.client.event.RefundSnapshotEventDTO;
import io.github.loadup.pay.payment.domain.model.OrderRefundSummary;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface RefundProjectionConverter {
    @Mapping(target = "amountMinor", source = "amount.cent")
    @Mapping(target = "currency", source = "amount.currencyValue")
    RefundSnapshotEventDTO toEvent(RefundSnapshotFacts value);

    @Mapping(target = "currency", source = "orderAmount.currencyValue")
    @Mapping(target = "refundReservedMinor", source = "reservedMinor")
    @Mapping(target = "refundSummaryVersion", source = "version")
    OrderRefundSummaryEventDTO toEvent(OrderRefundSummary value);

    default OffsetDateTime external(LocalDateTime value) {
        return value == null ? null : BusinessTime.external(value);
    }
}
