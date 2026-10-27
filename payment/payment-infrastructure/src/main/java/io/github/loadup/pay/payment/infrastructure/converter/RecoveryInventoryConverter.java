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
import io.github.loadup.pay.payment.app.model.RecoveryInventoryCell;
import io.github.loadup.pay.payment.infrastructure.model.RecoveryInventoryAggregate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

@Mapper(config = LoadUpMapStructConfig.class)
public interface RecoveryInventoryConverter {
    @Mapping(target = "kind", source = "kind")
    @Mapping(target = "unknownCount", source = "row.rowCount")
    @Mapping(target = "nominalAmountMinor", source = "row.amountMinor")
    @Mapping(target = "oldestUnknownAt", source = "row.oldestAt")
    @Mapping(target = "plus", ignore = true)
    RecoveryInventoryCell toCell(RecoveryInventoryCell.Kind kind, RecoveryInventoryAggregate row);

    default long minor(BigDecimal value) { return value == null ? 0L : value.longValueExact(); }
}
