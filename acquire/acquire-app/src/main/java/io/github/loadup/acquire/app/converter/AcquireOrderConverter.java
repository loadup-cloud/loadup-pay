/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.acquire.app.converter;

import io.github.loadup.acquire.app.model.productcenter.command.OrderAdmissionAuthorizeCommand;
import io.github.loadup.acquire.app.model.productcenter.dto.OrderAdmissionDTO;
import io.github.loadup.acquire.app.model.productcenter.dto.terms.FeeTermsDTO;
import io.github.loadup.acquire.app.model.productcenter.dto.terms.PaymentTermsDTO;
import io.github.loadup.acquire.client.command.AcquireOrderCreateCommand;
import io.github.loadup.acquire.client.command.CheckoutCreateCommand;
import io.github.loadup.acquire.client.command.DirectPayRecoverCommand;
import io.github.loadup.acquire.client.dto.AcquireOrderDTO;
import io.github.loadup.acquire.client.enums.PaymentProgress;
import io.github.loadup.acquire.client.enums.UnpaidOrderAction;
import io.github.loadup.acquire.domain.model.AcquireOrder;
import io.github.loadup.acquire.domain.model.OrderAdmissionProof;
import io.github.loadup.acquire.domain.model.OrderCreateIntent;
import io.github.loadup.acquire.domain.model.OrderFeePolicy;
import io.github.loadup.acquire.domain.model.OrderPaymentPolicy;
import io.github.loadup.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.commons.contract.ContractReference;
import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.commons.time.BusinessTime;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface AcquireOrderConverter {
    AcquireOrderCreateCommand toOrderCreate(DirectPayRecoverCommand command);

    AcquireOrderCreateCommand toOrderCreate(CheckoutCreateCommand command);

    OrderPaymentPolicy toPaymentPolicy(PaymentTermsDTO terms);

    OrderFeePolicy toFeePolicy(FeeTermsDTO terms);

    UnpaidOrderAction toUnpaidAction(UnpaidOrderAction action);

    @Mapping(target = "contract", source = "value")
    OrderAdmissionProof toProof(OrderAdmissionDTO value);

    ContractReference toReference(OrderAdmissionDTO value);

    @Mapping(target = "orderId", source = "intent.orderId")
    @Mapping(target = "payeeMerchantId", source = "command.payeeMerchantId")
    @Mapping(target = "requestDigest", source = "intent.requestDigest")
    OrderAdmissionAuthorizeCommand toAdmissionCommand(OrderCreateIntent intent, AcquireOrderCreateCommand command);

    @Mapping(target = "order", source = "order")
    @Mapping(target = "merchantOrderNo", source = "command.merchantOrderNo")
    @Mapping(target = "subject", source = "command.subject")
    StoredAcquireOrder toStored(
            AcquireOrder order,
            AcquireOrderCreateCommand command,
            OrderAdmissionProof admission,
            LocalDateTime admittedAt,
            String callerScopeHash,
            String snapshotId,
            String admissionPayload,
            String payloadHash);

    @Mapping(target = "orderId", source = "value.order.id")
    @Mapping(target = "payeeMerchantId", source = "value.order.payeeMerchantId")
    @Mapping(target = "currency", source = "value.order.amount.currencyValue")
    @Mapping(target = "amountMinor", source = "value.order.amount.cent")
    @Mapping(target = "paidAmountMinor", source = "value.order.paidAmountMinor")
    @Mapping(target = "paymentReservedMinor", source = "value.order.paymentReservedMinor")
    @Mapping(target = "outstandingAmountMinor", expression = "java(value.order().outstandingAmountMinor())")
    @Mapping(target = "availableToPayMinor", expression = "java(value.order().availableToPayMinor(now))")
    @Mapping(target = "state", source = "value.order.state")
    @Mapping(target = "progress", expression = "java(progress(value.order()))")
    @Mapping(target = "canAcceptNewPayment", expression = "java(value.order().canAcceptNewPayment(now))")
    @Mapping(target = "orderDueAt", source = "value.order.orderDueAt")
    @Mapping(target = "version", source = "value.order.version")
    AcquireOrderDTO toDTO(StoredAcquireOrder value, LocalDateTime now);

    default PaymentProgress progress(AcquireOrder order) {
        return order.paidAmountMinor() == 0
                ? PaymentProgress.UNPAID
                : order.paidAmountMinor() == order.amount().getCent() ? PaymentProgress.FULL : PaymentProgress.PARTIAL;
    }

    default OffsetDateTime external(LocalDateTime value) {
        return value == null ? null : BusinessTime.external(value);
    }
}
