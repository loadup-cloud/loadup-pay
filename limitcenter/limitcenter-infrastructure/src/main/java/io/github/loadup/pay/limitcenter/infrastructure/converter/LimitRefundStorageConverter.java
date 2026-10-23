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
package io.github.loadup.pay.limitcenter.infrastructure.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.limitcenter.domain.model.LimitRefundReceipt;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitRefundReceiptDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface LimitRefundStorageConverter {
    LimitRefundReceipt toDomain(LimitRefundReceiptDO row);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    LimitRefundReceiptDO toDO(String tenant, LimitRefundReceipt value);
}
