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
import io.github.loadup.pay.payment.domain.model.PaymentExecutionGate;
import io.github.loadup.pay.payment.domain.model.PaymentOrder;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentGate;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentGateDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentOrderDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentParentGateDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentAcceptanceStorageConverter {
    @Mapping(
            target = "amount",
            expression =
                    "java(io.github.loadup.commons.validation.DomainChecks.cnyAmount(row.getAmountMinor(), row.getCurrency()))")
    @Mapping(target = "version", source = "rowVersion")
    // A domain transition is not a fluent setter.
    @Mapping(target = "applyAttempt", ignore = true)
    @Mapping(target = "close", ignore = true)
    @Mapping(target = "fail", ignore = true)
    PaymentOrder toPayment(PaymentOrderDO row);

    @Mapping(target = "payment", source = "payment")
    StoredPaymentAcceptance toStored(PaymentOrderDO row, PaymentOrder payment);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "id", source = "value.payment.id")
    @Mapping(target = "orderId", source = "value.payment.orderId")
    @Mapping(target = "allocationId", source = "value.payment.allocationId")
    @Mapping(target = "payeeMerchantId", source = "value.payment.payeeMerchantId")
    @Mapping(target = "amountMinor", source = "value.payment.amount.cent")
    @Mapping(target = "currency", source = "value.payment.amount.currencyValue")
    @Mapping(target = "state", source = "value.payment.state")
    @Mapping(target = "activeAttemptId", source = "value.payment.activeAttemptId")
    @Mapping(target = "successfulAttemptId", source = "value.payment.successfulAttemptId")
    @Mapping(target = "limitOperationId", source = "value.payment.limitOperationId")
    @Mapping(target = "initiatedAt", source = "value.payment.initiatedAt")
    @Mapping(target = "rowVersion", source = "value.payment.version")
    @Mapping(target = "succeededAt", ignore = true)
    @Mapping(target = "firstUnknownAt", ignore = true)
    @Mapping(target = "terminalEvidenceId", source = "value.terminalEvidenceId")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    PaymentOrderDO toPaymentDO(String tenant, StoredPaymentAcceptance value);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "id", source = "value.payment.id")
    @Mapping(target = "orderId", source = "value.payment.orderId")
    @Mapping(target = "allocationId", source = "value.payment.allocationId")
    @Mapping(target = "payeeMerchantId", source = "value.payment.payeeMerchantId")
    @Mapping(target = "amountMinor", source = "value.payment.amount.cent")
    @Mapping(target = "currency", source = "value.payment.amount.currencyValue")
    @Mapping(target = "state", source = "value.payment.state")
    @Mapping(target = "activeAttemptId", source = "value.payment.activeAttemptId")
    @Mapping(target = "successfulAttemptId", source = "value.payment.successfulAttemptId")
    @Mapping(target = "limitOperationId", source = "value.payment.limitOperationId")
    @Mapping(target = "initiatedAt", source = "value.payment.initiatedAt")
    @Mapping(target = "rowVersion", source = "value.payment.version")
    @Mapping(target = "succeededAt", ignore = true)
    @Mapping(target = "terminalEvidenceId", source = "value.terminalEvidenceId")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "firstUnknownAt", source = "firstUnknownAt")
    PaymentOrderDO toPaymentPatch(String tenant, StoredPaymentAcceptance value, LocalDateTime firstUnknownAt);

    @Mapping(target = "orderId", source = "orderId")
    @Mapping(target = "paymentId", ignore = true)
    @Mapping(target = "fenceEpoch", source = "closeEpoch")
    // A domain transition is not a fluent setter.
    @Mapping(target = "fence", ignore = true)
    PaymentExecutionGate toParentGate(PaymentParentGateDO row);

    @Mapping(target = "fence", ignore = true)
    PaymentExecutionGate toPaymentGate(PaymentGateDO row);

    @Mapping(target = "gate", source = "gate")
    StoredPaymentGate toStoredParent(PaymentParentGateDO row, PaymentExecutionGate gate);

    @Mapping(target = "gate", source = "gate")
    StoredPaymentGate toStoredGate(PaymentGateDO row, PaymentExecutionGate gate);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "orderId", source = "value.gate.orderId")
    @Mapping(target = "state", source = "value.gate.state")
    @Mapping(target = "closeEpoch", source = "value.gate.fenceEpoch")
    @Mapping(target = "rowVersion", source = "value.rowVersion")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    PaymentParentGateDO toParentDO(String tenant, StoredPaymentGate value);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "orderId", source = "value.gate.orderId")
    @Mapping(target = "paymentId", source = "value.gate.paymentId")
    @Mapping(target = "state", source = "value.gate.state")
    @Mapping(target = "fenceEpoch", source = "value.gate.fenceEpoch")
    @Mapping(target = "reasonCode", ignore = true)
    @Mapping(target = "rowVersion", source = "value.rowVersion")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    PaymentGateDO toGateDO(String tenant, StoredPaymentGate value);
}
