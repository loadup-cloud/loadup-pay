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
import io.github.loadup.pay.payment.client.dto.RefundSettlementFactsDTO;
import io.github.loadup.pay.payment.client.event.RefundOutcomeEventDTO;
import io.github.loadup.pay.payment.domain.model.*;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.pay.limitcenter.client.command.LimitRefundRestoreCommand;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface RefundSettlementConverter {
    @Mapping(target = "operationId", source = "data.operationId")
    @Mapping(target = "paymentId", source = "data.paymentId")
    @Mapping(target = "refundId", source = "data.refundId")
    @Mapping(target = "refundVersion", source = "data.refundVersion")
    @Mapping(target = "businessEventId", source = "businessEventId")
    @Mapping(target = "refundAmountMinor", source = "data.amountMinor")
    @Mapping(target = "cumulativePaymentRefundedMinor", source = "data.cumulativePaymentRefundedMinor")
    @Mapping(target = "currency", source = "data.currency")
    @Mapping(target = "refundEvidenceId", source = "data.evidenceId")
    LimitRefundRestoreCommand toRestore(EventEnvelope<RefundOutcomeEventDTO> event);

    @Mapping(target = "refundId", source = "refund.id")
    @Mapping(target = "refundRequestId", source = "refund.refundRequestId")
    @Mapping(target = "paymentId", source = "refund.paymentId")
    @Mapping(target = "orderId", source = "request.orderId")
    @Mapping(target = "operationId", source = "payment.limitOperationId")
    @Mapping(target = "payeeMerchantId", source = "request.payeeMerchantId")
    @Mapping(target = "amountMinor", source = "refund.amount.cent")
    @Mapping(target = "currency", source = "refund.amount.currencyValue")
    @Mapping(target = "state", source = "refund.state")
    @Mapping(target = "refundVersion", source = "refund.version")
    @Mapping(target = "cumulativePaymentRefundedMinor", source = "capacity.refundedMinor")
    @Mapping(target = "evidenceId", source = "refund.terminalEvidenceId")
    RefundOutcomeEventDTO toOutcome(RefundOrder refund, RefundRequest request, PaymentOrder payment, PaymentRefundCapacity capacity);

    @Mapping(target = "refund", source = "refund")
    @Mapping(target = "paymentAmountMinor", source = "original.payment.amount.cent")
    @Mapping(target = "paymentVersion", source = "original.payment.version")
    @Mapping(target = "paymentEvidenceId", source = "original.terminalEvidenceId")
    @Mapping(target = "paymentAttemptId", source = "original.payment.successfulAttemptId")
    @Mapping(target = "paymentInitiatedAt", source = "original.payment.initiatedAt")
    @Mapping(target = "paymentRefundVersion", source = "capacity.version")
    RefundSettlementFactsDTO toFacts(RefundOutcomeEventDTO refund, StoredPaymentAcceptance original, PaymentRefundCapacity capacity);

    default OffsetDateTime external(LocalDateTime value) { return value == null ? null : BusinessTime.external(value); }
}
