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

import io.github.loadup.commons.mapping.LoadUpMapStructConfig;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationBatch;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationKey;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationLine;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationManifest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationResultSummary;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationTotals;
import io.github.loadup.pay.reconciliation.domain.model.StoredReconciliationBatch;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationBatchDO;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationDifferenceDO;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationEntryDO;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationInputDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = LoadUpMapStructConfig.class)
public interface ReconciliationStorageConverter {
    ReconciliationScope toScope(ReconciliationBatchDO row);

    ReconciliationScope toScope(ReconciliationInputDO row);

    @Mapping(target = "artifactId", source = "id")
    @Mapping(target = "scope", source = "row")
    @Mapping(target = "totals", source = "row")
    ReconciliationManifest toManifest(ReconciliationInputDO row);

    ReconciliationTotals toTotals(ReconciliationInputDO row);

    ReconciliationResultSummary toSummary(ReconciliationBatchDO row);

    @Mapping(target = "id", source = "row.id")
    @Mapping(target = "scope", source = "row")
    @Mapping(target = "state", source = "row.state")
    @Mapping(target = "runId", source = "row.runId")
    @Mapping(target = "version", source = "row.rowVersion")
    @Mapping(target = "channel", source = "channel")
    @Mapping(target = "ledger", source = "ledger")
    @Mapping(target = "result", source = "result")
    @Mapping(target = "attach", ignore = true)
    @Mapping(target = "begin", ignore = true)
    ReconciliationBatch toBatch(ReconciliationBatchDO row, ReconciliationManifest channel, ReconciliationManifest ledger, ReconciliationResultSummary result);

    @Mapping(target = "batch", source = "batch")
    @Mapping(target = "callerServiceId", source = "row.callerServiceId")
    @Mapping(target = "requestKey", source = "row.requestKey")
    @Mapping(target = "requestDigest", source = "row.requestDigest")
    @Mapping(target = "channelAccountCode", source = "row.channelAccountCode")
    StoredReconciliationBatch toStored(ReconciliationBatchDO row, ReconciliationBatch batch);

    @Mapping(target = "id", source = "value.batch.id")

    @Mapping(target = "callerServiceId", source = "value.callerServiceId")
    @Mapping(target = "requestKey", source = "value.requestKey")
    @Mapping(target = "requestDigest", source = "value.requestDigest")
    @Mapping(target = "channelAccountCode", source = "value.channelAccountCode")
    @Mapping(target = "channel", source = "value.batch.scope.channel")
    @Mapping(target = "accountScopeHash", source = "value.batch.scope.accountScopeHash")
    @Mapping(target = "billDate", source = "value.batch.scope.billDate")
    @Mapping(target = "statementProfile", source = "value.batch.scope.statementProfile")
    @Mapping(target = "state", source = "value.batch.state")
    @Mapping(target = "channelInputId", source = "value.batch.channel.artifactId")
    @Mapping(target = "ledgerInputId", source = "value.batch.ledger.artifactId")
    @Mapping(target = "runId", source = "value.batch.runId")
    @Mapping(target = "matchedCount", source = "value.batch.result.matchedCount")
    @Mapping(target = "differenceCount", source = "value.batch.result.differenceCount")
    @Mapping(target = "resultHash", source = "value.batch.result.resultHash")
    @Mapping(target = "rowVersion", source = "value.batch.version")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    ReconciliationBatchDO toDO(String tenant, StoredReconciliationBatch value);

    @Mapping(target = "id", source = "value.artifactId")

    @Mapping(target = "side", source = "value.side")
    @Mapping(target = "channel", source = "value.scope.channel")
    @Mapping(target = "accountScopeHash", source = "value.scope.accountScopeHash")
    @Mapping(target = "billDate", source = "value.scope.billDate")
    @Mapping(target = "statementProfile", source = "value.scope.statementProfile")
    @Mapping(target = "fileId", source = "fileId")
    @Mapping(target = "artifactSha256", source = "value.artifactSha256")
    @Mapping(target = "normalizedSha256", source = "value.normalizedSha256")
    @Mapping(target = "sourceCaptureId", source = "value.sourceCaptureId")
    @Mapping(target = "parserRevision", source = "value.parserRevision")
    @Mapping(target = "state", constant = "SEALED")
    @Mapping(target = "paymentCount", source = "value.totals.paymentCount")
    @Mapping(target = "paymentAmountMinor", source = "value.totals.paymentAmountMinor")
    @Mapping(target = "refundCount", source = "value.totals.refundCount")
    @Mapping(target = "refundAmountMinor", source = "value.totals.refundAmountMinor")
    @Mapping(target = "rejectionCode", ignore = true)
    @Mapping(target = "rowVersion", constant = "0L")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    ReconciliationInputDO toInput(String tenant, String fileId, ReconciliationManifest value);

    @Mapping(target = "key", source = "row")
    @Mapping(target = "amount", expression = "java(io.github.loadup.commons.validation.DomainChecks.cnyAmount(row.getAmountMinor(), row.getCurrency()))")
    @Mapping(target = "status", source = "state")
    ReconciliationLine toLine(ReconciliationEntryDO row);

    ReconciliationKey toKey(ReconciliationEntryDO row);

    ReconciliationKey toKey(ReconciliationDifferenceDO row);

    @Mapping(target = "id", ignore = true)

    @Mapping(target = "inputId", source = "inputId")
    @Mapping(target = "recordNumber", source = "value.recordNumber")
    @Mapping(target = "matchKeyHash", expression = "java(value.key().fingerprint())")
    @Mapping(target = "kind", source = "value.key.kind")
    @Mapping(target = "channelTransactionNo", source = "value.key.channelTransactionNo")
    @Mapping(target = "channelRefundNo", source = "value.key.channelRefundNo")
    @Mapping(target = "merchantPaymentNo", source = "value.merchantPaymentNo")
    @Mapping(target = "amountMinor", source = "value.amount.cent")
    @Mapping(target = "currency", source = "value.amount.currencyValue")
    @Mapping(target = "state", source = "value.status")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "false")
    ReconciliationEntryDO toEntry(String tenant, String inputId, ReconciliationLine value);
}
