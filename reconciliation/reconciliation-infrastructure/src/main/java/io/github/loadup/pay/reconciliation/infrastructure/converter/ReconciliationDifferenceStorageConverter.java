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
import io.github.loadup.pay.reconciliation.app.model.StoredReconciliationDifference;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationDifference;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationDifferenceReason;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationKey;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationLine;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationDifferenceDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.EnumSet;
import java.util.Set;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;

@Mapper(config = LoadUpMapStructConfig.class, imports = ReconciliationDifferenceReason.class)
public interface ReconciliationDifferenceStorageConverter {
    @Mapping(target = "key", source = "row")
    @Mapping(target = "channel", source = "channel")
    @Mapping(target = "ledger", source = "ledger")
    @Mapping(target = "reasons", expression = "java(reasons(row))")
    ReconciliationDifference toDifference(ReconciliationDifferenceDO row, ReconciliationLine channel, ReconciliationLine ledger);

    ReconciliationKey toKey(ReconciliationDifferenceDO row);

    @Mapping(target = "id", source = "row.id")
    @Mapping(target = "batchId", source = "row.batchId")
    @Mapping(target = "runId", source = "row.runId")
    @Mapping(target = "difference", source = "difference")
    StoredReconciliationDifference toStored(ReconciliationDifferenceDO row, ReconciliationDifference difference);

    default Set<ReconciliationDifferenceReason> reasons(ReconciliationDifferenceDO row) {
        var result = EnumSet.noneOf(ReconciliationDifferenceReason.class);
        flag(result, row.getChannelOnly(), ReconciliationDifferenceReason.CHANNEL_ONLY);
        flag(result, row.getLedgerOnly(), ReconciliationDifferenceReason.LEDGER_ONLY);
        flag(result, row.getAmountMismatch(), ReconciliationDifferenceReason.AMOUNT_MISMATCH);
        flag(result, row.getRequestMismatch(), ReconciliationDifferenceReason.REQUEST_MISMATCH);
        flag(result, row.getStateMismatch(), ReconciliationDifferenceReason.STATE_MISMATCH);
        return Set.copyOf(result);
    }

    private void flag(Set<ReconciliationDifferenceReason> result, Integer flag, ReconciliationDifferenceReason reason) {
        if (flag == null || flag < 0 || flag > 1) throw new CommonException(SNAPSHOT_INVALID);
        if (flag == 1) result.add(reason);
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tenantId", source = "tenant")
    @Mapping(target = "batchId", source = "batchId")
    @Mapping(target = "runId", source = "runId")
    @Mapping(target = "matchKeyHash", expression = "java(value.key().fingerprint())")
    @Mapping(target = "kind", source = "value.key.kind")
    @Mapping(target = "channelTransactionNo", source = "value.key.channelTransactionNo")
    @Mapping(target = "channelRefundNo", source = "value.key.channelRefundNo")
    @Mapping(target = "channelEntryId", source = "channelEntryId")
    @Mapping(target = "ledgerEntryId", source = "ledgerEntryId")
    @Mapping(target = "channelOnly", expression = "java(value.reasons().contains(ReconciliationDifferenceReason.CHANNEL_ONLY) ? 1 : 0)")
    @Mapping(target = "ledgerOnly", expression = "java(value.reasons().contains(ReconciliationDifferenceReason.LEDGER_ONLY) ? 1 : 0)")
    @Mapping(target = "amountMismatch", expression = "java(value.reasons().contains(ReconciliationDifferenceReason.AMOUNT_MISMATCH) ? 1 : 0)")
    @Mapping(target = "requestMismatch", expression = "java(value.reasons().contains(ReconciliationDifferenceReason.REQUEST_MISMATCH) ? 1 : 0)")
    @Mapping(target = "stateMismatch", expression = "java(value.reasons().contains(ReconciliationDifferenceReason.STATE_MISMATCH) ? 1 : 0)")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", constant = "0")
    ReconciliationDifferenceDO toDO(String tenant, String batchId, String runId, ReconciliationDifference value,
                                    String channelEntryId, String ledgerEntryId);
}
