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

import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.components.runtime.mapping.LoadUpMapStructConfig;
import io.github.loadup.contracts.event.payment.PaymentState;
import io.github.loadup.payment.app.model.limitcenter.command.LimitConfirmCommand;
import io.github.loadup.payment.app.model.limitcenter.command.LimitReleaseCommand;
import io.github.loadup.payment.domain.model.RiskConsultation;
import io.github.loadup.payment.domain.model.RiskDecision;
import io.github.loadup.payment.domain.model.RiskOutcome;
import io.github.loadup.payment.domain.model.StoredPaymentAcceptance;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentOutcomeDeliveryConverter {
    @Mapping(target = "operationId", source = "value.payment.limitOperationId")
    @Mapping(target = "paymentId", source = "value.payment.id")
    @Mapping(target = "paymentVersion", source = "value.payment.version")
    @Mapping(target = "successEvidenceId", source = "value.terminalEvidenceId")
    @Mapping(target = "amountMinor", source = "value.payment.amount.cent")
    @Mapping(target = "currency", source = "value.payment.amount.currencyValue")
    LimitConfirmCommand toConfirm(StoredPaymentAcceptance value, String businessEventId, LocalDateTime succeededAt);

    @Mapping(target = "operationId", source = "value.payment.limitOperationId")
    @Mapping(target = "paymentId", source = "value.payment.id")
    @Mapping(target = "paymentVersion", source = "value.payment.version")
    @Mapping(target = "noCollectionEvidenceId", source = "value.terminalEvidenceId")
    @Mapping(target = "reasonCode", source = "value.payment.state", qualifiedByName = "releaseReason")
    LimitReleaseCommand toRelease(StoredPaymentAcceptance value, String businessEventId);

    @Mapping(target = "consultation", source = "consultation")
    @Mapping(target = "decision", source = "decision")
    RiskOutcome toRisk(
            String businessEventId,
            RiskConsultation consultation,
            RiskDecision decision,
            PaymentState paymentState,
            long sourceVersion,
            String evidenceId);

    @Named("releaseReason")
    default String releaseReason(PaymentState state) {
        return "PAYMENT_" + state.name();
    }

    default OffsetDateTime external(LocalDateTime value) {
        return value == null ? null : BusinessTime.external(value);
    }
}
