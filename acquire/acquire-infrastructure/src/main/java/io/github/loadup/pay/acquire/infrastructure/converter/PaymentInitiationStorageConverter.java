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
import io.github.loadup.pay.acquire.domain.model.OrderPaymentAllocation;
import io.github.loadup.pay.acquire.domain.model.StoredPaymentAllocation;
import io.github.loadup.pay.acquire.infrastructure.dataobject.AcquireOrderDO;
import io.github.loadup.pay.acquire.infrastructure.dataobject.OrderPaymentAllocationDO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentInitiationStorageConverter {
    @Mapping(
            target = "amount",
            expression =
                    "java(io.github.loadup.commons.validation.DomainChecks.cnyAmount(row.getAmountMinor(),row.getCurrency()))")
    OrderPaymentAllocation toAllocation(OrderPaymentAllocationDO row);

    @Mapping(target = "allocation", source = "allocation")
    StoredPaymentAllocation toStored(OrderPaymentAllocationDO row, OrderPaymentAllocation allocation);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "id", source = "value.allocation.id")
    @Mapping(target = "orderId", source = "value.allocation.orderId")
    @Mapping(target = "paymentId", source = "value.allocation.paymentId")
    @Mapping(target = "actorScope", source = "value.allocation.actorScope")
    @Mapping(target = "actionKey", source = "value.allocation.actionKey")
    @Mapping(target = "requestDigest", source = "value.allocation.requestDigest")
    @Mapping(target = "amountMinor", source = "value.allocation.amount.cent")
    @Mapping(target = "currency", source = "value.allocation.amount.currencyValue")
    @Mapping(target = "state", source = "value.allocation.state")
    @Mapping(target = "paymentSourceVersion", source = "value.allocation.paymentSourceVersion")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    OrderPaymentAllocationDO toAllocationDO(String tenant, StoredPaymentAllocation value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "intentPayload", source = "intentPayload")
    @Mapping(target = "payloadHash", source = "payloadHash")
    @Mapping(target = "rowVersion", source = "rowVersion")
    OrderPaymentAllocationDO toInputPatch(StoredPaymentAllocation value);

    /**
     * An intentional four-column patch leaves admission, deadline and payment facts untouched.
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "paymentReservedMinor", source = "paymentReservedMinor")
    @Mapping(target = "reservedPaymentCount", source = "reservedPaymentCount")
    @Mapping(target = "state", source = "state")
    @Mapping(target = "rowVersion", source = "version")
    AcquireOrderDO toReservationPatch(AcquireOrder value);
}
