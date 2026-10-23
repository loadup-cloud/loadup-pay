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
import io.github.loadup.pay.acquire.domain.model.PaymentOutcomeReceipt;
import io.github.loadup.pay.acquire.domain.model.VerifiedPaymentOutcome;
import io.github.loadup.pay.acquire.infrastructure.dataobject.AcquireOrderDO;
import io.github.loadup.pay.acquire.infrastructure.dataobject.OrderPaymentAllocationDO;
import io.github.loadup.pay.acquire.infrastructure.dataobject.OrderPaymentOutcomeDO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentOutcomeStorageConverter {
    @Mapping(target = "kind", source = "outcomeKind")
    @Mapping(target = "paymentVersion", source = "paymentSourceVersion")
    @Mapping(target = "amount", expression = "java(io.github.loadup.pay.common.DomainChecks.amount(row.getAmountMinor(),row.getCurrency()))")
    VerifiedPaymentOutcome toOutcome(OrderPaymentOutcomeDO row);

    @Mapping(target = "outcome", source = "outcome")
    PaymentOutcomeReceipt toReceipt(OrderPaymentOutcomeDO row, VerifiedPaymentOutcome outcome);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "orderId", source = "value.outcome.orderId")
    @Mapping(target = "paymentId", source = "value.outcome.paymentId")
    @Mapping(target = "amountMinor", source = "value.outcome.amount.cent")
    @Mapping(target = "currency", source = "value.outcome.amount.currencyValue")
    @Mapping(target = "outcomeKind", source = "value.outcome.kind")
    @Mapping(target = "paymentSourceVersion", source = "value.outcome.paymentVersion")
    @Mapping(target = "evidenceId", source = "value.outcome.evidenceId")
    @Mapping(target = "executionFenced", source = "value.outcome.executionFenced")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    OrderPaymentOutcomeDO toDO(String tenant, PaymentOutcomeReceipt value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "paidAmountMinor", source = "order.paidAmountMinor")
    @Mapping(target = "paymentReservedMinor", source = "order.paymentReservedMinor")
    @Mapping(target = "successfulPaymentCount", source = "order.successfulPaymentCount")
    @Mapping(target = "reservedPaymentCount", source = "order.reservedPaymentCount")
    @Mapping(target = "state", source = "order.state")
    @Mapping(target = "rowVersion", source = "order.version")
    @Mapping(target = "paidAt", source = "paidAt")
    AcquireOrderDO toOrderPatch(AcquireOrder order, LocalDateTime paidAt);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "state", source = "allocation.state")
    @Mapping(target = "paymentSourceVersion", source = "allocation.paymentSourceVersion")
    @Mapping(target = "rowVersion", source = "rowVersion")
    OrderPaymentAllocationDO toAllocationPatch(OrderPaymentAllocation allocation, long rowVersion);
}
