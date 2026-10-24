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
import io.github.loadup.pay.payment.client.event.RefundMerchantEventDTO;
import io.github.loadup.pay.payment.domain.model.OrderRefundSummary;
import io.github.loadup.pay.payment.domain.model.RefundRequest;
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

    @Mapping(target = "aggregateType", constant = "RefundOrder")
    @Mapping(target = "merchantRefundNo", source = "merchantRefundNo")
    @Mapping(target = "amountMinor", source = "value.amount.cent")
    @Mapping(target = "currency", source = "value.amount.currencyValue")
    @Mapping(target = "succeededMinor", expression = "java(\"SUCCEEDED\".equals(value.state()) ? value.amount().getCent() : 0L)")
    @Mapping(target = "failedMinor", expression = "java(\"FAILED\".equals(value.state()) ? value.amount().getCent() : 0L)")
    RefundMerchantEventDTO toMerchantEvent(RefundSnapshotFacts value, String merchantRefundNo);

    @Mapping(target = "aggregateType", constant = "RefundRequest")
    @Mapping(target = "refundRequestId", source = "id")
    @Mapping(target = "refundId", ignore = true)
    @Mapping(target = "paymentId", ignore = true)
    @Mapping(target = "succeededAt", ignore = true)
    @Mapping(target = "amountMinor", source = "amount.cent")
    @Mapping(target = "currency", source = "amount.currencyValue")
    @Mapping(target = "sourceVersion", source = "version")
    @Mapping(target = "succeededMinor", expression = "java(value.succeededMinor())")
    @Mapping(target = "failedMinor", expression = "java(value.failedMinor())")
    RefundMerchantEventDTO toMerchantEvent(RefundRequest value);

    default OffsetDateTime external(LocalDateTime value) {
        return value == null ? null : BusinessTime.external(value);
    }
}
