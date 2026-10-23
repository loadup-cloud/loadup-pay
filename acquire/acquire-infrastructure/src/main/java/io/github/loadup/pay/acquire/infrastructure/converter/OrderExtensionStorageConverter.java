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
package io.github.loadup.pay.acquire.infrastructure.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.acquire.domain.model.AcquireOrder;
import io.github.loadup.pay.acquire.domain.model.OrderExtensionReceipt;
import io.github.loadup.pay.acquire.infrastructure.dataobject.AcquireOrderDO;
import io.github.loadup.pay.acquire.infrastructure.dataobject.OrderExtensionReceiptDO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface OrderExtensionStorageConverter {
    OrderExtensionReceipt toReceipt(OrderExtensionReceiptDO row);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    OrderExtensionReceiptDO toDO(String tenant, OrderExtensionReceipt value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "orderDueAt", source = "order.orderDueAt")
    @Mapping(target = "rowVersion", source = "order.version")
    @Mapping(target = "deadlineVersion", source = "deadlineVersion")
    AcquireOrderDO toDeadlinePatch(AcquireOrder order, long deadlineVersion);
}
