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
package io.github.loadup.pay.payment.infrastructure.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.payment.app.model.RefundSnapshotFacts;
import io.github.loadup.pay.payment.app.model.RefundSummaryFacts;
import io.github.loadup.pay.payment.domain.model.OrderRefundSummary;
import io.github.loadup.pay.payment.infrastructure.dataobject.OrderRefundSummaryDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundOrderDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundRequestDO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface RefundProjectionStorageConverter {
    @Mapping(target = "orderAmount", expression = "java(io.github.loadup.pay.common.DomainChecks.amount(row.getOrderAmountMinor(), row.getCurrency()))")
    @Mapping(target = "version", source = "rowVersion")
    @Mapping(target = "reserve", ignore = true)
    OrderRefundSummary toSummary(OrderRefundSummaryDO row);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "orderAmountMinor", source = "value.orderAmount.cent")
    @Mapping(target = "currency", source = "value.orderAmount.currencyValue")
    @Mapping(target = "rowVersion", source = "value.version")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    OrderRefundSummaryDO toSummaryDO(String tenant, String id, OrderRefundSummary value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "refundedMinor", source = "refundedMinor")
    @Mapping(target = "reservedMinor", source = "reservedMinor")
    @Mapping(target = "rowVersion", source = "version")
    OrderRefundSummaryDO toPatch(OrderRefundSummary value);

    @Mapping(target = "refundId", source = "row.id")
    @Mapping(target = "orderId", source = "request.orderId")
    @Mapping(target = "payeeMerchantId", source = "request.payeeMerchantId")
    @Mapping(target = "state", source = "row.state")
    @Mapping(target = "amount", expression = "java(io.github.loadup.pay.common.DomainChecks.amount(row.getAmountMinor(), row.getCurrency()))")
    @Mapping(target = "refundCreatedAt", source = "row.createdAt")
    @Mapping(target = "sourceUpdatedAt", source = "row.updatedAt")
    @Mapping(target = "sourceVersion", source = "row.rowVersion")
    RefundSnapshotFacts toFacts(RefundOrderDO row, RefundRequestDO request);

    @Mapping(target = "summary", source = "summary")
    @Mapping(target = "sourceUpdatedAt", source = "row.updatedAt")
    RefundSummaryFacts toFacts(OrderRefundSummaryDO row, OrderRefundSummary summary);
}
