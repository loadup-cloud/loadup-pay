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
import io.github.loadup.pay.acquire.domain.model.OrderFee;
import io.github.loadup.pay.acquire.domain.model.OrderFeePolicy;
import io.github.loadup.pay.acquire.infrastructure.dataobject.OrderFeeDO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface OrderFeeStorageConverter {
    @Mapping(target = "baseAmount", expression = "java(io.github.loadup.commons.validation.DomainChecks.cnyAmount(row.getBaseAmountMinor(),row.getCurrency()))")
    @Mapping(target = "feeAmount", expression = "java(io.github.loadup.commons.validation.DomainChecks.cnyAmount(row.getFeeAmountMinor(),row.getCurrency()))")
    @Mapping(target = "policy", source = "policy")
    @Mapping(target = "adjustedMinor", source = "row.adjustedAmountMinor")
    OrderFee toFee(OrderFeeDO row, OrderFeePolicy policy);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "state", source = "fee.state")
    @Mapping(target = "rowVersion", source = "rowVersion")
    @Mapping(target = "confirmedAt", source = "confirmedAt")
    OrderFeeDO toConfirmationPatch(OrderFee fee, long rowVersion, LocalDateTime confirmedAt);
}
