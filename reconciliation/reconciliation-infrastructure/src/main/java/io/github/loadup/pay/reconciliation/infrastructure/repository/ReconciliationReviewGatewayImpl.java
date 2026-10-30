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
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationWriteConflictException;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationReviewGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReview;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewEvent;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewOperation;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewProposal;
import io.github.loadup.pay.reconciliation.infrastructure.converter.ReconciliationReviewStorageConverter;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationReviewDO;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationReviewEventDO;
import io.github.loadup.pay.reconciliation.infrastructure.dataobject.ReconciliationReviewProposalDO;
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
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class ReconciliationReviewGatewayImpl implements ReconciliationReviewGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final ReconciliationReviewStorageConverter converter;

    public ReconciliationReviewGatewayImpl(NamedParameterJdbcTemplate jdbc,
                                           ReconciliationReviewStorageConverter converter, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.converter = converter;
    }

    @Override
    public Optional<ReconciliationReview> find(String tenant, String id) {
        ReconciliationStoreContext.readable(tenant);
        return headOrInitial(tenant, id, headRow(tenant, id, false));
    }

    @Override
    public Optional<ReconciliationReview> lock(String tenant, String id) {
        ReconciliationStoreContext.writable(tenant);
        return headOrInitial(tenant, id, headRow(tenant, id, true));
    }

    @Override
    public Optional<ReconciliationReviewEvent> findRequest(String tenant, String caller, String requestKey) {
        ReconciliationStoreContext.readable(tenant);
        DomainChecks.id(caller);
        DomainChecks.text(requestKey, 128);
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("caller", caller).addValue("key", requestKey);
        return event(eventRow("caller_service_id = :caller AND request_key = :key", args));
    }

    @Override
    public Optional<ReconciliationReviewEvent> event(String tenant, String id) {
        ReconciliationStoreContext.readable(tenant);
        DomainChecks.id(id);
        return event(eventRow("id = :id", new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", id)));
    }

    @Override
    public Optional<ReconciliationReviewProposal> proposal(String tenant, String id) {
        ReconciliationStoreContext.readable(tenant);
        DomainChecks.id(id);
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", id);
        var row = jdbc.query("SELECT id, tenant_id, created_at, updated_at, difference_id, batch_id, run_id, result_hash, match_key_hash, proposer_service_id, conclusion, note, evidence_file_id, evidence_storage_id, evidence_size, evidence_sha256, proposed_at FROM reconciliation_review_proposal WHERE tenant_id = :tenant AND id = :id AND deleted = FALSE",
                args, BeanPropertyRowMapper.newInstance(ReconciliationReviewProposalDO.class)).stream().findFirst().orElse(null);
        if (row == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(converter.proposal(row));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public PageResult<ReconciliationReviewEvent> history(String tenant, String differenceId, int page, int size) {
        ReconciliationStoreContext.readable(tenant);
        DomainChecks.id(differenceId);
        if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly() || page < 1 || size < 1 || size > 100) {
            throw new CommonException(INVALID_STATE);
        }
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("difference", differenceId).addValue("limit", size)
                .addValue("offset", (long) (page - 1) * size);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM reconciliation_review_event WHERE tenant_id = :tenant AND difference_id = :difference AND deleted = FALSE", args, Long.class);
        var rows = jdbc.query("SELECT id, tenant_id, created_at, updated_at, difference_id, batch_id, run_id, result_hash, match_key_hash, caller_service_id, request_key, request_digest, operation, proposal_id, state, source_version, note, occurred_at FROM reconciliation_review_event WHERE tenant_id = :tenant AND difference_id = :difference AND deleted = FALSE "
                + "ORDER BY source_version ASC LIMIT :limit OFFSET :offset", args, BeanPropertyRowMapper.newInstance(ReconciliationReviewEventDO.class));
        return PageResult.of(rows.stream().map(row -> event(row).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID))).toList(), total, page, size);
    }

    @Override
    public void commit(String tenant, ReconciliationReview previous, ReconciliationReview next,
                       ReconciliationReviewProposal proposed, ReconciliationReviewEvent event) {
        ReconciliationStoreContext.writable(tenant);
        if (previous == null || next == null || event == null || !previous.reference().equals(next.reference()) || next.version() != Math.incrementExact(previous.version())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        final ReconciliationReview expected;
        if (proposed != null) {
            if (event.operation() != ReconciliationReviewOperation.PROPOSE || !proposed.proposerServiceId().equals(event.callerServiceId())
                    || !proposed.proposedAt().equals(event.occurredAt())) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            expected = previous.propose(proposed, event.id(), previous.version());
        } else {
            if (event.operation() == ReconciliationReviewOperation.PROPOSE) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            var proposal = proposal(tenant, previous.proposalId()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
            expected = previous.decide(proposal, event.callerServiceId(), event.id(), event.operation() == ReconciliationReviewOperation.APPROVE, previous.version());
        }
        if (!expected.equals(next)) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        event.verifyHead(next);
        var current = lock(tenant, previous.reference().differenceId());
        if (previous.version() == 0L ? current.isPresent() : current.isEmpty() || !current.get().equals(previous)) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
        if (proposed != null) {
            if (jdbc.getJdbcTemplate().getDataSource() == null
                    || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())) {
                throw new CommonException(INVALID_STATE);
            }
            var row = converter.toDO(tenant, proposed);
            var now = LocalDateTime.now(clock);
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            int count = jdbc.update("""
                    INSERT INTO reconciliation_review_proposal (id, tenant_id, created_at, updated_at, deleted, difference_id, batch_id,
                        run_id, result_hash, match_key_hash, proposer_service_id, conclusion, note, evidence_file_id, evidence_storage_id,
                        evidence_size, evidence_sha256, proposed_at)
                    VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :differenceId, :batchId, :runId, :resultHash, :matchKeyHash,
                        :proposerServiceId, :conclusion, :note, :evidenceFileId, :evidenceStorageId, :evidenceSize, :evidenceSha256, :proposedAt)
                    """, new BeanPropertySqlParameterSource(row));
            if (count != 1) {
                throw new CommonException(CONCURRENT_UPDATE);
            }
        }
        if (jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
        var eventRow = converter.toDO(tenant, event);
        var now = LocalDateTime.now(clock);
        eventRow.setCreatedAt(now);
        eventRow.setUpdatedAt(now);
        int eventCount = jdbc.update("""
                INSERT INTO reconciliation_review_event (id, tenant_id, created_at, updated_at, deleted, difference_id, batch_id, run_id,
                    result_hash, match_key_hash, caller_service_id, request_key, request_digest, operation, proposal_id, state, source_version, note, occurred_at)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :differenceId, :batchId, :runId, :resultHash, :matchKeyHash,
                    :callerServiceId, :requestKey, :requestDigest, :operation, :proposalId, :state, :sourceVersion, :note, :occurredAt)
                ON CONFLICT (tenant_id, caller_service_id, request_key) DO NOTHING
                """, new BeanPropertySqlParameterSource(eventRow));
        if (eventCount == 0) {
            throw new ReconciliationWriteConflictException("Review request already exists");
        }
        if (eventCount != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
        var row = converter.toDO(tenant, next);
        if (previous.version() == 0L) {
            row.setCreatedAt(now);
            row.setUpdatedAt(now);
            int headCount = jdbc.update("""
                    INSERT INTO reconciliation_review (id, tenant_id, created_at, updated_at, deleted, batch_id, run_id,
                        result_hash, match_key_hash, state, proposal_id, last_event_id, row_version)
                    VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :batchId, :runId, :resultHash, :matchKeyHash,
                        :state, :proposalId, :lastEventId, :rowVersion)
                    ON CONFLICT (id) DO NOTHING
                    """, new BeanPropertySqlParameterSource(row));
            if (headCount == 0) {
                throw new ReconciliationWriteConflictException("Review head already exists");
            }
            if (headCount != 1) {
                throw new CommonException(CONCURRENT_UPDATE);
            }
        } else {
            var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", previous.reference().differenceId())
                    .addValue("expected", previous.version()).addValue("state", row.getState()).addValue("proposal", row.getProposalId())
                    .addValue("event", row.getLastEventId()).addValue("version", row.getRowVersion()).addValue("updated", now);
            if (jdbc.update("""
                    UPDATE reconciliation_review SET state = :state, proposal_id = :proposal, last_event_id = :event,
                        row_version = :version, updated_at = :updated
                    WHERE tenant_id = :tenant AND id = :id AND row_version = :expected AND deleted = FALSE
                    """, args) != 1) {
                throw new CommonException(CONCURRENT_UPDATE);
            }
        }
    }

    private Optional<ReconciliationReview> headOrInitial(String tenant, String id, ReconciliationReviewDO row) {
        if (row == null && jdbc.queryForObject("SELECT COUNT(*) FROM reconciliation_review_event WHERE tenant_id = :tenant AND difference_id = :id AND deleted = FALSE",
                new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", id), Long.class) != 0) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return head(row);
    }

    private Optional<ReconciliationReview> head(ReconciliationReviewDO row) {
        if (row == null) {
            return Optional.empty();
        }
        try {
            if (row.getRowVersion() == null || row.getRowVersion() < 1) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            return Optional.of(converter.head(row));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private Optional<ReconciliationReviewEvent> event(ReconciliationReviewEventDO row) {
        if (row == null) {
            return Optional.empty();
        }
        try {
            if (row.getSourceVersion() == null) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            return Optional.of(converter.event(row));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private ReconciliationReviewDO headRow(String tenant, String id, boolean lock) {
        DomainChecks.id(id);
        if (lock && (jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))) {
            throw new CommonException(INVALID_STATE);
        }
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", id);
        return jdbc.query("SELECT id, tenant_id, created_at, updated_at, batch_id, run_id, result_hash, match_key_hash, state, proposal_id, last_event_id, row_version FROM reconciliation_review WHERE tenant_id = :tenant AND id = :id AND deleted = FALSE"
                        + (lock ? " FOR UPDATE" : ""), args, BeanPropertyRowMapper.newInstance(ReconciliationReviewDO.class))
                .stream().findFirst().orElse(null);
    }

    private ReconciliationReviewEventDO eventRow(String predicate, MapSqlParameterSource args) {
        return jdbc.query("SELECT id, tenant_id, created_at, updated_at, difference_id, batch_id, run_id, result_hash, match_key_hash, caller_service_id, request_key, request_digest, operation, proposal_id, state, source_version, note, occurred_at FROM reconciliation_review_event WHERE tenant_id = :tenant AND deleted = FALSE AND " + predicate,
                args, BeanPropertyRowMapper.newInstance(ReconciliationReviewEventDO.class)).stream().findFirst().orElse(null);
    }
}
