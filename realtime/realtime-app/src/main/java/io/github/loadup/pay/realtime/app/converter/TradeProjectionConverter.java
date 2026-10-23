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
package io.github.loadup.pay.realtime.app.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.common.BusinessTime;
import io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO;
import io.github.loadup.pay.payment.client.event.*;
import io.github.loadup.pay.realtime.client.dto.*;
import io.github.loadup.pay.realtime.domain.model.*;
import org.mapstruct.*;
import java.time.*;

@Mapper(config = LoadUpMapStructConfig.class)
public interface TradeProjectionConverter {
    @Mapping(target = "paidMinor", source = "value.paidAmountMinor")
    @Mapping(target = "reservedMinor", source = "value.paymentReservedMinor")
    @Mapping(target = "amount", expression = "java(io.github.loadup.pay.common.DomainChecks.amount(value.amountMinor(), value.currency()))")
    @Mapping(target = "source", source = "source")
    TradeOrderProjection toOrder(OrderSnapshotEventDTO value, SourceVersion source, LocalDateTime snapshotAt);

    @Mapping(target = "amount", expression = "java(io.github.loadup.pay.common.DomainChecks.amount(value.amountMinor(), value.currency()))")
    @Mapping(target = "source", source = "source")
    TradePaymentProjection toPayment(PaymentSnapshotEventDTO value, SourceVersion source, LocalDateTime snapshotAt);

    @Mapping(target = "amount", expression = "java(io.github.loadup.pay.common.DomainChecks.amount(value.amountMinor(), value.currency()))")
    @Mapping(target = "source", source = "source")
    TradeRefundProjection toRefund(RefundSnapshotEventDTO value, SourceVersion source, LocalDateTime snapshotAt);

    @Mapping(target = "reservedMinor", source = "value.refundReservedMinor")
    @Mapping(target = "source", source = "source")
    TradeRefundSummary toSummary(OrderRefundSummaryEventDTO value, SourceVersion source, LocalDateTime snapshotAt);

    @Mapping(target = "amountMinor", source = "amount.cent")
    @Mapping(target = "currency", source = "amount.currencyValue")
    @Mapping(target = "paidAmountMinor", source = "paidMinor")
    @Mapping(target = "paymentReservedMinor", source = "reservedMinor")
    @Mapping(target = "sourceVersion", source = "source.number")
    OrderSnapshotEventDTO toEvent(TradeOrderProjection value);

    @Mapping(target = "amountMinor", source = "amount.cent")
    @Mapping(target = "currency", source = "amount.currencyValue")
    @Mapping(target = "sourceVersion", source = "source.number")
    PaymentSnapshotEventDTO toEvent(TradePaymentProjection value);

    @Mapping(target = "amountMinor", source = "amount.cent")
    @Mapping(target = "currency", source = "amount.currencyValue")
    @Mapping(target = "sourceVersion", source = "source.number")
    RefundSnapshotEventDTO toEvent(TradeRefundProjection value);

    @Mapping(target = "refundReservedMinor", source = "reservedMinor")
    @Mapping(target = "refundSummaryVersion", source = "source.number")
    OrderRefundSummaryEventDTO toEvent(TradeRefundSummary value);

    @Mapping(target = "amountMinor", source = "amount.cent")
    @Mapping(target = "currency", source = "amount.currencyValue")
    @Mapping(target = "sourceVersion", source = "source.number")
    TradePaymentDTO toDTO(TradePaymentProjection value);

    @Mapping(target = "amountMinor", source = "amount.cent")
    @Mapping(target = "currency", source = "amount.currencyValue")
    @Mapping(target = "sourceVersion", source = "source.number")
    TradeRefundDTO toDTO(TradeRefundProjection value);

    @Mapping(target = "orderId", source = "order.orderId")
    @Mapping(target = "payeeMerchantId", source = "order.payeeMerchantId")
    @Mapping(target = "merchantOrderNo", source = "order.merchantOrderNo")
    @Mapping(target = "amountMinor", source = "order.amount.cent")
    @Mapping(target = "currency", source = "order.amount.currencyValue")
    @Mapping(target = "paidAmountMinor", source = "order.paidMinor")
    @Mapping(target = "paymentReservedMinor", source = "order.reservedMinor")
    @Mapping(target = "state", source = "order.state")
    @Mapping(target = "orderCreatedAt", source = "order.orderCreatedAt")
    @Mapping(target = "orderSourceVersion", source = "order.source.number")
    @Mapping(target = "snapshotAt", source = "order.snapshotAt")
    TradeOrderDTO toOrderDTO(TradeOrderProjection order, Long refundedMinor, Long netReceivedMinor,
            Long refundSourceVersion, boolean amountsConsistent);

    @Mapping(target = "accepted", constant = "true")
    ProjectionAckDTO toAck(ProjectionReceipt receipt);

    default OffsetDateTime external(LocalDateTime value) { return value == null ? null : BusinessTime.external(value); }
    default LocalDateTime local(OffsetDateTime value) {
        return value == null ? null : BusinessTime.local(value).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }
}
