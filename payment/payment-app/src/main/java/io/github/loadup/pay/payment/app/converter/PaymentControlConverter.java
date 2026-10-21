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
package io.github.loadup.pay.payment.app.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.payment.domain.model.*;
import io.github.loadup.pay.payment.client.dto.*;
import org.mapstruct.*;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentControlConverter {
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
