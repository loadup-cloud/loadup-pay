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
import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationWriteConflictException;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationStatementGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementState;
import io.github.loadup.pay.reconciliation.infrastructure.converter.ReconciliationStatementStorageConverter;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationStatementDO;
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
public class ReconciliationStatementGatewayImpl implements ReconciliationStatementGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final ReconciliationStatementStorageConverter converter;

    public ReconciliationStatementGatewayImpl(NamedParameterJdbcTemplate jdbc, ReconciliationStatementStorageConverter converter, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.converter = converter;
    }

    @Override
    public Optional<ReconciliationStatementRequest> findRequest(String tenant, String caller, String requestKey) {
        ReconciliationStoreContext.readable(tenant);
        DomainChecks.id(caller);
        DomainChecks.text(requestKey, 128);
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("caller", caller).addValue("key", requestKey);
        return read(row("caller_service_id = :caller AND request_key = :key", args, false));
    }

    @Override
    public Optional<ReconciliationStatementRequest> find(String tenant, String id) {
        ReconciliationStoreContext.readable(tenant);
        DomainChecks.id(id);
        return read(row("id = :id", new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", id), false));
    }

    @Override
    public Optional<ReconciliationStatementRequest> lock(String tenant, String id) {
        ReconciliationStoreContext.writable(tenant);
        DomainChecks.id(id);
        requireJdbc();
        return read(row("id = :id", new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", id), true));
    }

    @Override
    public Optional<ReconciliationStatementRequest> activeForBatch(String tenant, String batchId) {
        ReconciliationStoreContext.writable(tenant);
        DomainChecks.id(batchId);
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("batch", batchId);
        var rows = jdbc.query("SELECT id, tenant_id, created_at, updated_at, batch_id, caller_service_id, request_key, request_digest, channel, account_scope_hash, bill_date, statement_profile, statement_merchant_id, statement_sub_merchant_id, binding_revision_id, max_bytes, task_id, state, row_version, format, request_sha256, official_hash_type, official_hash, received_epoch_micros, downloaded_epoch_micros, file_id, storage_id, file_size, source_sha256, proof_file_id, proof_storage_id, proof_file_size, proof_sha256 FROM reconciliation_statement_request WHERE tenant_id = :tenant AND deleted = FALSE "
                + "AND batch_id = :batch AND state = 'INIT' LIMIT 2", args, BeanPropertyRowMapper.newInstance(ReconciliationStatementDO.class));
        if (rows.size() > 1) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return rows.isEmpty() ? Optional.empty() : read(rows.getFirst());
    }

    @Override
    public void insert(String tenant, ReconciliationStatementRequest value) {
        ReconciliationStoreContext.writable(tenant);
        if (value == null || value.state() != ReconciliationStatementState.INIT || value.version() != 0L) {
            throw new CommonException(INVALID_STATE);
        }
        requireJdbc();
        var row = converter.toDO(tenant, value);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO reconciliation_statement_request (id, tenant_id, created_at, updated_at, deleted, batch_id, caller_service_id, request_key, request_digest, channel, account_scope_hash, bill_date, statement_profile, statement_merchant_id, statement_sub_merchant_id, binding_revision_id, max_bytes, task_id, state, row_version, format, request_sha256, official_hash_type, official_hash, received_epoch_micros, downloaded_epoch_micros, file_id, storage_id, file_size, source_sha256, proof_file_id, proof_storage_id, proof_file_size, proof_sha256)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :batchId, :callerServiceId, :requestKey, :requestDigest, :channel, :accountScopeHash, :billDate, :statementProfile, :statementMerchantId, :statementSubMerchantId, :bindingRevisionId, :maxBytes, :taskId, :state, :rowVersion, :format, :requestSha256, :officialHashType, :officialHash, :receivedEpochMicros, :downloadedEpochMicros, :fileId, :storageId, :fileSize, :sourceSha256, :proofFileId, :proofStorageId, :proofFileSize, :proofSha256)
                ON CONFLICT (tenant_id, caller_service_id, request_key) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) {
            throw new ReconciliationWriteConflictException("reconciliation_statement_request business key already exists");
        }
        if (count != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    @Override
    public void update(String tenant, ReconciliationStatementRequest value, long expectedVersion) {
        ReconciliationStoreContext.writable(tenant);
        if (value == null || expectedVersion < 0 || expectedVersion == Long.MAX_VALUE || value.version() != expectedVersion + 1L) {
            throw new CommonException(INVALID_STATE);
        }
        var original = lock(tenant, value.id()).orElseThrow(() -> new CommonException(NOT_FOUND));
        if (original.version() != expectedVersion) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
        if (!converter.toInitial(original).equals(converter.toInitial(value)) || original.state() != ReconciliationStatementState.INIT
                || value.state() == ReconciliationStatementState.INIT) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        requireJdbc();
        var row = converter.toDO(tenant, value);
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", value.id()).addValue("expected", expectedVersion)
                .addValue("updated", LocalDateTime.now(clock));
        args.addValue("state", row.getState());
        args.addValue("rowVersion", row.getRowVersion());
        args.addValue("format", row.getFormat());
        args.addValue("requestSha256", row.getRequestSha256());
        args.addValue("officialHashType", row.getOfficialHashType());
        args.addValue("officialHash", row.getOfficialHash());
        args.addValue("receivedEpochMicros", row.getReceivedEpochMicros());
        args.addValue("downloadedEpochMicros", row.getDownloadedEpochMicros());
        args.addValue("fileId", row.getFileId());
        args.addValue("storageId", row.getStorageId());
        args.addValue("fileSize", row.getFileSize());
        args.addValue("sourceSha256", row.getSourceSha256());
        args.addValue("proofFileId", row.getProofFileId());
        args.addValue("proofStorageId", row.getProofStorageId());
        args.addValue("proofFileSize", row.getProofFileSize());
        args.addValue("proofSha256", row.getProofSha256());
        if (jdbc.update("""
                UPDATE reconciliation_statement_request SET state = :state, row_version = :rowVersion, format = :format, request_sha256 = :requestSha256, official_hash_type = :officialHashType, official_hash = :officialHash, received_epoch_micros = :receivedEpochMicros, downloaded_epoch_micros = :downloadedEpochMicros, file_id = :fileId, storage_id = :storageId, file_size = :fileSize, source_sha256 = :sourceSha256, proof_file_id = :proofFileId, proof_storage_id = :proofStorageId, proof_file_size = :proofFileSize, proof_sha256 = :proofSha256, updated_at = :updated
                WHERE tenant_id = :tenant AND id = :id AND row_version = :expected AND state = 'INIT' AND deleted = FALSE
                """, args) != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    private void requireJdbc() {
        if (jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
    }

    private ReconciliationStatementDO row(String predicate, MapSqlParameterSource args, boolean lock) {
        return jdbc.query("SELECT id, tenant_id, created_at, updated_at, batch_id, caller_service_id, request_key, request_digest, channel, account_scope_hash, bill_date, statement_profile, statement_merchant_id, statement_sub_merchant_id, binding_revision_id, max_bytes, task_id, state, row_version, format, request_sha256, official_hash_type, official_hash, received_epoch_micros, downloaded_epoch_micros, file_id, storage_id, file_size, source_sha256, proof_file_id, proof_storage_id, proof_file_size, proof_sha256 FROM reconciliation_statement_request WHERE tenant_id = :tenant AND deleted = FALSE AND "
                        + predicate + (lock ? " FOR UPDATE" : ""), args, BeanPropertyRowMapper.newInstance(ReconciliationStatementDO.class))
                .stream().findFirst().orElse(null);
    }

    private Optional<ReconciliationStatementRequest> read(ReconciliationStatementDO row) {
        if (row == null) {
            return Optional.empty();
        }
        try {
            if (row.getRowVersion() == null || row.getMaxBytes() == null) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            return Optional.of(converter.toModel(row));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

}
