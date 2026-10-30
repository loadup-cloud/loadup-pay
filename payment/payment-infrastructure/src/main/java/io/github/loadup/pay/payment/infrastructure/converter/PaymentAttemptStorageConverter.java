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
import io.github.loadup.pay.channel.api.model.ChannelBindingRevision;
import io.github.loadup.pay.payment.domain.model.PaymentAttempt;
import io.github.loadup.pay.payment.domain.model.RiskDecision;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentAttemptDO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentAttemptStorageConverter {
    @Mapping(target = "binding", source = "binding")
    @Mapping(
            target = "amount",
            expression =
                    "java(io.github.loadup.commons.validation.DomainChecks.cnyAmount(row.getAmountMinor(), row.getCurrency()))")
    @Mapping(target = "beginSubmission", ignore = true)
    @Mapping(target = "closeBeforeSubmission", ignore = true)
    @Mapping(target = "id", source = "row.id")
    PaymentAttempt toAttempt(PaymentAttemptDO row, ChannelBindingRevision binding);

    @Mapping(target = "decision", source = "riskDecision")
    @Mapping(target = "reasonCode", source = "riskReason")
    @Mapping(target = "consultationRef", source = "riskConsultationRef")
    RiskDecision toRisk(PaymentAttemptDO row);

    @Mapping(target = "attempt", source = "attempt")
    @Mapping(target = "riskDecision", source = "risk")
    StoredPaymentAttempt toStored(PaymentAttemptDO row, PaymentAttempt attempt, RiskDecision risk);

    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "id", source = "value.attempt.id")
    @Mapping(target = "paymentId", source = "value.attempt.paymentId")
    @Mapping(target = "attemptNo", source = "value.attempt.attemptNo")
    @Mapping(target = "amountMinor", source = "value.attempt.amount.cent")
    @Mapping(target = "currency", source = "value.attempt.amount.currencyValue")
    @Mapping(target = "bindingRevisionId", source = "value.attempt.binding.id")
    @Mapping(target = "channel", source = "value.attempt.binding.channel")
    @Mapping(target = "accessMode", source = "value.attempt.binding.accessMode")
    @Mapping(target = "accountScopeHash", source = "value.attempt.binding.accountScopeHash")
    @Mapping(target = "method", source = "value.attempt.method")
    @Mapping(target = "channelRequestNo", source = "value.attempt.channelRequestNo")
    @Mapping(target = "state", source = "value.attempt.state")
    @Mapping(target = "executionEpoch", source = "value.attempt.executionEpoch")
    @Mapping(target = "noCollectionEvidenceId", source = "value.attempt.noCollectionEvidenceId")
    @Mapping(target = "riskDecision", source = "value.riskDecision.decision")
    @Mapping(target = "riskReason", source = "value.riskDecision.reasonCode")
    @Mapping(target = "riskConsultationRef", source = "value.riskDecision.consultationRef")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    PaymentAttemptDO toDO(String tenant, StoredPaymentAttempt value);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "state", source = "attempt.state")
    @Mapping(target = "executionEpoch", source = "attempt.executionEpoch")
    @Mapping(target = "noCollectionEvidenceId", source = "attempt.noCollectionEvidenceId")
    @Mapping(target = "riskDecision", source = "riskDecision.decision")
    @Mapping(target = "riskReason", source = "riskDecision.reasonCode")
    @Mapping(target = "riskConsultationRef", source = "riskDecision.consultationRef")
    @Mapping(target = "executionPermitHash", source = "executionPermitHash")
    @Mapping(target = "submissionStartedAt", source = "submissionStartedAt")
    @Mapping(target = "rowVersion", source = "rowVersion")
    PaymentAttemptDO toTransitionPatch(StoredPaymentAttempt value);
}
