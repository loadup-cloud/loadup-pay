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
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationWriteConflictException;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationImportGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationImportRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationImportState;
import io.github.loadup.pay.reconciliation.infrastructure.converter.ReconciliationImportStorageConverter;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationImportDO;
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
public class ReconciliationImportGatewayImpl implements ReconciliationImportGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final ReconciliationImportStorageConverter converter;

    public ReconciliationImportGatewayImpl(NamedParameterJdbcTemplate jdbc, ReconciliationImportStorageConverter converter, @Qualifier("reconciliationClock") Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.converter = converter;
    }

    @Override
    public Optional<ReconciliationImportRequest> findRequest(String tenant, String caller, String requestKey) {
        ReconciliationStoreContext.readable(tenant);
        DomainChecks.id(caller);
        DomainChecks.text(requestKey, 128);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("caller", caller).addValue("key", requestKey);
        return read(row("caller_service_id = :caller AND request_key = :key", args, false));
    }

    @Override
    public Optional<ReconciliationImportRequest> find(String tenant, String id) {
        ReconciliationStoreContext.readable(tenant);
        DomainChecks.id(id);
        return read(row("id = :id", new MapSqlParameterSource("tenant", tenant).addValue("id", id), false));
    }

    @Override
    public Optional<ReconciliationImportRequest> lock(String tenant, String id) {
        ReconciliationStoreContext.writable(tenant);
        DomainChecks.id(id);
        requireJdbc();
        return read(row("id = :id", new MapSqlParameterSource("tenant", tenant).addValue("id", id), true));
    }

    @Override
    public Optional<ReconciliationImportRequest> activeForBatch(String tenant, String batchId) {
        ReconciliationStoreContext.writable(tenant);
        DomainChecks.id(batchId);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("batch", batchId);
        var rows = jdbc.query("SELECT id, tenant_id, created_at, updated_at, batch_id, caller_service_id, request_key, request_digest, channel, account_scope_hash, bill_date, statement_profile, statement_merchant_id, statement_sub_merchant_id, file_id, storage_id, file_size, source_sha256, source_kind, task_id, state, artifact_id, rejection_code, row_version FROM reconciliation_import WHERE tenant_id = :tenant AND deleted = FALSE "
                + "AND batch_id = :batch AND state = 'INIT' LIMIT 2", args, BeanPropertyRowMapper.newInstance(ReconciliationImportDO.class));
        if (rows.size() > 1) throw new CommonException(SNAPSHOT_INVALID);
        return rows.isEmpty() ? Optional.empty() : read(rows.getFirst());
    }

    @Override
    public void insert(String tenant, ReconciliationImportRequest value) {
        ReconciliationStoreContext.writable(tenant);
        if (value == null || value.state() != ReconciliationImportState.INIT || value.version() != 0L)
            throw new CommonException(INVALID_STATE);
        requireJdbc();
        var row = converter.toDO(tenant, value);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO reconciliation_import (id, tenant_id, created_at, updated_at, deleted, batch_id, caller_service_id, request_key, request_digest, channel, account_scope_hash, bill_date, statement_profile, statement_merchant_id, statement_sub_merchant_id, file_id, storage_id, file_size, source_sha256, source_kind, task_id, state, artifact_id, rejection_code, row_version)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :batchId, :callerServiceId, :requestKey, :requestDigest, :channel, :accountScopeHash, :billDate, :statementProfile, :statementMerchantId, :statementSubMerchantId, :fileId, :storageId, :fileSize, :sourceSha256, :sourceKind, :taskId, :state, :artifactId, :rejectionCode, :rowVersion)
                ON CONFLICT (tenant_id, caller_service_id, request_key) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0)
            throw new ReconciliationWriteConflictException("reconciliation_import business key already exists");
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void update(String tenant, ReconciliationImportRequest value, long expectedVersion) {
        ReconciliationStoreContext.writable(tenant);
        if (value == null || expectedVersion < 0 || expectedVersion == Long.MAX_VALUE || value.version() != expectedVersion + 1L)
            throw new CommonException(INVALID_STATE);
        var original = lock(tenant, value.id()).orElseThrow(() -> new CommonException(NOT_FOUND));
        if (original.version() != expectedVersion) throw new CommonException(CONCURRENT_UPDATE);
        if (!converter.toInitial(original).equals(converter.toInitial(value)) || original.state() != ReconciliationImportState.INIT
                || value.state() == ReconciliationImportState.INIT) throw new CommonException(SNAPSHOT_INVALID);
        requireJdbc();
        var row = converter.toDO(tenant, value);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", value.id()).addValue("expected", expectedVersion)
                .addValue("updated", LocalDateTime.now(clock));
        args.addValue("state", row.getState());
        args.addValue("artifactId", row.getArtifactId());
        args.addValue("rejectionCode", row.getRejectionCode());
        args.addValue("rowVersion", row.getRowVersion());
        if (jdbc.update("""
                UPDATE reconciliation_import SET state = :state, artifact_id = :artifactId, rejection_code = :rejectionCode, row_version = :rowVersion, updated_at = :updated
                WHERE tenant_id = :tenant AND id = :id AND row_version = :expected AND state = 'INIT' AND deleted = FALSE
                """, args) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private void requireJdbc() {
        if (jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
    }

    private ReconciliationImportDO row(String predicate, MapSqlParameterSource args, boolean lock) {
        return jdbc.query("SELECT id, tenant_id, created_at, updated_at, batch_id, caller_service_id, request_key, request_digest, channel, account_scope_hash, bill_date, statement_profile, statement_merchant_id, statement_sub_merchant_id, file_id, storage_id, file_size, source_sha256, source_kind, task_id, state, artifact_id, rejection_code, row_version FROM reconciliation_import WHERE tenant_id = :tenant AND deleted = FALSE AND "
                        + predicate + (lock ? " FOR UPDATE" : ""), args, BeanPropertyRowMapper.newInstance(ReconciliationImportDO.class))
                .stream().findFirst().orElse(null);
    }

    private Optional<ReconciliationImportRequest> read(ReconciliationImportDO row) {
        if (row == null) return Optional.empty();
        try {
            if (row.getRowVersion() == null || row.getFileSize() == null) throw new CommonException(SNAPSHOT_INVALID);
            return Optional.of(converter.toModel(row));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

}
