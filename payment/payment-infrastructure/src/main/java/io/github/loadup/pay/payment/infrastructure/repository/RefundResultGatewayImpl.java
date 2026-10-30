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
package io.github.loadup.pay.payment.infrastructure.repository;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.model.RefundObservation;
import io.github.loadup.pay.payment.app.port.PaymentWriteConflictException;
import io.github.loadup.pay.payment.domain.gateway.RefundResultGateway;
import io.github.loadup.pay.payment.domain.model.RefundChannelReceipt;
import io.github.loadup.pay.payment.domain.model.RefundExecutionFacts;
import io.github.loadup.pay.payment.domain.model.RefundProgressChange;
import io.github.loadup.pay.payment.domain.model.RefundResultAnomaly;
import io.github.loadup.pay.payment.domain.model.RefundState;
import io.github.loadup.pay.payment.domain.model.RefundTransactionClaim;
import io.github.loadup.pay.payment.infrastructure.converter.RefundResultStorageConverter;
import io.github.loadup.pay.payment.infrastructure.converter.RefundStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundAllocationPlanDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundChannelReceiptDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundOrderDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundTransactionClaimDO;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundTransactionClaimMapper;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Supplier;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class RefundResultGatewayImpl extends ServiceImpl<RefundTransactionClaimMapper, RefundTransactionClaimDO>
        implements RefundResultGateway {
    private static final String CLAIM_COLUMNS = "id, created_at, updated_at, refund_id, payment_id, attempt_id, "
            + "channel, binding_revision_id, account_scope_hash, channel_request_no, original_channel_transaction_no, "
            + "channel_refund_no, amount_minor, currency, channel_refund_transaction_no, succeeded_at, receipt_id";
    private static final String CHILD_COLUMNS = "id, created_at, updated_at, refund_request_id, payment_id, original_attempt_id, "
            + "binding_revision_id, channel, account_scope_hash, original_channel_request_no, original_channel_transaction_no, "
            + "original_succeeded_at, submission_started_at, channel_refund_no, channel_refund_transaction_no, amount_minor, currency, "
            + "state, first_unknown_at, terminal_evidence_id, next_query_at, query_sequence, succeeded_at, row_version";
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock databaseClock;
    private final RefundResultStorageConverter converter;
    private final RefundStorageConverter storage;
    private final Clock clock;

    public RefundResultGatewayImpl(
            NamedParameterJdbcTemplate jdbc,
            RefundTransactionClaimMapper mapper,
            RefundResultStorageConverter converter,
            RefundStorageConverter storage,
            Clock clock,
            Clock databaseClock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.databaseClock = databaseClock;
        this.converter = converter;
        this.storage = storage;
        this.clock = clock;
    }

    @Override
    public Optional<String> requestId(String tenant, String refundId) {
        scoped(tenant);
        var row = child(tenant, refundId, false);
        return Optional.ofNullable(row).map(RefundOrderDO::getRefundRequestId);
    }

    @Override
    public RefundExecutionFacts lockChild(String tenant, String refundId) {
        writable(tenant);
        if (jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
        var row = child(tenant, refundId, true);
        if (row == null) {
            throw new CommonException(NOT_FOUND);
        }
        var args = new MapSqlParameterSource().addValue("refund", refundId);
        var plan = jdbc.query("""
                SELECT id, created_at, updated_at, refund_request_id, plan_version, sequence_no,
                       payment_id, refund_id, amount_minor FROM refund_allocation_plan
                WHERE refund_id = :refund AND deleted = FALSE
                """, args, BeanPropertyRowMapper.newInstance(RefundAllocationPlanDO.class)).stream().findFirst().orElse(null);
        return read(() -> {
            if (plan == null || !plan.getRefundRequestId().equals(row.getRefundRequestId()) || !plan.getPaymentId().equals(row.getPaymentId())
                    || !plan.getAmountMinor().equals(row.getAmountMinor()) || plan.getPlanVersion() != 1) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            var child = storage.toChild(row, storage.toRefund(row), storage.toIdentity(row), plan.getSequenceNo());
            return converter.toFacts(row, child);
        });
    }

    @Override
    public Optional<RefundChannelReceipt> receipt(String tenant, String refundId, RefundObservation.Source source, String key) {
        scoped(tenant);
        var args = new MapSqlParameterSource().addValue("refund", refundId).addValue("source", source.name()).addValue("key", key);
        var row = jdbc.query("""
                SELECT id, created_at, updated_at, refund_id, source, receipt_key, facts_digest,
                       source_digest, observation_payload, disposition, reason_code, received_at FROM refund_channel_receipt
                WHERE refund_id = :refund AND source = :source AND receipt_key = :key AND deleted = FALSE
                """, args, BeanPropertyRowMapper.newInstance(RefundChannelReceiptDO.class)).stream().findFirst().orElse(null);
        return row == null ? Optional.empty() : Optional.of(read(() -> converter.toReceipt(row)));
    }

    @Override
    public void insertReceipt(String tenant, RefundChannelReceipt receipt) {
        writable(tenant);
        if (jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
        var row = converter.toReceiptDO(tenant, receipt);
        var now = LocalDateTime.now(databaseClock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO refund_channel_receipt (id, created_at, updated_at, deleted, refund_id, source,
                    receipt_key, facts_digest, source_digest, observation_payload, disposition, reason_code, received_at)
                VALUES (:id, :createdAt, :updatedAt, FALSE, :refundId, :source, :receiptKey, :factsDigest,
                    :sourceDigest, :observationPayload, :disposition, :reasonCode, :receivedAt)
                ON CONFLICT (refund_id, source, receipt_key) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) {
            throw new PaymentWriteConflictException("Refund channel receipt already exists");
        }
        if (count != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    @Override
    public void recordAnomaly(String tenant, RefundResultAnomaly value) {
        writable(tenant);
        if (jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
        var row = converter.toAnomalyDO(tenant, value);
        var now = LocalDateTime.now(databaseClock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO refund_result_anomaly (id, created_at, updated_at, deleted, refund_id,
                    receipt_id, reason_code, facts_digest, observation_payload, received_at)
                VALUES (:id, :createdAt, :updatedAt, FALSE, :refundId, :receiptId, :reasonCode,
                    :factsDigest, :observationPayload, :receivedAt)
                ON CONFLICT (receipt_id, facts_digest, reason_code) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count != 0 && count != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    @Override
    public Optional<RefundTransactionClaim> transaction(Channel channel, String scope, String number) {
        writable(TenantUtil.getTenantId());
        // One exact immutable financial key must see aliases across tenants. Never expose this lookup as an API.
        var row = getOne(QueryWrapper.create()
                .eq("channel", channel.name())
                .eq("account_scope_hash", scope)
                .eq("channel_refund_transaction_no", number));
        return claim(row);
    }

    @Override
    public Optional<RefundTransactionClaim> claim(String tenant, String refundId) {
        scoped(tenant);
        var row = getOne(QueryWrapper.create().eq("refund_id", refundId));
        return claim(row);
    }

    private Optional<RefundTransactionClaim> claim(RefundTransactionClaimDO row) {
        return row == null ? Optional.empty()
                : Optional.of(read(() -> converter.toClaim(
                io.github.loadup.components.database.tenant.TenantContext.tenantId(),
                row,
                converter.toIdentity(row))));
    }

    @Override
    public void insertClaim(RefundTransactionClaim value) {
        writable(value.tenantId());
        if (jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
        var row = converter.toClaimDO(value);
        var now = LocalDateTime.now(databaseClock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO refund_transaction_claim (id, created_at, updated_at, deleted, refund_id, payment_id,
                    attempt_id, channel, binding_revision_id, account_scope_hash, channel_request_no, original_channel_transaction_no,
                    channel_refund_no, amount_minor, currency, channel_refund_transaction_no, succeeded_at, receipt_id)
                VALUES (:id, :createdAt, :updatedAt, FALSE, :refundId, :paymentId, :attemptId, :channel,
                    :bindingRevisionId, :accountScopeHash, :channelRequestNo, :originalChannelTransactionNo, :channelRefundNo,
                    :amountMinor, :currency, :channelRefundTransactionNo, :succeededAt, :receiptId)
                """, new BeanPropertySqlParameterSource(row));
        if (count != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    @Override
    public void update(String tenant, RefundProgressChange change) {
        writable(tenant);
        var before = change.before().child().refund();
        if (jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
        LocalDateTime firstUnknownAt = null;
        if (change.refund().state() == RefundState.UNKNOWN) {
            // Preserve the first observation across later UNKNOWN results and recovery generations.
            var row = child(tenant, before.id(), false);
            if (row == null || !row.getRowVersion().equals(before.version())) {
                throw new CommonException(CONCURRENT_UPDATE);
            }
            firstUnknownAt = row.getFirstUnknownAt() == null
                    ? LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS) : row.getFirstUnknownAt();
        }
        var patch = converter.toChildPatch(change, firstUnknownAt);
        var childArgs = new MapSqlParameterSource().addValue("id", before.id())
                .addValue("request", before.refundRequestId()).addValue("payment", before.paymentId())
                .addValue("oldState", before.state().name()).addValue("oldVersion", before.version())
                .addValue("oldStarted", change.before().submissionStartedAt()).addValue("state", patch.getState())
                .addValue("evidence", patch.getTerminalEvidenceId()).addValue("version", patch.getRowVersion())
                .addValue("started", patch.getSubmissionStartedAt()).addValue("transaction", patch.getChannelRefundTransactionNo())
                .addValue("succeeded", patch.getSucceededAt()).addValue("firstUnknown", patch.getFirstUnknownAt())
                .addValue("updated", LocalDateTime.now(databaseClock));
        if (jdbc.update("""
                UPDATE refund_order SET state = :state, terminal_evidence_id = COALESCE(:evidence, terminal_evidence_id),
                    row_version = :version, submission_started_at = COALESCE(:started, submission_started_at),
                    channel_refund_transaction_no = COALESCE(:transaction, channel_refund_transaction_no),
                    succeeded_at = COALESCE(:succeeded, succeeded_at), first_unknown_at = COALESCE(:firstUnknown, first_unknown_at),
                    updated_at = :updated
                WHERE id = :id AND refund_request_id = :request AND payment_id = :payment
                    AND state = :oldState AND row_version = :oldVersion
                    AND submission_started_at IS NOT DISTINCT FROM :oldStarted AND deleted = FALSE
                """, childArgs) != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
        var request = converter.toRequestPatch(change.request());
        var requestArgs = new MapSqlParameterSource().addValue("id", change.beforeRequest().id())
                .addValue("expected", change.beforeRequest().version()).addValue("priorState", change.beforeRequest().state().name())
                .addValue("state", request.getState()).addValue("version", request.getRowVersion())
                .addValue("succeeded", request.getSucceededMinor()).addValue("failed", request.getFailedMinor())
                .addValue("unknown", request.getUnknownCount()).addValue("updated", LocalDateTime.now(databaseClock));
        if (jdbc.update("""
                UPDATE refund_request SET state = :state, row_version = :version, succeeded_minor = :succeeded,
                    failed_minor = :failed, unknown_count = :unknown, updated_at = :updated
                WHERE id = :id AND row_version = :expected AND state = :priorState AND deleted = FALSE
                """, requestArgs) != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
        if (!change.capacity().equals(change.beforeCapacity())) {
            if (jdbc.getJdbcTemplate().getDataSource() == null
                    || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())) {
                throw new CommonException(INVALID_STATE);
            }
            var capacity = change.beforeCapacity();
            var next = change.capacity();
            var args = new MapSqlParameterSource().addValue("payment", capacity.paymentId()).addValue("order", capacity.orderId())
                    .addValue("payee", capacity.payeeMerchantId()).addValue("expectedVersion", capacity.version())
                    .addValue("expectedReserved", capacity.reservedMinor()).addValue("expectedRefunded", capacity.refundedMinor())
                    .addValue("reserved", next.reservedMinor()).addValue("refunded", next.refundedMinor())
                    .addValue("version", next.version()).addValue("updated", LocalDateTime.now(databaseClock));
            if (jdbc.update("""
                    UPDATE payment_refund_capacity SET reserved_minor = :reserved, refunded_minor = :refunded,
                        row_version = :version, updated_at = :updated WHERE payment_id = :payment
                        AND order_id = :order AND payee_merchant_id = :payee AND row_version = :expectedVersion
                        AND reserved_minor = :expectedReserved AND refunded_minor = :expectedRefunded AND deleted = FALSE
                    """, args) != 1) {
                throw new CommonException(CONCURRENT_UPDATE);
            }
        }
    }

    private RefundOrderDO child(String tenant, String id, boolean lock) {
        var args = new MapSqlParameterSource().addValue("id", id);
        return jdbc.query("SELECT " + CHILD_COLUMNS + " FROM refund_order WHERE "
                        + "id = :id AND deleted = FALSE" + (lock ? " FOR UPDATE" : ""), args,
                BeanPropertyRowMapper.newInstance(RefundOrderDO.class)).stream().findFirst().orElse(null);
    }

    private void scoped(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
    }

    private void writable(String tenant) {
        scoped(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new CommonException(INVALID_STATE);
        }
    }

    private <T> T read(Supplier<T> action) {
        try {
            return action.get();
        } catch (CommonException | org.springframework.dao.DataAccessException failure) {
            throw failure;
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
