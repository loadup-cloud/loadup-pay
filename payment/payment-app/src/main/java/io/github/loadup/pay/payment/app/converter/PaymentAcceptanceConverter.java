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
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentOutcomeDTO;
import io.github.loadup.pay.payment.client.dto.PaymentSettlementFactsDTO;
import io.github.loadup.pay.payment.domain.model.PaymentOrder;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface PaymentAcceptanceConverter {
    @Mapping(target = "payment", source = "payment")
    @Mapping(target = "limitOperationId", source = "value.payment.limitOperationId")
    @Mapping(target = "payeeMerchantId", source = "value.payment.payeeMerchantId")
    PaymentSettlementFactsDTO toSettlementFacts(StoredPaymentAcceptance value, PaymentExecutionDTO payment, boolean executionFenced);

    @Mapping(target = "payment", source = "payment")
    PaymentOutcomeDTO toOutcome(PaymentExecutionDTO payment, boolean executionFenced);

    @Mapping(target = "paymentId", source = "payment.id")
    @Mapping(target = "orderId", source = "payment.orderId")
    @Mapping(target = "allocationId", source = "payment.allocationId")
    @Mapping(target = "amountMinor", source = "payment.amount.cent")
    @Mapping(target = "currency", source = "payment.amount.currencyValue")
    @Mapping(target = "state", source = "payment.state")
    @Mapping(target = "activeAttemptId", source = "payment.activeAttemptId")
    @Mapping(target = "initiatedAt", source = "payment.initiatedAt")
    @Mapping(target = "version", source = "payment.version")
    @Mapping(target = "action", ignore = true)
    PaymentExecutionDTO toDTO(StoredPaymentAcceptance value);

    @Mapping(target = "terminalEvidenceId", ignore = true)
    StoredPaymentAcceptance toStored(
            PaymentOrder payment,
            String admissionId,
            String requestDigest,
            String callerServiceId,
            String intentPayload,
            String payloadHash);

    default OffsetDateTime external(LocalDateTime value) {
        return value == null ? null : BusinessTime.external(value);
    }
}
