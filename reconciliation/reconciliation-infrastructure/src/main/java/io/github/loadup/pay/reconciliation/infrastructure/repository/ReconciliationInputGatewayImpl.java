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
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationWriteConflictException;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationInputGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationDataset;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationManifest;
import io.github.loadup.pay.reconciliation.infrastructure.converter.ReconciliationStorageConverter;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationEntryDO;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationInputDO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INPUT_INVALID;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INPUT_NOT_SEALED;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class ReconciliationInputGatewayImpl implements ReconciliationInputGateway {
    private final ReconciliationStorageConverter converter;
    private final int maxRecords;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final IdGenerator ids;

    public ReconciliationInputGatewayImpl(ReconciliationStorageConverter converter, @Value("${loadup.modules.reconciliation.max-records:10000}") int maxRecords,
                                          NamedParameterJdbcTemplate jdbc, @Qualifier("reconciliationClock") Clock clock, IdGenerator ids) {
        if (maxRecords < 1 || maxRecords > 1000000) throw new CommonException(INVALID_ARGUMENT);
        this.converter = converter;
        this.maxRecords = maxRecords;
        this.jdbc = jdbc;
        this.clock = clock;
        this.ids = ids;
    }

    @Override
    public Optional<ReconciliationManifest> sealedManifest(String tenant, String artifactId) {
        ReconciliationStoreContext.readable(tenant);
        var row = input(tenant, artifactId, false);
        return row == null || !"SEALED".equals(row.getState()) ? Optional.empty() : Optional.of(manifest(row));
    }

    @Override
    public ReconciliationDataset loadSealed(String tenant, String artifactId) {
        ReconciliationStoreContext.readable(tenant);
        var row = input(tenant, artifactId, false);
        if (row == null || !"SEALED".equals(row.getState())) throw new CommonException(INPUT_NOT_SEALED);
        var manifest = manifest(row);
        if (manifest.totals().recordCount() > maxRecords) throw new CommonException(INPUT_INVALID);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("input", artifactId).addValue("limit", maxRecords + 1);
        var rows = jdbc.query("SELECT * FROM reconciliation_entry WHERE tenant_id = :tenant AND input_id = :input "
                + "AND deleted = FALSE ORDER BY record_number ASC LIMIT :limit", args, BeanPropertyRowMapper.newInstance(ReconciliationEntryDO.class));
        try {
            var lines = rows.stream().map(entry -> {
                var line = converter.toLine(entry);
                if (!line.key().fingerprint().equals(entry.getMatchKeyHash()))
                    throw new CommonException(SNAPSHOT_INVALID);
                return line;
            }).toList();
            return new ReconciliationDataset(manifest, lines);
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insertSealed(String tenant, String fileId, ReconciliationDataset dataset) {
        ReconciliationStoreContext.writable(tenant);
        io.github.loadup.commons.validation.DomainChecks.id(fileId);
        if (dataset == null || dataset.records().size() > maxRecords) throw new CommonException(INPUT_INVALID);
        var original = input(tenant, dataset.manifest().artifactId(), true);
        if (original != null) {
            if (!fileId.equals(original.getFileId()) || !dataset.equals(loadSealed(tenant, dataset.manifest().artifactId())))
                throw new CommonException(IDEMPOTENCY_CONFLICT);
            return;
        }
        var input = converter.toInput(tenant, fileId, dataset.manifest());
        var inputNow = LocalDateTime.now(clock);
        input.setCreatedAt(inputNow);
        input.setUpdatedAt(inputNow);
        int inputCount = jdbc.update("""
                INSERT INTO reconciliation_input (id, tenant_id, created_at, updated_at, deleted, side, channel, account_scope_hash,
                    bill_date, statement_profile, file_id, artifact_sha256, normalized_sha256, source_capture_id, parser_revision,
                    state, payment_count, payment_amount_minor, refund_count, refund_amount_minor, rejection_code, row_version)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :side, :channel, :accountScopeHash, :billDate,
                    :statementProfile, :fileId, :artifactSha256, :normalizedSha256, :sourceCaptureId, :parserRevision,
                    :state, :paymentCount, :paymentAmountMinor, :refundCount, :refundAmountMinor, :rejectionCode, :rowVersion)
                ON CONFLICT (id) DO NOTHING
                """, new BeanPropertySqlParameterSource(input));
        if (inputCount == 0)
            throw new ReconciliationWriteConflictException("reconciliation_input business key already exists");
        if (inputCount != 1) throw new CommonException(CONCURRENT_UPDATE);
        if (jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE);
        var now = LocalDateTime.now(clock);
        var records = dataset.records();
        for (int offset = 0; offset < records.size(); offset += 500) {
            var rows = records.subList(offset, Math.min(offset + 500, records.size())).stream().map(line -> {
                var entry = converter.toEntry(tenant, dataset.manifest().artifactId(), line);
                entry.setId(ids.generate());
                entry.setCreatedAt(now);
                entry.setUpdatedAt(now);
                return new BeanPropertySqlParameterSource(entry);
            }).toArray(BeanPropertySqlParameterSource[]::new);
            int[] counts = jdbc.batchUpdate("""
                    INSERT INTO reconciliation_entry (id, tenant_id, created_at, updated_at, deleted, input_id, record_number,
                        match_key_hash, kind, channel_transaction_no, channel_refund_no, merchant_payment_no, amount_minor, currency, state)
                    VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :inputId, :recordNumber, :matchKeyHash, :kind,
                        :channelTransactionNo, :channelRefundNo, :merchantPaymentNo, :amountMinor, :currency, :state)
                    """, rows);
            if (counts.length != rows.length) throw new CommonException(CONCURRENT_UPDATE);
            for (int count : counts)
                if (count != 1 && count != Statement.SUCCESS_NO_INFO) throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    private ReconciliationManifest manifest(ReconciliationInputDO row) {
        try {
            if (row.getPaymentCount() == null || row.getPaymentAmountMinor() == null || row.getRefundCount() == null
                    || row.getRefundAmountMinor() == null || row.getRejectionCode() != null)
                throw new CommonException(SNAPSHOT_INVALID);
            return converter.toManifest(row);
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private ReconciliationInputDO input(String tenant, String id, boolean lock) {
        io.github.loadup.commons.validation.DomainChecks.id(id);
        if (lock && (jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())))
            throw new CommonException(io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", id);
        return jdbc.query("SELECT * FROM reconciliation_input WHERE tenant_id = :tenant AND id = :id AND deleted = FALSE"
                        + (lock ? " FOR UPDATE" : ""), args, BeanPropertyRowMapper.newInstance(ReconciliationInputDO.class))
                .stream().findFirst().orElse(null);
    }
}
