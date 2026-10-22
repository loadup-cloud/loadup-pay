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
import io.github.loadup.pay.acquire.client.dto.*;
import io.github.loadup.pay.acquire.domain.model.*;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentOutcomeDTO;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import java.time.LocalDateTime;
import org.mapstruct.*;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentProgressConverter {
    @Mapping(target = "payment", source = "payment")
    PaymentOutcomeDTO toObserved(PaymentExecutionDTO payment, boolean executionFenced);

    @Mapping(target = "amount", expression = "java(io.github.loadup.pay.common.DomainChecks.amount(command.amountMinor(),command.currency()))")
    @Mapping(target = "kind", constant = "NOT_ACCEPTED")
    @Mapping(target = "paymentVersion", constant = "0L")
    @Mapping(target = "evidenceId", source = "noCollectionEvidenceId")
    @Mapping(target = "executionFenced", constant = "true")
    VerifiedPaymentOutcome toNotAccepted(PaymentExecuteCommand command, String noCollectionEvidenceId);

    @Mapping(target = "order", source = "order")
    StoredAcquireOrder withOrder(StoredAcquireOrder original, AcquireOrder order);

    PaymentProgressDTO toProgress(PaymentExecutionDTO payment, AcquireOrderDTO order);

    @Mapping(target = "amount", expression = "java(io.github.loadup.pay.common.DomainChecks.amount(payment.amountMinor(),payment.currency()))")
    @Mapping(target = "paymentVersion", source = "payment.version")
    @Mapping(target = "evidenceId", source = "payment.terminalEvidenceId")
    VerifiedPaymentOutcome toOutcome(PaymentExecutionDTO payment, VerifiedPaymentOutcome.Kind kind, boolean executionFenced);

    @Mapping(target = "outcome", source = "outcome")
    PaymentOutcomeReceipt toReceipt(String id, String allocationId, VerifiedPaymentOutcome outcome, LocalDateTime observedAt);
}
