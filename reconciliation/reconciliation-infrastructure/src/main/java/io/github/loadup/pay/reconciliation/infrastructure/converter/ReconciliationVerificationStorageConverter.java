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
package io.github.loadup.pay.reconciliation.infrastructure.converter;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.commons.money.Money;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationKey;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewReference;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationReceipt;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationRequest;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationVerificationDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;

@Mapper(config = LoadUpMapStructConfig.class)
public interface ReconciliationVerificationStorageConverter {
    ReconciliationReviewReference reference(ReconciliationVerificationDO row);

    ReconciliationScope scope(ReconciliationVerificationDO row);

    ReconciliationKey key(ReconciliationVerificationDO row);

    @Mapping(target = "amount", expression = "java(amount(row))")
    ReconciliationVerificationReceipt toReceipt(ReconciliationVerificationDO row);

    default Money amount(ReconciliationVerificationDO row) {
        if ((row.getAmountMinor() == null) != (row.getCurrency() == null)) throw new CommonException(SNAPSHOT_INVALID);
        return row.getAmountMinor() == null ? null : Money.ofMinor(row.getAmountMinor(), row.getCurrency());
    }

    @Mapping(target = "reference", source = "row")
    @Mapping(target = "scope", source = "row")
    @Mapping(target = "key", source = "row")
    @Mapping(target = "receipt", expression = "java(receipt(row))")
    @Mapping(target = "version", source = "rowVersion")
    @Mapping(target = "complete", ignore = true)
    ReconciliationVerificationRequest toModel(ReconciliationVerificationDO row);

    default ReconciliationVerificationReceipt receipt(ReconciliationVerificationDO row) {
        if ("RECORDED".equals(row.getState())) {
            if (row.getStartedEpochMicros() == null || row.getFinishedEpochMicros() == null)
                throw new CommonException(SNAPSHOT_INVALID);
            return toReceipt(row);
        }
        if (row.getAcceptedEpochMicros() != null || row.getDisposition() != null || row.getBindingRevisionId() != null || row.getPaymentId() != null || row.getAttemptId() != null || row.getRefundId() != null || row.getSourceVersion() != null || row.getLocalState() != null || row.getOutcome() != null || row.getAmountMinor() != null || row.getCurrency() != null || row.getObservedChannelTransactionNo() != null || row.getObservedRefundTransactionNo() != null || row.getSucceededEpochMicros() != null || row.getSourceDigest() != null || row.getStartedEpochMicros() != null || row.getFinishedEpochMicros() != null)
            throw new CommonException(SNAPSHOT_INVALID);
        return null;
    }

    @Mapping(target = "state", constant = "INIT")
    @Mapping(target = "receipt", ignore = true)
    @Mapping(target = "version", constant = "0L")
    @Mapping(target = "complete", ignore = true)
    ReconciliationVerificationRequest toInitial(ReconciliationVerificationRequest value);

    @Mapping(target = "id", source = "value.id")
    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "differenceId", source = "value.reference.differenceId")
    @Mapping(target = "batchId", source = "value.reference.batchId")
    @Mapping(target = "runId", source = "value.reference.runId")
    @Mapping(target = "resultHash", source = "value.reference.resultHash")
    @Mapping(target = "matchKeyHash", source = "value.reference.matchKeyHash")
    @Mapping(target = "proposalId", source = "value.proposalId")
    @Mapping(target = "approvalEventId", source = "value.approvalEventId")
    @Mapping(target = "reviewVersion", source = "value.reviewVersion")
    @Mapping(target = "callerServiceId", source = "value.callerServiceId")
    @Mapping(target = "requestKey", source = "value.requestKey")
    @Mapping(target = "requestDigest", source = "value.requestDigest")
    @Mapping(target = "channel", source = "value.scope.channel")
    @Mapping(target = "accountScopeHash", source = "value.scope.accountScopeHash")
    @Mapping(target = "billDate", source = "value.scope.billDate")
    @Mapping(target = "statementProfile", source = "value.scope.statementProfile")
    @Mapping(target = "kind", source = "value.key.kind")
    @Mapping(target = "channelTransactionNo", source = "value.key.channelTransactionNo")
    @Mapping(target = "channelRefundNo", source = "value.key.channelRefundNo")
    @Mapping(target = "merchantPaymentNo", source = "value.merchantPaymentNo")
    @Mapping(target = "deliveryId", source = "value.deliveryId")
    @Mapping(target = "state", source = "value.state")
    @Mapping(target = "rowVersion", source = "value.version")
    @Mapping(target = "disposition", source = "value.receipt.disposition")
    @Mapping(target = "bindingRevisionId", source = "value.receipt.bindingRevisionId")
    @Mapping(target = "paymentId", source = "value.receipt.paymentId")
    @Mapping(target = "attemptId", source = "value.receipt.attemptId")
    @Mapping(target = "refundId", source = "value.receipt.refundId")
    @Mapping(target = "sourceVersion", source = "value.receipt.sourceVersion")
    @Mapping(target = "localState", source = "value.receipt.localState")
    @Mapping(target = "outcome", source = "value.receipt.outcome")
    @Mapping(target = "amountMinor", source = "value.receipt", qualifiedByName = "amountMinor")
    @Mapping(target = "currency", source = "value.receipt.amount.currencyValue")
    @Mapping(target = "observedChannelTransactionNo", source = "value.receipt.observedChannelTransactionNo")
    @Mapping(target = "observedRefundTransactionNo", source = "value.receipt.observedRefundTransactionNo")
    @Mapping(target = "succeededEpochMicros", source = "value.receipt.succeededEpochMicros")
    @Mapping(target = "sourceDigest", source = "value.receipt.sourceDigest")
    @Mapping(target = "startedEpochMicros", source = "value.receipt", qualifiedByName = "startedEpochMicros")
    @Mapping(target = "finishedEpochMicros", source = "value.receipt", qualifiedByName = "finishedEpochMicros")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    @Mapping(target = "acceptedEpochMicros", source = "value.receipt.acceptedEpochMicros")
    ReconciliationVerificationDO toDO(String tenant, ReconciliationVerificationRequest value);

    @Named("startedEpochMicros")
    default Long started(ReconciliationVerificationReceipt value) {
        return value == null ? null : value.startedEpochMicros();
    }

    @Named("finishedEpochMicros")
    default Long finished(ReconciliationVerificationReceipt value) {
        return value == null ? null : value.finishedEpochMicros();
    }

    @Named("amountMinor")
    default Long cents(ReconciliationVerificationReceipt value) {
        return value == null || value.amount() == null ? null : value.amount().getCent();
    }
}
