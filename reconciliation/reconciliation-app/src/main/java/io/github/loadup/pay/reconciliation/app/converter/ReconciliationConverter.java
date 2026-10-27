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
import io.github.loadup.pay.reconciliation.client.dto.*;
import io.github.loadup.pay.reconciliation.domain.model.*;
import io.github.loadup.pay.reconciliation.app.model.StoredReconciliationDifference;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface ReconciliationConverter {
    @Mapping(target = "batchId", source = "id")
    @Mapping(target = "channel", source = "scope.channel")
    @Mapping(target = "accountScopeHash", source = "scope.accountScopeHash")
    @Mapping(target = "billDate", source = "scope.billDate")
    @Mapping(target = "statementProfile", source = "scope.statementProfile")
    @Mapping(target = "channelInput", source = "channel")
    @Mapping(target = "ledgerInput", source = "ledger")
    @Mapping(target = "matchedCount", source = "result.matchedCount")
    @Mapping(target = "differenceCount", source = "result.differenceCount")
    @Mapping(target = "resultHash", source = "result.resultHash")
    ReconciliationBatchDTO toDTO(ReconciliationBatch value);

    @Mapping(target = "paymentCount", source = "totals.paymentCount")
    @Mapping(target = "paymentAmountMinor", source = "totals.paymentAmountMinor")
    @Mapping(target = "refundCount", source = "totals.refundCount")
    @Mapping(target = "refundAmountMinor", source = "totals.refundAmountMinor")
    ReconciliationInputDTO toDTO(ReconciliationManifest value);

    @Mapping(target = "amountMinor", source = "amount.cent")
    @Mapping(target = "currency", source = "amount.currencyValue")
    ReconciliationLineDTO toDTO(ReconciliationLine value);

    @Mapping(target = "differenceId", source = "id")
    @Mapping(target = "kind", source = "difference.key.kind")
    @Mapping(target = "channelTransactionNo", source = "difference.key.channelTransactionNo")
    @Mapping(target = "channelRefundNo", source = "difference.key.channelRefundNo")
    @Mapping(target = "reasons", source = "difference.reasons")
    @Mapping(target = "channel", source = "difference.channel")
    @Mapping(target = "ledger", source = "difference.ledger")
    ReconciliationDifferenceDTO toDTO(StoredReconciliationDifference value);

    @Mapping(target = "batch", source = "batch")
    @Mapping(target = "callerServiceId", source = "original.callerServiceId")
    @Mapping(target = "requestKey", source = "original.requestKey")
    @Mapping(target = "requestDigest", source = "original.requestDigest")
    @Mapping(target = "channelAccountCode", source = "original.channelAccountCode")
    StoredReconciliationBatch withBatch(StoredReconciliationBatch original, ReconciliationBatch batch);

    StoredReconciliationBatch toStored(ReconciliationBatch batch, String callerServiceId, String requestKey, String requestDigest, String channelAccountCode);
    ReconciliationSide toSide(io.github.loadup.pay.reconciliation.client.enums.ReconciliationSide value);
    ReconciliationRecordKind toKind(io.github.loadup.pay.reconciliation.client.enums.ReconciliationRecordKind value);
    ReconciliationDifferenceReason toReason(io.github.loadup.pay.reconciliation.client.enums.ReconciliationDifferenceReason value);
}
