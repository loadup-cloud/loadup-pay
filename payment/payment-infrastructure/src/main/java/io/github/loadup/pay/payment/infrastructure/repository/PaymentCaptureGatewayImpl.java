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
import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.payment.app.converter.PaymentCaptureConverter;
import io.github.loadup.pay.payment.app.port.PaymentWriteConflictException;
import io.github.loadup.pay.payment.client.support.PaymentCaptureContentHash;
import io.github.loadup.pay.payment.domain.gateway.PaymentCaptureGateway;
import io.github.loadup.pay.payment.domain.model.PaymentCapture;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureRead;
import io.github.loadup.pay.payment.domain.model.PaymentCaptureState;
import io.github.loadup.pay.payment.domain.model.PaymentLedgerLine;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentCaptureStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentCaptureDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentCaptureLineDO;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentCaptureMapper;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class PaymentCaptureGatewayImpl extends ServiceImpl<PaymentCaptureMapper, PaymentCaptureDO>
        implements PaymentCaptureGateway {
    private static final String CAPTURE_COLUMNS = "id, created_at, updated_at, caller_service_id, request_key, request_digest, "
            + "channel, account_scope_hash, bill_date, statement_profile, system_zone, state, started_at, finished_at, payment_count, "
            + "payment_amount_minor, refund_count, refund_amount_minor, content_hash, blocker, blocker_subject_id, row_version";
    private final NamedParameterJdbcTemplate jdbc;
    private final PaymentCaptureStorageConverter converter;
    private final PaymentCaptureConverter views;
    private final Clock clock;

    public PaymentCaptureGatewayImpl(
            NamedParameterJdbcTemplate jdbc,
            PaymentCaptureMapper mapper,
            PaymentCaptureStorageConverter converter,
            PaymentCaptureConverter views,
            Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.converter = converter;
        this.views = views;
        this.clock = clock;
    }

    @Override
    public Optional<PaymentCapture> findRequest(String tenant, String caller, String key) {
        context(tenant, false);
        DomainChecks.id(caller);
        DomainChecks.text(key, 128);
        return model(getOne(QueryWrapper.create()
                .eq("caller_service_id", caller)
                .eq("request_key", key)));
    }

    @Override
    public Optional<PaymentCapture> find(String tenant, String id) {
        context(tenant, false);
        return model(capture(tenant, id, false));
    }

    @Override
    public Optional<PaymentCapture> lock(String tenant, String id) {
        context(tenant, true);
        return model(capture(tenant, id, true));
    }

    @Override
    public void insert(String tenant, PaymentCapture value) {
        context(tenant, true);
        if (value.state() != PaymentCaptureState.INIT || value.version() != 0L) {
            throw new CommonException(INVALID_STATE);
        }
        var row = converter.toDO(tenant, value);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO payment_reconciliation_capture (id, created_at, updated_at, deleted, caller_service_id,
                    request_key, request_digest, channel, account_scope_hash, bill_date, statement_profile, system_zone,
                    state, started_at, finished_at, payment_count, payment_amount_minor, refund_count, refund_amount_minor,
                    content_hash, blocker, blocker_subject_id, row_version)
                VALUES (:id, :createdAt, :updatedAt, FALSE, :callerServiceId, :requestKey, :requestDigest,
                    :channel, :accountScopeHash, :billDate, :statementProfile, :systemZone, :state, :startedAt, :finishedAt,
                    :paymentCount, :paymentAmountMinor, :refundCount, :refundAmountMinor, :contentHash, :blocker, :blockerSubjectId, :rowVersion)
                ON CONFLICT (caller_service_id, request_key) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) {
            throw new PaymentWriteConflictException("Payment capture request key already exists");
        }
        if (count != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    @Override
    public void complete(String tenant, PaymentCapture original, PaymentCapture next, PaymentCaptureRead read) {
        context(tenant, true);
        var current = lock(tenant, original.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!current.equals(original) || original.state() != PaymentCaptureState.INIT || !original.complete(read, next.contentHash()).equals(next)) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        if (lineCount(tenant, original.id()) != 0) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        if (next.state() == PaymentCaptureState.SEALED) {
            if (!next.contentHash().equals(PaymentCaptureContentHash.compute(next.id(), next.scope().channel(), next.scope().accountScopeHash(), next.scope().billDate(),
                    next.scope().statementProfile(), next.scope().systemZone(), views.offset(next.startedAt(), next.scope().systemZone()),
                    views.offset(next.finishedAt(), next.scope().systemZone()), read.lines().stream().map(line -> views.toDTO(line, next.scope().systemZone())).toList()))) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            for (int start = 0; start < read.lines().size(); start += 256) {
                var batch = read.lines().subList(start, Math.min(start + 256, read.lines().size()));
                var now = LocalDateTime.now(clock);
                SqlParameterSource[] parameters = batch.stream().map(line -> {
                    var row = converter.toLineDO(tenant, original.id(), line);
                    row.setCreatedAt(now);
                    row.setUpdatedAt(now);
                    return new BeanPropertySqlParameterSource(row);
                }).toArray(SqlParameterSource[]::new);
                int[] counts = jdbc.batchUpdate("""
                        INSERT INTO payment_reconciliation_line (id, created_at, updated_at, deleted, capture_id,
                            ordinal, key_hash, kind, channel_transaction_no, channel_refund_no, merchant_payment_no,
                            amount_minor, currency, status, source_at, source_id, evidence_id, source_version)
                        VALUES (:id, :createdAt, :updatedAt, FALSE, :captureId, :ordinal, :keyHash, :kind,
                            :channelTransactionNo, :channelRefundNo, :merchantPaymentNo, :amountMinor, :currency, :status,
                            :sourceAt, :sourceId, :evidenceId, :sourceVersion)
                        """, parameters);
                if (counts.length != parameters.length) {
                    throw new CommonException(CONCURRENT_UPDATE);
                }
                for (int count : counts) {
                    if (count != 1 && count != java.sql.Statement.SUCCESS_NO_INFO) {
                        throw new CommonException(CONCURRENT_UPDATE);
                    }
                }
            }
        }
        var row = converter.toDO(tenant, next);
        var args = new MapSqlParameterSource().addValue("id", original.id()).addValue("state", row.getState())
                .addValue("started", row.getStartedAt()).addValue("finished", row.getFinishedAt())
                .addValue("paymentCount", row.getPaymentCount()).addValue("paymentAmount", row.getPaymentAmountMinor())
                .addValue("refundCount", row.getRefundCount()).addValue("refundAmount", row.getRefundAmountMinor())
                .addValue("hash", row.getContentHash()).addValue("blocker", row.getBlocker())
                .addValue("subject", row.getBlockerSubjectId()).addValue("version", row.getRowVersion()).addValue("updated", LocalDateTime.now(clock));
        if (jdbc.update("""
                UPDATE payment_reconciliation_capture SET state = :state, started_at = :started, finished_at = :finished,
                    payment_count = :paymentCount, payment_amount_minor = :paymentAmount, refund_count = :refundCount,
                    refund_amount_minor = :refundAmount, content_hash = :hash, blocker = :blocker, blocker_subject_id = :subject,
                    row_version = :version, updated_at = :updated
                WHERE id = :id AND state = 'INIT' AND row_version = 0 AND deleted = FALSE
                """, args) != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    @Override
    public PageResult<PaymentLedgerLine> page(String tenant, PaymentCapture capture, int page, int size) {
        context(tenant, false);
        if (!TransactionSynchronizationManager.isCurrentTransactionReadOnly() || !Integer.valueOf(TransactionDefinition.ISOLATION_REPEATABLE_READ)
                .equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()) || page < 1 || size < 1 || size > 100 || capture.state() != PaymentCaptureState.SEALED) {
            throw new CommonException(INVALID_STATE);
        }
        long expected = Math.addExact(capture.paymentCount(), capture.refundCount());
        if (lineCount(tenant, capture.id()) != expected) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        long offset = (long) (page - 1) * size;
        var args = new MapSqlParameterSource().addValue("capture", capture.id()).addValue("size", size).addValue("offset", offset);
        var rows = jdbc.query("""
                SELECT id, created_at, updated_at, capture_id, ordinal, key_hash, kind, channel_transaction_no,
                       channel_refund_no, merchant_payment_no, amount_minor, currency, status, source_at, source_id, evidence_id, source_version
                FROM payment_reconciliation_line WHERE capture_id = :capture AND deleted = FALSE
                ORDER BY ordinal ASC LIMIT :size OFFSET :offset
                """, args, BeanPropertyRowMapper.newInstance(PaymentCaptureLineDO.class));
        var result = new java.util.ArrayList<PaymentLedgerLine>();
        try {
            for (var row : rows) {
                if (row.getOrdinal() == null || row.getSourceVersion() == null || row.getAmountMinor() == null) {
                    throw new CommonException(SNAPSHOT_INVALID);
                }
                var line = converter.toLine(row);
                if (line.ordinal() != offset + result.size() + 1L || !capture.id().equals(row.getCaptureId())
                        || !PaymentCaptureContentHash.key(line.kind().name(), line.channelTransactionNo(), line.channelRefundNo()).equals(row.getKeyHash())
                        || line.sourceAt().isBefore(capture.scope().windowStart()) || !line.sourceAt().isBefore(capture.scope().windowEnd())) {
                    throw new CommonException(SNAPSHOT_INVALID);
                }
                result.add(line);
            }
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return PageResult.of(result, expected, page, size);
    }

    private Optional<PaymentCapture> model(PaymentCaptureDO row) {
        if (row == null) {
            return Optional.empty();
        }
        try {
            if (row.getRowVersion() == null) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            return Optional.of(converter.toModel(row));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private PaymentCaptureDO capture(String tenant, String id, boolean lock) {
        DomainChecks.id(id);
        if (!lock) {
            return getById(id);
        }
        var args = new MapSqlParameterSource().addValue("id", id);
        return jdbc.query("SELECT " + CAPTURE_COLUMNS + " FROM payment_reconciliation_capture "
                        + "WHERE id = :id AND deleted = FALSE FOR UPDATE", args,
                BeanPropertyRowMapper.newInstance(PaymentCaptureDO.class)).stream().findFirst().orElse(null);
    }

    private long lineCount(String tenant, String id) {
        DomainChecks.id(id);
        var args = new MapSqlParameterSource().addValue("capture", id);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM payment_reconciliation_line "
                + "WHERE capture_id = :capture AND deleted = FALSE", args, Long.class);
        if (count == null) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return count;
    }

    private void context(String tenant, boolean write) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive() || write && TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
    }
}
