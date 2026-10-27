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
package io.github.loadup.pay.reconciliation.infrastructure.repository;

import com.mybatisflex.core.query.QueryWrapper;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.reconciliation.app.model.*;
import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationDifferenceReader;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationResultGateway;
import io.github.loadup.pay.reconciliation.domain.model.*;
import io.github.loadup.pay.reconciliation.infrastructure.converter.*;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.*;
import io.github.loadup.pay.reconciliation.infrastructure.mapper.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.*;
import static io.github.loadup.pay.reconciliation.infrastructure.dataobject.table.Tables.*;

@Repository
public class ReconciliationResultGatewayImpl implements ReconciliationResultGateway, ReconciliationDifferenceReader {
    private final ReconciliationDifferenceDOMapper differences;
    private final ReconciliationEntryDOMapper entries;
    private final ReconciliationBatchDOMapper batches;
    private final ReconciliationStorageConverter values;
    private final ReconciliationDifferenceStorageConverter converter;
    public ReconciliationResultGatewayImpl(ReconciliationDifferenceDOMapper differences, ReconciliationEntryDOMapper entries,
            ReconciliationBatchDOMapper batches, ReconciliationStorageConverter values, ReconciliationDifferenceStorageConverter converter) {
        this.differences = differences; this.entries = entries; this.batches = batches; this.values = values; this.converter = converter;
    }
    @Override public void insert(String tenant, String batchId, String runId, ReconciliationResult result) {
        ReconciliationStoreContext.writable(tenant);
        var batch = batch(tenant, batchId, true);
        if (result == null || !"RUNNING".equals(batch.getState()) || !Objects.equals(runId, batch.getRunId())
                || !Objects.equals(batch.getChannelInputId(), result.channel().artifactId()) || !Objects.equals(batch.getLedgerInputId(), result.ledger().artifactId())
                || !values.toScope(batch).equals(result.channel().scope())) throw new CommonException(SNAPSHOT_INVALID);
        if (differences.selectCountByQuery(scope(tenant, batchId)) != 0) throw new CommonException(SNAPSHOT_INVALID);
        var channel = referenced(tenant, batch.getChannelInputId(), result.differences(), true);
        var ledger = referenced(tenant, batch.getLedgerInputId(), result.differences(), false);
        for (var value : result.differences()) {
            var fromChannel = reference(channel, value.channel());
            var fromLedger = reference(ledger, value.ledger());
            var row = converter.toDO(tenant, batchId, runId, value,
                    fromChannel == null ? null : fromChannel.getId(), fromLedger == null ? null : fromLedger.getId());
            if (differences.insert(row) != 1) throw new CommonException(CONCURRENT_UPDATE);
        }
    }
    @Override public PageResult<StoredReconciliationDifference> page(String tenant, String batchId, ReconciliationRecordKind kind,
            ReconciliationDifferenceReason reason, int page, int size) {
        ReconciliationStoreContext.readable(tenant);
        if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly() || page < 1 || size < 1 || size > 100)
            throw new CommonException(INVALID_STATE);
        var batch = batch(tenant, batchId, false);
        if (!Set.of("MATCHED", "DIFFERENCES").contains(batch.getState())) throw new CommonException(INVALID_STATE);
        var query = scope(tenant, batchId);
        if (kind != null) query.and(RECONCILIATION_DIFFERENCE_DO.KIND.eq(kind.name()));
        if (reason != null) switch (reason) {
            case CHANNEL_ONLY -> query.and(RECONCILIATION_DIFFERENCE_DO.CHANNEL_ONLY.eq(1));
            case LEDGER_ONLY -> query.and(RECONCILIATION_DIFFERENCE_DO.LEDGER_ONLY.eq(1));
            case AMOUNT_MISMATCH -> query.and(RECONCILIATION_DIFFERENCE_DO.AMOUNT_MISMATCH.eq(1));
            case REQUEST_MISMATCH -> query.and(RECONCILIATION_DIFFERENCE_DO.REQUEST_MISMATCH.eq(1));
            case STATE_MISMATCH -> query.and(RECONCILIATION_DIFFERENCE_DO.STATE_MISMATCH.eq(1));
        }
        long total = differences.selectCountByQuery(query);
        var rows = differences.selectListByQuery(query.orderBy(RECONCILIATION_DIFFERENCE_DO.ID.asc()).limit(size).offset((long)(page - 1) * size));
        var ids = new HashSet<String>();
        for (var row : rows) { if (row.getChannelEntryId() != null) ids.add(row.getChannelEntryId()); if (row.getLedgerEntryId() != null) ids.add(row.getLedgerEntryId()); }
        var indexed = new HashMap<String, ReconciliationEntryDO>();
        if (!ids.isEmpty()) for (var row : entries.selectListByQuery(QueryWrapper.create().where(RECONCILIATION_ENTRY_DO.TENANT_ID.eq(tenant))
                .and(RECONCILIATION_ENTRY_DO.DELETED.eq(0)).and(RECONCILIATION_ENTRY_DO.ID.in(ids)))) indexed.put(row.getId(), row);
        var records = rows.stream().map(row -> {
            try {
                if (!Objects.equals(batch.getRunId(), row.getRunId())) throw new CommonException(SNAPSHOT_INVALID);
                var channel = line(indexed, row.getChannelEntryId(), batch.getChannelInputId());
                var ledger = line(indexed, row.getLedgerEntryId(), batch.getLedgerInputId());
                var value = converter.toDifference(row, channel, ledger);
                if (!value.key().fingerprint().equals(row.getMatchKeyHash())) throw new CommonException(SNAPSHOT_INVALID);
                return converter.toStored(row, value);
            } catch (RuntimeException corrupt) { throw new CommonException(SNAPSHOT_INVALID); }
        }).toList();
        return PageResult.of(records, total, page, size);
    }
    private Map<String, ReconciliationEntryDO> referenced(String tenant, String input, List<ReconciliationDifference> values, boolean channel) {
        var keys = values.stream().map(value -> channel ? value.channel() : value.ledger()).filter(Objects::nonNull)
                .map(value -> value.key().fingerprint()).toList();
        var result = new HashMap<String, ReconciliationEntryDO>();
        for (int offset = 0; offset < keys.size(); offset += 256) {
            var part = keys.subList(offset, Math.min(offset + 256, keys.size()));
            for (var row : entries.selectListByQuery(QueryWrapper.create().where(RECONCILIATION_ENTRY_DO.TENANT_ID.eq(tenant))
                    .and(RECONCILIATION_ENTRY_DO.DELETED.eq(0)).and(RECONCILIATION_ENTRY_DO.INPUT_ID.eq(input))
                    .and(RECONCILIATION_ENTRY_DO.MATCH_KEY_HASH.in(part)))) {
                if (!input.equals(row.getInputId()) || result.put(row.getMatchKeyHash(), row) != null) throw new CommonException(SNAPSHOT_INVALID);
            }
        }
        return result;
    }
    private ReconciliationEntryDO reference(Map<String, ReconciliationEntryDO> entries, ReconciliationLine expected) {
        if (expected == null) return null;
        var row = entries.get(expected.key().fingerprint());
        if (row == null || !expected.equals(read(row))) throw new CommonException(SNAPSHOT_INVALID);
        return row;
    }
    private ReconciliationLine line(Map<String, ReconciliationEntryDO> entries, String id, String inputId) {
        if (id == null) return null;
        var row = entries.get(id);
        if (row == null || !inputId.equals(row.getInputId())) throw new CommonException(SNAPSHOT_INVALID);
        return read(row);
    }
    private ReconciliationLine read(ReconciliationEntryDO row) {
        try {
            var value = values.toLine(row);
            if (!value.key().fingerprint().equals(row.getMatchKeyHash())) throw new CommonException(SNAPSHOT_INVALID);
            return value;
        } catch (RuntimeException corrupt) { throw new CommonException(SNAPSHOT_INVALID); }
    }
    private ReconciliationBatchDO batch(String tenant, String id, boolean lock) {
        io.github.loadup.commons.validation.DomainChecks.id(id);
        var query = QueryWrapper.create().where(RECONCILIATION_BATCH_DO.TENANT_ID.eq(tenant)).and(RECONCILIATION_BATCH_DO.DELETED.eq(0))
                .and(RECONCILIATION_BATCH_DO.ID.eq(id));
        var row = batches.selectOneByQuery(lock ? query.forUpdate() : query);
        if (row == null) throw new CommonException(NOT_FOUND);
        return row;
    }
    private QueryWrapper scope(String tenant, String batchId) {
        return QueryWrapper.create().where(RECONCILIATION_DIFFERENCE_DO.TENANT_ID.eq(tenant)).and(RECONCILIATION_DIFFERENCE_DO.DELETED.eq(0))
                .and(RECONCILIATION_DIFFERENCE_DO.BATCH_ID.eq(batchId));
    }
}
