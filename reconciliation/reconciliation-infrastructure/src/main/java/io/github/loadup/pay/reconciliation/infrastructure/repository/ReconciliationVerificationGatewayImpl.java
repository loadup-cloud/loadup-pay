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
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationVerificationGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationRequest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationState;
import io.github.loadup.pay.reconciliation.infrastructure.converter.ReconciliationVerificationStorageConverter;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationVerificationDO;
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
public class ReconciliationVerificationGatewayImpl implements ReconciliationVerificationGateway {
    private final Clock clock;
    private final NamedParameterJdbcTemplate jdbc;
    private final ReconciliationVerificationStorageConverter converter;

    public ReconciliationVerificationGatewayImpl(ReconciliationVerificationStorageConverter converter, NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.clock = clock;
        this.jdbc = jdbc;
        this.converter = converter;
    }

    @Override
    public Optional<ReconciliationVerificationRequest> findRequest(String tenant, String caller, String requestKey) {
        ReconciliationStoreContext.readable(tenant);
        DomainChecks.id(caller);
        DomainChecks.text(requestKey, 128);
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("caller", caller).addValue("key", requestKey);
        return read(row("caller_service_id = :caller AND request_key = :key", args, false));
    }

    @Override
    public Optional<ReconciliationVerificationRequest> find(String tenant, String id) {
        ReconciliationStoreContext.readable(tenant);
        DomainChecks.id(id);
        return read(row("id = :id", new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", id), false));
    }

    @Override
    public Optional<ReconciliationVerificationRequest> lock(String tenant, String id) {
        ReconciliationStoreContext.writable(tenant);
        DomainChecks.id(id);
        if (jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
        return read(row("id = :id", new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", id), true));
    }

    @Override
    public void insert(String tenant, ReconciliationVerificationRequest value) {
        ReconciliationStoreContext.writable(tenant);
        if (value == null || value.state() != ReconciliationVerificationState.INIT || value.version() != 0L) {
            throw new CommonException(INVALID_STATE);
        }
        requireJdbc();
        var row = converter.toDO(tenant, value);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO reconciliation_verification_request (id, tenant_id, created_at, updated_at, deleted, difference_id, batch_id, run_id, result_hash, match_key_hash, proposal_id, approval_event_id, review_version, caller_service_id, request_key, request_digest, channel, account_scope_hash, bill_date, statement_profile, kind, channel_transaction_no, channel_refund_no, merchant_payment_no, delivery_id, state, row_version, disposition, binding_revision_id, payment_id, attempt_id, refund_id, source_version, local_state, outcome, amount_minor, currency, observed_channel_transaction_no, observed_refund_transaction_no, succeeded_epoch_micros, source_digest, started_epoch_micros, finished_epoch_micros, accepted_epoch_micros)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :differenceId, :batchId, :runId, :resultHash, :matchKeyHash, :proposalId, :approvalEventId, :reviewVersion, :callerServiceId, :requestKey, :requestDigest, :channel, :accountScopeHash, :billDate, :statementProfile, :kind, :channelTransactionNo, :channelRefundNo, :merchantPaymentNo, :deliveryId, :state, :rowVersion, :disposition, :bindingRevisionId, :paymentId, :attemptId, :refundId, :sourceVersion, :localState, :outcome, :amountMinor, :currency, :observedChannelTransactionNo, :observedRefundTransactionNo, :succeededEpochMicros, :sourceDigest, :startedEpochMicros, :finishedEpochMicros, :acceptedEpochMicros)
                ON CONFLICT (tenant_id, caller_service_id, request_key) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) {
            throw new ReconciliationWriteConflictException("reconciliation_verification_request business key already exists");
        }
        if (count != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    @Override
    public void update(String tenant, ReconciliationVerificationRequest value, long expectedVersion) {
        ReconciliationStoreContext.writable(tenant);
        if (value == null || expectedVersion < 0 || expectedVersion == Long.MAX_VALUE || value.version() != expectedVersion + 1L) {
            throw new CommonException(INVALID_STATE);
        }
        var original = lock(tenant, value.id()).orElseThrow(() -> new CommonException(NOT_FOUND));
        if (original.version() != expectedVersion) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
        if (!converter.toInitial(original).equals(converter.toInitial(value)) || original.state() != ReconciliationVerificationState.INIT
                || value.state() == ReconciliationVerificationState.INIT) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        requireJdbc();
        var row = converter.toDO(tenant, value);
        row.setUpdatedAt(LocalDateTime.now(clock));
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", value.id()).addValue("expected", expectedVersion)
                .addValue("updated", row.getUpdatedAt());
        args.addValue("state", row.getState());
        args.addValue("rowVersion", row.getRowVersion());
        args.addValue("disposition", row.getDisposition());
        args.addValue("bindingRevisionId", row.getBindingRevisionId());
        args.addValue("paymentId", row.getPaymentId());
        args.addValue("attemptId", row.getAttemptId());
        args.addValue("refundId", row.getRefundId());
        args.addValue("sourceVersion", row.getSourceVersion());
        args.addValue("localState", row.getLocalState());
        args.addValue("outcome", row.getOutcome());
        args.addValue("amountMinor", row.getAmountMinor());
        args.addValue("currency", row.getCurrency());
        args.addValue("observedChannelTransactionNo", row.getObservedChannelTransactionNo());
        args.addValue("observedRefundTransactionNo", row.getObservedRefundTransactionNo());
        args.addValue("succeededEpochMicros", row.getSucceededEpochMicros());
        args.addValue("sourceDigest", row.getSourceDigest());
        args.addValue("startedEpochMicros", row.getStartedEpochMicros());
        args.addValue("finishedEpochMicros", row.getFinishedEpochMicros());
        args.addValue("acceptedEpochMicros", row.getAcceptedEpochMicros());
        if (jdbc.update("""
                UPDATE reconciliation_verification_request SET state = :state, row_version = :rowVersion, disposition = :disposition, binding_revision_id = :bindingRevisionId, payment_id = :paymentId, attempt_id = :attemptId, refund_id = :refundId, source_version = :sourceVersion, local_state = :localState, outcome = :outcome, amount_minor = :amountMinor, currency = :currency, observed_channel_transaction_no = :observedChannelTransactionNo, observed_refund_transaction_no = :observedRefundTransactionNo, succeeded_epoch_micros = :succeededEpochMicros, source_digest = :sourceDigest, started_epoch_micros = :startedEpochMicros, finished_epoch_micros = :finishedEpochMicros, accepted_epoch_micros = :acceptedEpochMicros, updated_at = :updated
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

    private ReconciliationVerificationDO row(String predicate, MapSqlParameterSource args, boolean lock) {
        return jdbc.query("SELECT id, tenant_id, created_at, updated_at, difference_id, batch_id, run_id, result_hash, match_key_hash, proposal_id, approval_event_id, review_version, caller_service_id, request_key, request_digest, channel, account_scope_hash, bill_date, statement_profile, kind, channel_transaction_no, channel_refund_no, merchant_payment_no, delivery_id, state, row_version, disposition, binding_revision_id, payment_id, attempt_id, refund_id, source_version, local_state, outcome, amount_minor, currency, observed_channel_transaction_no, observed_refund_transaction_no, succeeded_epoch_micros, source_digest, started_epoch_micros, finished_epoch_micros, accepted_epoch_micros FROM reconciliation_verification_request WHERE tenant_id = :tenant AND deleted = FALSE AND "
                        + predicate + (lock ? " FOR UPDATE" : ""), args, BeanPropertyRowMapper.newInstance(ReconciliationVerificationDO.class))
                .stream().findFirst().orElse(null);
    }

    private Optional<ReconciliationVerificationRequest> read(ReconciliationVerificationDO row) {
        if (row == null) {
            return Optional.empty();
        }
        try {
            if (row.getRowVersion() == null || row.getReviewVersion() == null) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            return Optional.of(converter.toModel(row));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

}
