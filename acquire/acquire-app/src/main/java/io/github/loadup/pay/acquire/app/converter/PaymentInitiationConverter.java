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
package io.github.loadup.pay.acquire.app.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.acquire.client.command.PaymentStartCommand;
import io.github.loadup.pay.acquire.client.dto.PaymentStartDTO;
import io.github.loadup.pay.acquire.domain.model.OrderPaymentAllocation;
import io.github.loadup.pay.acquire.domain.model.PaymentIntent;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.acquire.domain.model.StoredPaymentAllocation;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInteractionDTO;
import io.github.loadup.pay.payment.client.query.PaymentInputLookupQuery;
import io.github.loadup.pay.product.client.dto.AdmittedOrderTermsDTO;
import io.github.loadup.pay.product.client.dto.OrderAdmissionDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentInitiationConverter {
    @Mapping(target = "interaction", ignore = true)
    @Mapping(target = "choice", source = "choice")
    PaymentStartCommand toInputlessStart(String orderId, String actionKey, long amountMinor,
            io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO choice);

    @Mapping(
            target = "amount",
            expression = "java(io.github.loadup.commons.validation.DomainChecks.cnyAmount(command.amountMinor(), currency))")
    PaymentIntent toIntent(
            PaymentStartCommand command,
            String allocationId,
            String paymentId,
            String actorScope,
            String requestDigest,
            String currency);

    @Mapping(target = "orderAmountMinor", source = "order.order.amount.cent")
    @Mapping(target = "currency", source = "order.order.amount.currencyValue")
    AdmittedOrderTermsDTO toAdmitted(StoredAcquireOrder order, OrderAdmissionDTO admission);

    @Mapping(target = "orderId", source = "order.order.id")
    @Mapping(target = "paymentId", source = "allocation.paymentId")
    @Mapping(target = "allocationId", source = "allocation.id")
    @Mapping(target = "admissionId", source = "order.admission.admissionId")
    @Mapping(target = "payeeMerchantId", source = "order.order.payeeMerchantId")
    @Mapping(target = "amountMinor", source = "allocation.amount.cent")
    @Mapping(target = "currency", source = "allocation.amount.currencyValue")
    @Mapping(target = "requestDigest", source = "allocation.requestDigest")
    @Mapping(target = "choice", source = "command.choice")
    @Mapping(target = "admittedTerms", source = "admittedTerms")
    @Mapping(target = "preparedInputRef", ignore = true)
    @Mapping(target = "subject", source = "order.subject")
    @Mapping(target = "payableUntil", expression = "java(io.github.loadup.commons.time.BusinessTime.external(order.order().orderDueAt()))")
    PaymentExecuteCommand toExecute(
            StoredAcquireOrder order,
            OrderPaymentAllocation allocation,
            PaymentStartCommand command,
            AdmittedOrderTermsDTO admittedTerms);

    @Mapping(target = "allocation", source = "allocation")
    @Mapping(target = "rowVersion", constant = "0L")
    StoredPaymentAllocation toStored(
            OrderPaymentAllocation allocation, String callerScopeHash, String intentPayload, String payloadHash);

    @Mapping(target = "inputKey", source = "inputKey")
    @Mapping(target = "interaction", source = "interaction")
    PaymentInputPrepareCommand toInputPreparation(PaymentExecuteCommand command, String inputKey,
                                                  PaymentInteractionDTO interaction);

    @Mapping(target = "inputKey", source = "inputKey")
    PaymentInputLookupQuery toInputLookup(PaymentExecuteCommand command, String inputKey);

    @Mapping(target = "inputRef", source = "command.preparedInputRef")
    @Mapping(target = "inputKey", source = "inputKey")
    io.github.loadup.pay.payment.client.command.PaymentInputExpireCommand toInputExpiry(PaymentExecuteCommand command, String inputKey);

    @Mapping(target = "requestKey", expression = "java(io.github.loadup.pay.payment.client.facade.PaymentInputExpiryPolicy.REQUEST_KEY_PREFIX + command.allocationId())")
    @Mapping(target = "fenceEpoch", expression = "java(io.github.loadup.pay.payment.client.facade.PaymentInputExpiryPolicy.FENCE_EPOCH)")
    @Mapping(target = "reasonCode", expression = "java(io.github.loadup.pay.payment.client.facade.PaymentInputExpiryPolicy.REASON)")
    io.github.loadup.pay.payment.client.command.PaymentCloseCommand toInputExpiryClose(PaymentExecuteCommand command);

    @Mapping(target = "preparedInputRef", source = "inputRef")
    PaymentExecuteCommand withPreparedInput(PaymentExecuteCommand original, String inputRef);

    @Mapping(target = "intentPayload", source = "payload")
    @Mapping(target = "payloadHash", source = "hash")
    @Mapping(target = "rowVersion", expression = "java(Math.incrementExact(original.rowVersion()))")
    StoredPaymentAllocation withPreparedIntent(StoredPaymentAllocation original, String payload, String hash);

    PaymentStartDTO toStart(PaymentExecutionDTO payment);
}
