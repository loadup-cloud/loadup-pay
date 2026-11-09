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
package io.github.loadup.payment.app.converter;

import io.github.loadup.components.runtime.mapping.LoadUpMapStructConfig;
import io.github.loadup.payment.client.command.PaymentCloseCommand;
import io.github.loadup.payment.client.command.PaymentInputExpireCommand;
import io.github.loadup.payment.client.dto.ParentFenceDTO;
import io.github.loadup.payment.client.dto.PaymentCloseDTO;
import io.github.loadup.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.payment.client.dto.PaymentInputExpiryDTO;
import io.github.loadup.payment.client.enums.PaymentInputExpiryDisposition;
import io.github.loadup.payment.domain.model.PaymentExecutionGate;
import io.github.loadup.payment.domain.model.PaymentOrder;
import io.github.loadup.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.payment.domain.model.StoredPaymentGate;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentControlConverter {
    @Mapping(target = "paymentId", source = "command.paymentId")
    @Mapping(target = "orderId", source = "command.orderId")
    @Mapping(target = "disposition", source = "disposition")
    @Mapping(target = "closed", source = "closed")
    PaymentInputExpiryDTO toInputExpiry(
            PaymentInputExpireCommand command, PaymentInputExpiryDisposition disposition, PaymentCloseDTO closed);

    @Mapping(
            target = "requestKey",
            expression = "java(io.github.loadup.payment.client.support.PaymentInputExpiryPolicy.REQUEST_KEY_PREFIX +"
                    + " command.allocationId())")
    @Mapping(
            target = "fenceEpoch",
            expression = "java(io.github.loadup.payment.client.support.PaymentInputExpiryPolicy.FENCE_EPOCH)")
    @Mapping(
            target = "reasonCode",
            expression = "java(io.github.loadup.payment.client.support.PaymentInputExpiryPolicy.REASON)")
    PaymentCloseCommand toInputExpiryClose(PaymentInputExpireCommand command);

    @Mapping(target = "gate", source = "gate")
    @Mapping(target = "rowVersion", expression = "java(Math.incrementExact(current.rowVersion()))")
    StoredPaymentGate withGate(StoredPaymentGate current, PaymentExecutionGate gate);

    @Mapping(target = "payment", source = "payment")
    @Mapping(target = "terminalEvidenceId", source = "terminalEvidenceId")
    StoredPaymentAcceptance withPayment(
            StoredPaymentAcceptance current, PaymentOrder payment, String terminalEvidenceId);

    ParentFenceDTO toParent(
            String orderId, long closeEpoch, boolean fenced, boolean allChildrenConverged, int unknownCount);

    @Mapping(target = "payment", source = "payment")
    PaymentCloseDTO toClose(
            String paymentId,
            String orderId,
            long fenceEpoch,
            boolean fenced,
            PaymentExecutionDTO payment,
            String noCollectionEvidenceId);
}
