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
package io.github.loadup.pay.reconciliation.app.converter;

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.components.outbox.OutboxRecord;
import io.github.loadup.pay.payment.client.dto.PaymentVerificationDTO;
import io.github.loadup.pay.payment.client.query.PaymentVerificationQuery;
import io.github.loadup.pay.reconciliation.app.model.ApprovedReconciliationReview;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationVerificationDTO;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationVerificationReceiptDTO;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationReceipt;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationRequest;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;

@Mapper(config = LoadUpMapStructConfig.class)
public interface ReconciliationVerificationConverter {
    @Mapping(target = "reference", source = "approved.reference")
    @Mapping(target = "proposalId", source = "approved.proposalId")
    @Mapping(target = "approvalEventId", source = "approved.approvalEventId")
    @Mapping(target = "reviewVersion", source = "approved.reviewVersion")
    @Mapping(target = "scope", source = "approved.scope")
    @Mapping(target = "key", source = "approved.key")
    @Mapping(target = "merchantPaymentNo", source = "approved.merchantPaymentNo")
    @Mapping(target = "id", source = "id")
    @Mapping(target = "callerServiceId", source = "callerServiceId")
    @Mapping(target = "requestKey", source = "requestKey")
    @Mapping(target = "requestDigest", source = "requestDigest")
    @Mapping(target = "deliveryId", source = "deliveryId")
    @Mapping(target = "state", constant = "INIT")
    @Mapping(target = "receipt", ignore = true)
    @Mapping(target = "version", constant = "0L")
    @Mapping(target = "complete", ignore = true)
    ReconciliationVerificationRequest toRequest(ApprovedReconciliationReview approved, String id, String callerServiceId, String requestKey, String requestDigest, String deliveryId);

    @Mapping(target = "sourceRequestId", source = "id")
    @Mapping(target = "channel", source = "scope.channel")
    @Mapping(target = "accountScopeHash", source = "scope.accountScopeHash")
    @Mapping(target = "kind", source = "key.kind")
    @Mapping(target = "channelTransactionNo", source = "key.channelTransactionNo")
    @Mapping(target = "channelRefundNo", source = "key.channelRefundNo")
    PaymentVerificationQuery toQuery(ReconciliationVerificationRequest value);

    @Mapping(target = "startedEpochMicros", source = "startedAt", qualifiedByName = "verificationMicros")
    @Mapping(target = "finishedEpochMicros", source = "finishedAt", qualifiedByName = "verificationMicros")
    @Mapping(target = "succeededEpochMicros", source = "succeededAt", qualifiedByName = "verificationMicros")
    @Mapping(target = "acceptedEpochMicros", source = "acceptedAt", qualifiedByName = "verificationMicros")
    ReconciliationVerificationReceipt toReceipt(PaymentVerificationDTO value);

    @Mapping(target = "startedAt", source = "startedEpochMicros", qualifiedByName = "verificationExternal")
    @Mapping(target = "finishedAt", source = "finishedEpochMicros", qualifiedByName = "verificationExternal")
    @Mapping(target = "succeededAt", source = "succeededEpochMicros", qualifiedByName = "verificationExternal")
    @Mapping(target = "acceptedAt", source = "acceptedEpochMicros", qualifiedByName = "verificationExternal")
    ReconciliationVerificationReceiptDTO toDTO(ReconciliationVerificationReceipt value, @Context Clock clock);

    @Mapping(target = "requestId", source = "value.id")
    @Mapping(target = "batchId", source = "value.reference.batchId")
    @Mapping(target = "differenceId", source = "value.reference.differenceId")
    @Mapping(target = "proposalId", source = "value.proposalId")
    @Mapping(target = "approvalEventId", source = "value.approvalEventId")
    @Mapping(target = "reviewVersion", source = "value.reviewVersion")
    @Mapping(target = "state", source = "value.state")
    @Mapping(target = "receipt", source = "value.receipt")
    @Mapping(target = "version", source = "value.version")
    @Mapping(target = "deliveryId", source = "value.deliveryId")
    @Mapping(target = "deliveryStatus", source = "delivery.status")
    @Mapping(target = "attempts", source = "delivery.attempts")
    ReconciliationVerificationDTO toDTO(ReconciliationVerificationRequest value, OutboxRecord delivery, @Context Clock clock);

    @Named("verificationMicros")
    default Long micros(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        if (value.getNano() % 1000 != 0) {
            throw new io.github.loadup.commons.error.CommonException(io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INPUT_INVALID);
        }
        return Math.addExact(Math.multiplyExact(value.toEpochSecond(), 1000000L), value.getNano() / 1000L);
    }

    @Named("verificationExternal")
    default OffsetDateTime external(Long value, @Context Clock clock) {
        return value == null ? null : OffsetDateTime.ofInstant(Instant.ofEpochSecond(Math.floorDiv(value, 1000000L), Math.floorMod(value, 1000000L) * 1000L), clock.getZone());
    }
}
