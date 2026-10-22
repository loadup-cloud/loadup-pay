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
import io.github.loadup.pay.limitcenter.domain.model.*;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.*;
import org.mapstruct.*;

@Mapper(config = LoadUpMapStructConfig.class)
public interface LimitSettlementStorageConverter {
    LimitSettlementReceipt toReceipt(LimitSettlementReceiptDO row);
    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    LimitSettlementReceiptDO toDO(String tenant, LimitSettlementReceipt value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "consumedValue", source = "consumed")
    @Mapping(target = "restoredValue", source = "restored")
    LimitReservationItemDO toItemPatch(LimitReservationItem item);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "state", source = "gate.state")
    @Mapping(target = "paymentSourceVersion", source = "gate.paymentSourceVersion")
    @Mapping(target = "noCollectionEvidenceId", source = "gate.noCollectionEvidenceId")
    @Mapping(target = "rowVersion", source = "rowVersion")
    LimitOperationGateDO toGatePatch(LimitOperationGate gate, long rowVersion);
}
