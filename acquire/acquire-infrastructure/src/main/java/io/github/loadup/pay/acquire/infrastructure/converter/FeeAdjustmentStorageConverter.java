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
import io.github.loadup.pay.acquire.infrastructure.dataobject.FeeAdjustmentDO;
import org.mapstruct.*;
import java.time.LocalDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface FeeAdjustmentStorageConverter {
    @Mapping(target = "feeId", source = "fee.id")
    @Mapping(target = "orderId", source = "fee.orderId")
    @Mapping(target = "adjustment", expression = "java(io.github.loadup.commons.validation.DomainChecks.cnyAmount(fee.adjustmentDueMinor(), fee.baseAmount().getCurrencyValue()))")
    @Mapping(target = "refundSourceVersion", source = "fee.refundBasisVersion")
    FeeAdjustment toAdjustment(String id, OrderFee fee, String triggerKey);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "sourceVersion", source = "value.refundSourceVersion")
    @Mapping(target = "adjustmentMinor", source = "value.adjustment.cent")
    @Mapping(target = "currency", source = "value.adjustment.currencyValue")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    FeeAdjustmentDO toDO(String tenant, FeeAdjustment value, String sourceEventId, long cumulativeRefundedMinor,
            String ruleSnapshot, LocalDateTime appliedAt);
}
