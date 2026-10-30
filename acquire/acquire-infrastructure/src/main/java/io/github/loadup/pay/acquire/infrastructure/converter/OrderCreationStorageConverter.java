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
import io.github.loadup.pay.acquire.domain.model.OrderAdmissionProof;
import io.github.loadup.pay.acquire.domain.model.OrderCreateIntent;
import io.github.loadup.pay.acquire.domain.model.OrderFee;
import io.github.loadup.pay.acquire.domain.model.OrderPaymentPolicy;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.acquire.domain.model.UnpaidOrderAction;
import io.github.loadup.pay.acquire.infrastructure.dataobject.AcquireOrderDO;
import io.github.loadup.pay.acquire.infrastructure.dataobject.OrderContractSnapshotDO;
import io.github.loadup.pay.acquire.infrastructure.dataobject.OrderCreateIntentDO;
import io.github.loadup.pay.acquire.infrastructure.dataobject.OrderFeeDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface OrderCreationStorageConverter {
    @Mapping(target = "entryMode", source = "row.entryMode")
    @Mapping(target = "initialSessionId", source = "row.initialSessionId")
    OrderCreateIntent toIntent(OrderCreateIntentDO row);

    @Mapping(target = "entryMode", source = "value.entryMode")
    @Mapping(target = "initialSessionId", source = "value.initialSessionId")

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    OrderCreateIntentDO toIntentDO(String tenant, OrderCreateIntent value);

    @Mapping(
            target = "amount",
            expression =
                    "java(io.github.loadup.commons.validation.DomainChecks.cnyAmount(row.getAmountMinor(), row.getCurrency()))")
    @Mapping(target = "version", source = "row.rowVersion")
    @Mapping(target = "paymentPolicy", source = "policy")
    @Mapping(target = "unpaidAction", source = "action")
    // Domain transitions, falsely detected as fluent setters by MapStruct 1.6.
    @Mapping(target = "finishClose", ignore = true)
    @Mapping(target = "checkDue", ignore = true)
    AcquireOrder toOrder(AcquireOrderDO row, OrderPaymentPolicy policy, UnpaidOrderAction action);

    @Mapping(target = "order", source = "order")
    @Mapping(target = "merchantOrderNo", source = "row.merchantOrderNo")
    @Mapping(target = "subject", source = "row.subject")
    @Mapping(target = "admittedAt", source = "row.admittedAt")
    @Mapping(target = "callerScopeHash", source = "intent.callerScopeHash")
    @Mapping(target = "snapshotId", source = "snapshot.id")
    @Mapping(target = "admissionPayload", source = "snapshot.configuration")
    @Mapping(target = "payloadHash", source = "snapshot.payloadHash")
    StoredAcquireOrder toStored(
            AcquireOrderDO row,
            OrderContractSnapshotDO snapshot,
            OrderCreateIntent intent,
            AcquireOrder order,
            OrderAdmissionProof admission);


    @Mapping(target = "id", source = "value.order.id")
    @Mapping(target = "payeeMerchantId", source = "value.order.payeeMerchantId")
    @Mapping(target = "admissionId", source = "value.admission.admissionId")
    @Mapping(target = "contractSnapshotId", source = "value.snapshotId")
    @Mapping(target = "currency", source = "value.order.amount.currencyValue")
    @Mapping(target = "amountMinor", source = "value.order.amount.cent")
    @Mapping(target = "paidAmountMinor", source = "value.order.paidAmountMinor")
    @Mapping(target = "paymentReservedMinor", source = "value.order.paymentReservedMinor")
    @Mapping(target = "successfulPaymentCount", source = "value.order.successfulPaymentCount")
    @Mapping(target = "reservedPaymentCount", source = "value.order.reservedPaymentCount")
    @Mapping(target = "state", source = "value.order.state")
    @Mapping(target = "orderDueAt", source = "value.order.orderDueAt")
    @Mapping(target = "closeEpoch", source = "value.order.closeEpoch")
    @Mapping(target = "rowVersion", source = "value.order.version")
    @Mapping(target = "deadlineVersion", constant = "1L")
    @Mapping(target = "paidAt", ignore = true)
    @Mapping(target = "closedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    AcquireOrderDO toOrderDO(String tenant, StoredAcquireOrder value);


    @Mapping(target = "id", source = "value.snapshotId")
    @Mapping(target = "orderId", source = "value.order.id")
    @Mapping(target = "contractId", source = "value.admission.contract.contractId")
    @Mapping(target = "contractRevision", source = "value.admission.contract.revision")
    @Mapping(target = "contractGeneration", source = "value.admission.contract.generation")
    @Mapping(target = "snapshotHash", source = "value.admission.contract.snapshotHash")
    @Mapping(target = "compiledTermsHash", source = "value.admission.contract.compiledTermsHash")
    @Mapping(target = "schemaVersion", constant = "1")
    @Mapping(target = "configuration", source = "value.admissionPayload")
    @Mapping(target = "admissionDigest", source = "value.admission.requestDigest")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    OrderContractSnapshotDO toSnapshotDO(String tenant, StoredAcquireOrder value);


    @Mapping(target = "feeType", constant = "ORDER_AMOUNT")
    @Mapping(target = "currency", source = "value.baseAmount.currencyValue")
    @Mapping(target = "baseAmountMinor", source = "value.baseAmount.cent")
    @Mapping(target = "feeAmountMinor", source = "value.feeAmount.cent")
    @Mapping(target = "feeRuleSnapshot", source = "payload")
    @Mapping(target = "adjustedAmountMinor", source = "value.adjustedMinor")
    @Mapping(target = "confirmedAt", ignore = true)
    @Mapping(target = "rowVersion", constant = "0L")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    OrderFeeDO toFeeDO(String tenant, OrderFee value, String payload);
}
