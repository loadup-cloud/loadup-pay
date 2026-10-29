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

import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.reconciliation.app.model.StoredReconciliationDifference;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationDifferenceReader;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationResultGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationDifference;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationDifferenceReason;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationLine;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationRecordKind;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationResult;
import io.github.loadup.pay.reconciliation.infrastructure.converter.ReconciliationDifferenceStorageConverter;
import io.github.loadup.pay.reconciliation.infrastructure.converter.ReconciliationStorageConverter;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationBatchDO;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationDifferenceDO;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationEntryDO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class ReconciliationResultGatewayImpl implements ReconciliationResultGateway, ReconciliationDifferenceReader {
    private final IdGenerator ids;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final ReconciliationStorageConverter values;
    private final ReconciliationDifferenceStorageConverter converter;

    public ReconciliationResultGatewayImpl(IdGenerator ids,
                                           NamedParameterJdbcTemplate jdbc, ReconciliationStorageConverter values, ReconciliationDifferenceStorageConverter converter, @Qualifier("reconciliationClock") Clock clock) {
        this.ids = ids;
        this.jdbc = jdbc;
        this.clock = clock;
        this.values = values;
        this.converter = converter;
    }

    @Override
    public void insert(String tenant, String batchId, String runId, ReconciliationResult result) {
        ReconciliationStoreContext.writable(tenant);
        var batch = batch(tenant, batchId, true);
        if (result == null || !"RUNNING".equals(batch.getState()) || !Objects.equals(runId, batch.getRunId())
                || !Objects.equals(batch.getChannelInputId(), result.channel().artifactId()) || !Objects.equals(batch.getLedgerInputId(), result.ledger().artifactId())
                || !values.toScope(batch).equals(result.channel().scope())) throw new CommonException(SNAPSHOT_INVALID);
        var countArgs = new MapSqlParameterSource("tenant", tenant).addValue("batch", batchId);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM reconciliation_difference WHERE tenant_id = :tenant AND batch_id = :batch AND deleted = FALSE", countArgs, Long.class) != 0)
            throw new CommonException(SNAPSHOT_INVALID);
        var channel = referenced(tenant, batch.getChannelInputId(), result.differences(), true);
        var ledger = referenced(tenant, batch.getLedgerInputId(), result.differences(), false);
        var pending = new ArrayList<BeanPropertySqlParameterSource>();
        var now = LocalDateTime.now(clock);
        for (var value : result.differences()) {
            var fromChannel = reference(channel, value.channel());
            var fromLedger = reference(ledger, value.ledger());
            var row = converter.toDO(tenant, batchId, runId, value,
                    fromChannel == null ? null : fromChannel.getId(), fromLedger == null ? null : fromLedger.getId());
            row.setId(ids.generate());
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            pending.add(new BeanPropertySqlParameterSource(row));
        }
        for (int offset = 0; offset < pending.size(); offset += 500) {
            var rows = pending.subList(offset, Math.min(offset + 500, pending.size())).toArray(BeanPropertySqlParameterSource[]::new);
            int[] counts = jdbc.batchUpdate("""
                    INSERT INTO reconciliation_difference (id, tenant_id, created_at, updated_at, deleted, batch_id, run_id,
                        match_key_hash, kind, channel_transaction_no, channel_refund_no, channel_entry_id, ledger_entry_id,
                        channel_only, ledger_only, amount_mismatch, request_mismatch, state_mismatch)
                    VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :batchId, :runId, :matchKeyHash, :kind,
                        :channelTransactionNo, :channelRefundNo, :channelEntryId, :ledgerEntryId, :channelOnly, :ledgerOnly,
                        :amountMismatch, :requestMismatch, :stateMismatch)
                    """, rows);
            if (counts.length != rows.length) throw new CommonException(CONCURRENT_UPDATE);
            for (int count : counts)
                if (count != 1 && count != Statement.SUCCESS_NO_INFO) throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    @Override
    public PageResult<StoredReconciliationDifference> page(String tenant, String batchId, ReconciliationRecordKind kind,
                                                           ReconciliationDifferenceReason reason, int page, int size) {
        ReconciliationStoreContext.readable(tenant);
        if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly() || page < 1 || size < 1 || size > 100)
            throw new CommonException(INVALID_STATE);
        var batch = batch(tenant, batchId, false);
        if (!Set.of("MATCHED", "DIFFERENCES").contains(batch.getState())) throw new CommonException(INVALID_STATE);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("batch", batchId).addValue("limit", size)
                .addValue("offset", (long) (page - 1) * size);
        var where = new StringBuilder("tenant_id = :tenant AND batch_id = :batch AND deleted = FALSE");
        if (kind != null) {
            where.append(" AND kind = :kind");
            args.addValue("kind", kind.name());
        }
        if (reason != null) where.append(switch (reason) {
            case CHANNEL_ONLY -> " AND channel_only = TRUE";
            case LEDGER_ONLY -> " AND ledger_only = TRUE";
            case AMOUNT_MISMATCH -> " AND amount_mismatch = TRUE";
            case REQUEST_MISMATCH -> " AND request_mismatch = TRUE";
            case STATE_MISMATCH -> " AND state_mismatch = TRUE";
        });
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM reconciliation_difference WHERE " + where, args, Long.class);
        var rows = jdbc.query("SELECT * FROM reconciliation_difference WHERE " + where + " ORDER BY id ASC LIMIT :limit OFFSET :offset",
                args, BeanPropertyRowMapper.newInstance(ReconciliationDifferenceDO.class));
        var ids = new HashSet<String>();
        for (var row : rows) {
            if (row.getChannelEntryId() != null) ids.add(row.getChannelEntryId());
            if (row.getLedgerEntryId() != null) ids.add(row.getLedgerEntryId());
        }
        var indexed = new HashMap<String, ReconciliationEntryDO>();
        if (!ids.isEmpty())
            for (var row : jdbc.query("SELECT * FROM reconciliation_entry WHERE tenant_id = :tenant AND deleted = FALSE AND id IN (:ids)",
                    new MapSqlParameterSource("tenant", tenant).addValue("ids", ids), BeanPropertyRowMapper.newInstance(ReconciliationEntryDO.class)))
                indexed.put(row.getId(), row);
        var records = rows.stream().map(row -> {
            try {
                if (!Objects.equals(batch.getRunId(), row.getRunId())) throw new CommonException(SNAPSHOT_INVALID);
                var channel = line(indexed, row.getChannelEntryId(), batch.getChannelInputId());
                var ledger = line(indexed, row.getLedgerEntryId(), batch.getLedgerInputId());
                var value = converter.toDifference(row, channel, ledger);
                if (!value.key().fingerprint().equals(row.getMatchKeyHash()))
                    throw new CommonException(SNAPSHOT_INVALID);
                return converter.toStored(row, value);
            } catch (RuntimeException corrupt) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
        }).toList();
        return PageResult.of(records, total, page, size);
    }

    @Override
    public Optional<StoredReconciliationDifference> find(String tenant, String batchId, String differenceId) {
        ReconciliationStoreContext.readable(tenant);
        io.github.loadup.commons.validation.DomainChecks.id(differenceId);
        var batch = batch(tenant, batchId, false);
        if (!Set.of("MATCHED", "DIFFERENCES").contains(batch.getState())) throw new CommonException(INVALID_STATE);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("batch", batchId).addValue("id", differenceId);
        var row = jdbc.query("SELECT * FROM reconciliation_difference WHERE tenant_id = :tenant AND batch_id = :batch AND id = :id AND deleted = FALSE",
                args, BeanPropertyRowMapper.newInstance(ReconciliationDifferenceDO.class)).stream().findFirst().orElse(null);
        if (row == null) return Optional.empty();
        try {
            if (!differenceId.equals(row.getId()) || !batchId.equals(row.getBatchId()) || !Objects.equals(batch.getRunId(), row.getRunId()))
                throw new CommonException(SNAPSHOT_INVALID);
            var ids = new HashSet<String>();
            if (row.getChannelEntryId() != null) ids.add(row.getChannelEntryId());
            if (row.getLedgerEntryId() != null) ids.add(row.getLedgerEntryId());
            var indexed = new HashMap<String, ReconciliationEntryDO>();
            if (!ids.isEmpty())
                for (var entry : jdbc.query("SELECT * FROM reconciliation_entry WHERE tenant_id = :tenant AND deleted = FALSE AND id IN (:ids)",
                        new MapSqlParameterSource("tenant", tenant).addValue("ids", ids), BeanPropertyRowMapper.newInstance(ReconciliationEntryDO.class)))
                    indexed.put(entry.getId(), entry);
            var value = converter.toDifference(row, line(indexed, row.getChannelEntryId(), batch.getChannelInputId()), line(indexed, row.getLedgerEntryId(), batch.getLedgerInputId()));
            if (!value.key().fingerprint().equals(row.getMatchKeyHash())) throw new CommonException(SNAPSHOT_INVALID);
            return Optional.of(converter.toStored(row, value));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private Map<String, ReconciliationEntryDO> referenced(String tenant, String input, List<ReconciliationDifference> values, boolean channel) {
        var keys = values.stream().map(value -> channel ? value.channel() : value.ledger()).filter(Objects::nonNull)
                .map(value -> value.key().fingerprint()).toList();
        var result = new HashMap<String, ReconciliationEntryDO>();
        for (int offset = 0; offset < keys.size(); offset += 256) {
            var part = keys.subList(offset, Math.min(offset + 256, keys.size()));
            for (var row : jdbc.query("SELECT * FROM reconciliation_entry WHERE tenant_id = :tenant AND deleted = FALSE AND input_id = :input AND match_key_hash IN (:keys)",
                    new MapSqlParameterSource("tenant", tenant).addValue("input", input).addValue("keys", part), BeanPropertyRowMapper.newInstance(ReconciliationEntryDO.class))) {
                if (!input.equals(row.getInputId()) || result.put(row.getMatchKeyHash(), row) != null)
                    throw new CommonException(SNAPSHOT_INVALID);
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
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private ReconciliationBatchDO batch(String tenant, String id, boolean lock) {
        io.github.loadup.commons.validation.DomainChecks.id(id);
        if (lock && (jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())))
            throw new CommonException(INVALID_STATE);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", id);
        var row = jdbc.query("SELECT * FROM reconciliation_batch WHERE tenant_id = :tenant AND id = :id AND deleted = FALSE"
                        + (lock ? " FOR UPDATE" : ""), args, BeanPropertyRowMapper.newInstance(ReconciliationBatchDO.class))
                .stream().findFirst().orElse(null);
        if (row == null) throw new CommonException(NOT_FOUND);
        return row;
    }

}
