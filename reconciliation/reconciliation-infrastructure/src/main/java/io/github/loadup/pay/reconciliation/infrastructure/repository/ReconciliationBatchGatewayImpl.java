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

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationWriteConflictException;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationBatchGateway;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationInputGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationResultSummary;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationState;
import io.github.loadup.pay.reconciliation.domain.model.StoredReconciliationBatch;
import io.github.loadup.pay.reconciliation.infrastructure.converter.ReconciliationStorageConverter;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationBatchDO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class ReconciliationBatchGatewayImpl implements ReconciliationBatchGateway {
    private final Clock clock;
    private final NamedParameterJdbcTemplate jdbc;
    private final ReconciliationInputGateway inputs;
    private final ReconciliationStorageConverter converter;

    public ReconciliationBatchGatewayImpl(ReconciliationInputGateway inputs, ReconciliationStorageConverter converter, NamedParameterJdbcTemplate jdbc, @Qualifier("reconciliationClock") Clock clock) {
        this.clock = clock;
        this.jdbc = jdbc;
        this.inputs = inputs;
        this.converter = converter;
    }

    @Override
    public Optional<StoredReconciliationBatch> findRequest(String tenant, String caller, String requestKey) {
        ReconciliationStoreContext.readable(tenant);
        io.github.loadup.commons.validation.DomainChecks.id(caller);
        io.github.loadup.commons.validation.DomainChecks.text(requestKey, 128);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("caller", caller).addValue("key", requestKey);
        return optional(tenant, row("caller_service_id = :caller AND request_key = :key", args, false));
    }

    @Override
    public Optional<StoredReconciliationBatch> find(String tenant, String id) {
        ReconciliationStoreContext.readable(tenant);
        io.github.loadup.commons.validation.DomainChecks.id(id);
        return optional(tenant, row("id = :id", new MapSqlParameterSource("tenant", tenant).addValue("id", id), false));
    }

    @Override
    public Optional<StoredReconciliationBatch> findRun(String tenant, String runId) {
        ReconciliationStoreContext.readable(tenant);
        io.github.loadup.commons.validation.DomainChecks.id(runId);
        return optional(tenant, row("run_id = :run", new MapSqlParameterSource("tenant", tenant).addValue("run", runId), false));
    }

    @Override
    public Optional<StoredReconciliationBatch> lock(String tenant, String id) {
        ReconciliationStoreContext.writable(tenant);
        io.github.loadup.commons.validation.DomainChecks.id(id);
        return optional(tenant, row("id = :id", new MapSqlParameterSource("tenant", tenant).addValue("id", id), true));
    }

    @Override
    public void insert(String tenant, StoredReconciliationBatch value) {
        ReconciliationStoreContext.writable(tenant);
        if (value == null || value.batch().state() != ReconciliationState.INIT || value.batch().version() != 0
                || value.batch().channel() != null || value.batch().ledger() != null)
            throw new CommonException(INVALID_STATE);
        requireJdbc();
        var row = converter.toDO(tenant, value);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO reconciliation_batch (id, tenant_id, created_at, updated_at, deleted, caller_service_id, request_key,
                    request_digest, channel_account_code, channel, account_scope_hash, bill_date, statement_profile, state,
                    channel_input_id, ledger_input_id, run_id, matched_count, difference_count, result_hash, row_version)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :callerServiceId, :requestKey, :requestDigest, :channelAccountCode,
                    :channel, :accountScopeHash, :billDate, :statementProfile, :state, :channelInputId, :ledgerInputId, :runId,
                    :matchedCount, :differenceCount, :resultHash, :rowVersion)
                ON CONFLICT (tenant_id, caller_service_id, request_key) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0)
            throw new ReconciliationWriteConflictException("reconciliation_batch business key already exists");
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void update(String tenant, StoredReconciliationBatch value, long expectedVersion) {
        ReconciliationStoreContext.writable(tenant);
        if (value == null || value.batch().version() != Math.incrementExact(expectedVersion))
            throw new CommonException(INVALID_STATE);
        var before = optional(tenant, row("id = :id", new MapSqlParameterSource("tenant", tenant).addValue("id", value.batch().id()), true))
                .orElseThrow(() -> new CommonException(NOT_FOUND));
        if (before.batch().version() != expectedVersion) throw new CommonException(CONCURRENT_UPDATE);
        if (!before.batch().scope().equals(value.batch().scope()) || !before.callerServiceId().equals(value.callerServiceId())
                || !before.requestKey().equals(value.requestKey()) || !before.requestDigest().equals(value.requestDigest())
                || !before.channelAccountCode().equals(value.channelAccountCode())
                || before.batch().channel() != null && !before.batch().channel().equals(value.batch().channel())
                || before.batch().ledger() != null && !before.batch().ledger().equals(value.batch().ledger()))
            throw new CommonException(SNAPSHOT_INVALID);
        boolean allowed = switch (before.batch().state()) {
            case INIT ->
                    (value.batch().state() == ReconciliationState.INIT || value.batch().state() == ReconciliationState.READY)
                            && ((before.batch().channel() == null && value.batch().channel() != null ? 1 : 0)
                            + (before.batch().ledger() == null && value.batch().ledger() != null ? 1 : 0) == 1);
            case READY -> value.batch().state() == ReconciliationState.RUNNING;
            case RUNNING ->
                    (value.batch().state() == ReconciliationState.MATCHED || value.batch().state() == ReconciliationState.DIFFERENCES)
                            && before.batch().runId().equals(value.batch().runId());
            default -> false;
        };
        if (!allowed) throw new CommonException(INVALID_STATE);
        requireJdbc();
        var row = converter.toDO(tenant, value);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", value.batch().id()).addValue("expected", expectedVersion)
                .addValue("state", row.getState()).addValue("channelInput", row.getChannelInputId()).addValue("ledgerInput", row.getLedgerInputId())
                .addValue("run", row.getRunId()).addValue("matched", row.getMatchedCount()).addValue("differences", row.getDifferenceCount())
                .addValue("hash", row.getResultHash()).addValue("version", row.getRowVersion()).addValue("updated", LocalDateTime.now(clock));
        if (jdbc.update("""
                UPDATE reconciliation_batch SET state = :state, channel_input_id = COALESCE(:channelInput, channel_input_id),
                    ledger_input_id = COALESCE(:ledgerInput, ledger_input_id), run_id = COALESCE(:run, run_id),
                    matched_count = COALESCE(:matched, matched_count), difference_count = COALESCE(:differences, difference_count),
                    result_hash = COALESCE(:hash, result_hash), row_version = :version, updated_at = :updated
                WHERE tenant_id = :tenant AND id = :id AND row_version = :expected AND deleted = FALSE
                """, args) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private ReconciliationBatchDO row(String predicate, MapSqlParameterSource args, boolean lock) {
        if (lock && (jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())))
            throw new CommonException(INVALID_STATE);
        return jdbc.query("SELECT id, tenant_id, created_at, updated_at, caller_service_id, request_key, request_digest, channel_account_code, channel, account_scope_hash, bill_date, statement_profile, state, channel_input_id, ledger_input_id, run_id, matched_count, difference_count, result_hash, row_version FROM reconciliation_batch WHERE tenant_id = :tenant AND deleted = FALSE AND "
                        + predicate + (lock ? " FOR UPDATE" : ""), args, BeanPropertyRowMapper.newInstance(ReconciliationBatchDO.class))
                .stream().findFirst().orElse(null);
    }

    private Optional<StoredReconciliationBatch> optional(String tenant, ReconciliationBatchDO row) {
        if (row == null) return Optional.empty();
        try {
            if (row.getRowVersion() == null) throw new CommonException(SNAPSHOT_INVALID);
            var channel = row.getChannelInputId() == null ? null : inputs.sealedManifest(tenant, row.getChannelInputId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            var ledger = row.getLedgerInputId() == null ? null : inputs.sealedManifest(tenant, row.getLedgerInputId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            ReconciliationResultSummary summary = null;
            if (row.getMatchedCount() != null || row.getDifferenceCount() != null || row.getResultHash() != null) {
                if (row.getMatchedCount() == null || row.getDifferenceCount() == null || row.getResultHash() == null)
                    throw new CommonException(SNAPSHOT_INVALID);
                summary = converter.toSummary(row);
            }
            return Optional.of(converter.toStored(row, converter.toBatch(row, channel, ledger, summary)));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private void requireJdbc() {
        if (jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
    }
}
