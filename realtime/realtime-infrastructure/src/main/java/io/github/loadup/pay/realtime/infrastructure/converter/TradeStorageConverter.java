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
package io.github.loadup.pay.realtime.infrastructure.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.realtime.domain.model.ProjectionReceipt;
import io.github.loadup.pay.realtime.domain.model.TradeOrderProjection;
import io.github.loadup.pay.realtime.domain.model.TradePaymentProjection;
import io.github.loadup.pay.realtime.domain.model.TradeRefundProjection;
import io.github.loadup.pay.realtime.domain.model.TradeRefundSummary;
import io.github.loadup.pay.realtime.infrastructure.dataobject.ProjectionReceiptDO;
import io.github.loadup.pay.realtime.infrastructure.dataobject.TradeOrderDO;
import io.github.loadup.pay.realtime.infrastructure.dataobject.TradePaymentDO;
import io.github.loadup.pay.realtime.infrastructure.dataobject.TradeRefundDO;
import io.github.loadup.pay.realtime.infrastructure.dataobject.TradeSummaryDO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface TradeStorageConverter {
    @Mapping(target = "id", source = "id")
    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "sourceVersion", source = "value.source.number")
    @Mapping(target = "businessCreatedAt", source = "value.orderCreatedAt")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    TradeOrderDO toDO(String id, String tenant, TradeOrderProjection value, String contentHash, String payload);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "sourceVersion", source = "value.source.number")
    @Mapping(target = "contentHash", source = "contentHash")
    @Mapping(target = "payload", source = "payload")
    @Mapping(target = "snapshotAt", source = "value.snapshotAt")
    @Mapping(target = "state", source = "value.state")
    TradeOrderDO toPatch(TradeOrderProjection value, String contentHash, String payload);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "sourceVersion", source = "value.source.number")
    @Mapping(target = "businessCreatedAt", source = "value.initiatedAt")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    TradePaymentDO toDO(String id, String tenant, TradePaymentProjection value, String contentHash, String payload);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "sourceVersion", source = "value.source.number")
    @Mapping(target = "contentHash", source = "contentHash")
    @Mapping(target = "payload", source = "payload")
    @Mapping(target = "snapshotAt", source = "value.snapshotAt")
    @Mapping(target = "state", source = "value.state")
    TradePaymentDO toPatch(TradePaymentProjection value, String contentHash, String payload);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "sourceVersion", source = "value.source.number")
    @Mapping(target = "businessCreatedAt", source = "value.refundCreatedAt")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    TradeRefundDO toDO(String id, String tenant, TradeRefundProjection value, String contentHash, String payload);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "sourceVersion", source = "value.source.number")
    @Mapping(target = "contentHash", source = "contentHash")
    @Mapping(target = "payload", source = "payload")
    @Mapping(target = "snapshotAt", source = "value.snapshotAt")
    @Mapping(target = "state", source = "value.state")
    TradeRefundDO toPatch(TradeRefundProjection value, String contentHash, String payload);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "sourceVersion", source = "value.source.number")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    TradeSummaryDO toDO(String id, String tenant, TradeRefundSummary value, String contentHash, String payload);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "sourceVersion", source = "value.source.number")
    @Mapping(target = "contentHash", source = "contentHash")
    @Mapping(target = "payload", source = "payload")
    @Mapping(target = "snapshotAt", source = "value.snapshotAt")
    TradeSummaryDO toPatch(TradeRefundSummary value, String contentHash, String payload);

    ProjectionReceipt toReceipt(ProjectionReceiptDO row);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    ProjectionReceiptDO toDO(String tenant, ProjectionReceipt value);
}
