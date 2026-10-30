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

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.model.PaymentObservation;
import io.github.loadup.pay.payment.app.port.PaymentWriteConflictException;
import io.github.loadup.pay.payment.domain.gateway.PaymentResultGateway;
import io.github.loadup.pay.payment.domain.model.ChannelTransactionClaim;
import io.github.loadup.pay.payment.domain.model.PaymentChannelReceipt;
import io.github.loadup.pay.payment.domain.model.PaymentRefundCapacity;
import io.github.loadup.pay.payment.domain.model.PaymentResultAnomaly;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentResultStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.ChannelTransactionClaimDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentChannelReceiptDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentRefundCapacityDO;
import io.github.loadup.components.database.tenant.TenantContext;
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
import java.util.function.Supplier;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class PaymentResultGatewayImpl implements PaymentResultGateway {
    private static final String CLAIM_COLUMNS = "id, tenant_id, created_at, updated_at, payment_id, attempt_id, channel, "
            + "binding_revision_id, account_scope_hash, channel_request_no, amount_minor, currency, channel_transaction_no, succeeded_at, receipt_id";
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final PaymentResultStorageConverter converter;

    public PaymentResultGatewayImpl(NamedParameterJdbcTemplate jdbc,
                                    PaymentResultStorageConverter converter, @Qualifier("databaseClock") Clock clock) {
        this.converter = converter;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<PaymentChannelReceipt> receipt(String tenant, String attempt, PaymentObservation.Source source, String key) {
        scope(tenant);
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("attempt", attempt).addValue("source", source.name()).addValue("key", key);
        var row = jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, payment_id, attempt_id, channel, binding_revision_id,
                       account_scope_hash, channel_request_no, source, receipt_key, facts_digest, source_digest,
                       observation_payload, disposition, reason_code, received_at
                FROM payment_channel_receipt WHERE tenant_id = :tenant AND attempt_id = :attempt AND source = :source
                    AND receipt_key = :key AND deleted = FALSE
                """, args, BeanPropertyRowMapper.newInstance(PaymentChannelReceiptDO.class)).stream().findFirst().orElse(null);
        return row == null ? Optional.empty() : Optional.of(read(() -> converter.toReceipt(row, converter.toIdentity(row))));
    }

    @Override
    public void insertReceipt(String tenant, PaymentChannelReceipt receipt) {
        writable(tenant);
        jdbcTransaction();
        var row = converter.toReceiptDO(tenant, receipt);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO payment_channel_receipt (id, tenant_id, created_at, updated_at, deleted, payment_id, attempt_id,
                    channel, binding_revision_id, account_scope_hash, channel_request_no, source, receipt_key, facts_digest,
                    source_digest, observation_payload, disposition, reason_code, received_at)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :paymentId, :attemptId, :channel, :bindingRevisionId,
                    :accountScopeHash, :channelRequestNo, :source, :receiptKey, :factsDigest, :sourceDigest, :observationPayload,
                    :disposition, :reasonCode, :receivedAt)
                ON CONFLICT (tenant_id, attempt_id, source, receipt_key) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) throw new PaymentWriteConflictException("Payment channel receipt already exists");
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void recordAnomaly(String tenant, PaymentResultAnomaly anomaly) {
        writable(tenant);
        jdbcTransaction();
        var row = converter.toAnomalyDO(tenant, anomaly);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO payment_result_anomaly (id, tenant_id, created_at, updated_at, deleted, payment_id,
                    attempt_id, receipt_id, reason_code, facts_digest, observation_payload, received_at)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :paymentId, :attemptId, :receiptId,
                    :reasonCode, :factsDigest, :observationPayload, :receivedAt)
                ON CONFLICT (tenant_id, receipt_id, facts_digest, reason_code) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count != 0 && count != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public Optional<ChannelTransactionClaim> transaction(Channel channel, String scope, String number) {
        // This one exact-key immutable lookup must see financial aliases in other tenants.
        // No collection/list query or outward-facing API bypasses tenant isolation.
        var args = new MapSqlParameterSource("channel", channel.name()).addValue("scope", scope).addValue("number", number);
        var row = jdbc.query("SELECT " + CLAIM_COLUMNS + " FROM channel_transaction_claim WHERE channel = :channel "
                        + "AND account_scope_hash = :scope AND channel_transaction_no = :number AND deleted = FALSE", args,
                BeanPropertyRowMapper.newInstance(ChannelTransactionClaimDO.class)).stream().findFirst().orElse(null);
        return claim(row);
    }

    @Override
    public Optional<ChannelTransactionClaim> attemptClaim(String tenant, String attempt) {
        scope(tenant);
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("attempt", attempt);
        var row = jdbc.query("SELECT " + CLAIM_COLUMNS + " FROM channel_transaction_claim WHERE tenant_id = :tenant "
                        + "AND attempt_id = :attempt AND deleted = FALSE", args,
                BeanPropertyRowMapper.newInstance(ChannelTransactionClaimDO.class)).stream().findFirst().orElse(null);
        return claim(row);
    }

    private Optional<ChannelTransactionClaim> claim(ChannelTransactionClaimDO row) {
        return row == null ? Optional.empty() : Optional.of(read(() -> converter.toClaim(row, converter.toIdentity(row))));
    }

    @Override
    public void insertClaim(ChannelTransactionClaim claim) {
        writable(claim.tenantId());
        jdbcTransaction();
        var row = converter.toClaimDO(claim);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO channel_transaction_claim (id, tenant_id, created_at, updated_at, deleted, payment_id,
                    attempt_id, channel, binding_revision_id, account_scope_hash, channel_request_no, amount_minor,
                    currency, channel_transaction_no, succeeded_at, receipt_id)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :paymentId, :attemptId, :channel, :bindingRevisionId,
                    :accountScopeHash, :channelRequestNo, :amountMinor, :currency, :channelTransactionNo, :succeededAt, :receiptId)
                """, new BeanPropertySqlParameterSource(row));
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void completeSuccess(String tenant, StoredPaymentAcceptance payment, long expectedVersion, java.time.LocalDateTime succeededAt) {
        writable(tenant);
        if (payment.payment().state() != PaymentState.SUCCEEDED || payment.terminalEvidenceId() == null || succeededAt == null
                || payment.payment().version() != Math.incrementExact(expectedVersion))
            throw new CommonException(INVALID_STATE);
        jdbcTransaction();
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", payment.payment().id())
                .addValue("attempt", payment.payment().successfulAttemptId()).addValue("version", payment.payment().version())
                .addValue("expected", expectedVersion).addValue("evidence", payment.terminalEvidenceId())
                .addValue("succeeded", succeededAt).addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE payment_order SET state = 'SUCCEEDED', successful_attempt_id = :attempt,
                    terminal_evidence_id = :evidence, succeeded_at = :succeeded, row_version = :version, updated_at = :updated
                WHERE tenant_id = :tenant AND id = :id AND active_attempt_id = :attempt
                    AND state IN ('PROCESSING', 'UNKNOWN', 'CLOSING') AND row_version = :expected AND deleted = FALSE
                """, args);
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public Optional<PaymentRefundCapacity> capacity(String tenant, String payment) {
        scope(tenant);
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("payment", payment);
        var row = jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, payment_id, order_id, payee_merchant_id,
                       succeeded_at, succeeded_minor, currency, refunded_minor, reserved_minor, row_version
                FROM payment_refund_capacity WHERE tenant_id = :tenant AND payment_id = :payment AND deleted = FALSE
                """, args, BeanPropertyRowMapper.newInstance(PaymentRefundCapacityDO.class)).stream().findFirst().orElse(null);
        return row == null ? Optional.empty() : Optional.of(read(() -> converter.toCapacity(row)));
    }

    @Override
    public void insertCapacity(String tenant, String id, PaymentRefundCapacity capacity) {
        writable(tenant);
        if (capacity.version() != 0 || capacity.reservedMinor() != 0 || capacity.refundedMinor() != 0)
            throw new CommonException(INVALID_STATE);
        jdbcTransaction();
        var row = converter.toCapacityDO(tenant, id, capacity);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO payment_refund_capacity (id, tenant_id, created_at, updated_at, deleted, payment_id, order_id,
                    payee_merchant_id, succeeded_at, succeeded_minor, currency, refunded_minor, reserved_minor, row_version)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :paymentId, :orderId, :payeeMerchantId, :succeededAt,
                    :succeededMinor, :currency, :refundedMinor, :reservedMinor, :rowVersion)
                ON CONFLICT (tenant_id, payment_id) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) throw new PaymentWriteConflictException("Payment refund capacity already exists");
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private <T> T read(Supplier<T> mapping) {
        try {
            return mapping.get();
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private void writable(String tenant) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        scope(tenant);
    }

    private void scope(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }

    private void jdbcTransaction() {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly() || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
    }
}
