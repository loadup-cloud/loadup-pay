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
import io.github.loadup.pay.acquire.domain.model.*;
import io.github.loadup.pay.acquire.infrastructure.dataobject.*;
import java.time.LocalDateTime;
import org.mapstruct.*;

@Mapper(config = LoadUpMapStructConfig.class)
public interface OrderClosureStorageConverter {
    @Mapping(target = "advanceCursor", ignore = true)
    OrderCloseIntent toIntent(OrderCloseIntentDO row);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    OrderCloseIntentDO toDO(String tenant, OrderCloseIntent value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "state", source = "state")
    @Mapping(target = "closeEpoch", source = "closeEpoch")
    @Mapping(target = "rowVersion", source = "version")
    AcquireOrderDO toClosingPatch(AcquireOrder order);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "state", source = "order.state")
    @Mapping(target = "rowVersion", source = "order.version")
    @Mapping(target = "closedAt", source = "closedAt")
    AcquireOrderDO toClosedPatch(AcquireOrder order, LocalDateTime closedAt);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "cursorPaymentId", source = "cursorPaymentId")
    @Mapping(target = "rowVersion", source = "rowVersion")
    OrderCloseIntentDO toCursorPatch(OrderCloseIntent intent);
}
