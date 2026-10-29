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
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.app.port.PaymentWriteConflictException;
import io.github.loadup.pay.payment.domain.gateway.PaymentControlGateway;
import io.github.loadup.pay.payment.domain.model.GateState;
import io.github.loadup.pay.payment.domain.model.PaymentChildrenStatus;
import io.github.loadup.pay.payment.domain.model.PaymentControlReceipt;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentGate;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentAcceptanceStorageConverter;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentControlStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentControlReceiptDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentOrderDO;
import org.springframework.beans.factory.annotation.Qualifier;
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

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class PaymentControlGatewayImpl implements PaymentControlGateway {
    private static final String PAYMENT_COLUMNS = "id, tenant_id, created_at, updated_at, order_id, allocation_id, admission_id, "
            + "payee_merchant_id, amount_minor, currency, request_digest, caller_service_id, state, active_attempt_id, "
            + "successful_attempt_id, limit_operation_id, initiated_at, first_unknown_at, succeeded_at, terminal_evidence_id, "
            + "intent_payload, payload_hash, row_version";
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock databaseClock;
    private final PaymentAcceptanceStorageConverter converter;
    private final PaymentControlStorageConverter receiptConverter;
    private final PaymentAcceptanceCodec codec;
    private final Clock clock;

    public PaymentControlGatewayImpl(
            NamedParameterJdbcTemplate jdbc,
            PaymentAcceptanceStorageConverter converter,
            PaymentControlStorageConverter receiptConverter,
            PaymentAcceptanceCodec codec,
            @Qualifier("paymentClock") Clock clock,
            @Qualifier("databaseClock") Clock databaseClock) {
        this.jdbc = jdbc;
        this.databaseClock = databaseClock;
        this.converter = converter;
        this.receiptConverter = receiptConverter;
        this.codec = codec;
        this.clock = clock;
    }

    @Override
    public Optional<StoredPaymentAcceptance> lockPayment(String tenant, String paymentId) {
        gateTransaction(tenant);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", paymentId);
        var row = jdbc.query("SELECT " + PAYMENT_COLUMNS + " FROM payment_order WHERE tenant_id = :tenant "
                        + "AND id = :id AND deleted = FALSE FOR UPDATE", args, BeanPropertyRowMapper.newInstance(PaymentOrderDO.class))
                .stream().findFirst().orElse(null);
        if (row == null) return Optional.empty();
        try {
            var stored = converter.toStored(row, converter.toPayment(row));
            codec.decode(stored);
            return Optional.of(stored);
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void updatePayment(String tenant, StoredPaymentAcceptance value, long expectedVersion) {
        gateTransaction(tenant);
        if (value.payment().version() <= expectedVersion) throw new CommonException(INVALID_STATE);
        LocalDateTime firstUnknownAt = null;
        if (value.payment().state() == PaymentState.UNKNOWN) {
            // The caller holds the financial locks; the version predicate also rejects stale patches.
            var query = new MapSqlParameterSource("tenant", tenant).addValue("id", value.payment().id()).addValue("version", expectedVersion);
            var row = jdbc.query("SELECT id, first_unknown_at FROM payment_order WHERE tenant_id = :tenant "
                            + "AND id = :id AND row_version = :version AND deleted = FALSE", query,
                    BeanPropertyRowMapper.newInstance(PaymentOrderDO.class)).stream().findFirst().orElse(null);
            if (row == null) throw new CommonException(CONCURRENT_UPDATE);
            firstUnknownAt = row.getFirstUnknownAt() == null
                    ? LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS) : row.getFirstUnknownAt();
        }
        var row = converter.toPaymentPatch(tenant, value, firstUnknownAt);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", value.payment().id()).addValue("expected", expectedVersion)
                .addValue("state", row.getState()).addValue("active", row.getActiveAttemptId()).addValue("success", row.getSuccessfulAttemptId())
                .addValue("unknown", row.getFirstUnknownAt()).addValue("evidence", row.getTerminalEvidenceId())
                .addValue("version", row.getRowVersion()).addValue("updated", LocalDateTime.now(databaseClock));
        int changed = jdbc.update("""
                UPDATE payment_order SET state = :state, active_attempt_id = COALESCE(:active, active_attempt_id),
                    successful_attempt_id = COALESCE(:success, successful_attempt_id),
                    first_unknown_at = COALESCE(:unknown, first_unknown_at), terminal_evidence_id = COALESCE(:evidence, terminal_evidence_id),
                    row_version = :version, updated_at = :updated
                WHERE tenant_id = :tenant AND id = :id AND row_version = :expected AND deleted = FALSE
                """, args);
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void updateParent(String tenant, StoredPaymentGate value, long expectedVersion) {
        gateTransaction(tenant);
        checkGateVersion(value, expectedVersion);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", value.id()).addValue("orderId", value.gate().orderId())
                .addValue("epoch", value.gate().fenceEpoch()).addValue("version", value.rowVersion())
                .addValue("expected", expectedVersion).addValue("updated", LocalDateTime.now(databaseClock));
        int changed = jdbc.update("""
                UPDATE payment_parent_gate SET state = 'FENCED', close_epoch = :epoch, row_version = :version, updated_at = :updated
                WHERE tenant_id = :tenant AND id = :id AND order_id = :orderId AND row_version = :expected AND deleted = FALSE
                """, args);
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void updateGate(String tenant, StoredPaymentGate value, long expectedVersion) {
        gateTransaction(tenant);
        checkGateVersion(value, expectedVersion);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", value.id()).addValue("payment", value.gate().paymentId())
                .addValue("epoch", value.gate().fenceEpoch()).addValue("version", value.rowVersion())
                .addValue("expected", expectedVersion).addValue("updated", LocalDateTime.now(databaseClock));
        int changed = jdbc.update("""
                UPDATE payment_gate SET state = 'FENCED', fence_epoch = :epoch, row_version = :version, updated_at = :updated
                WHERE tenant_id = :tenant AND id = :id AND payment_id = :payment AND row_version = :expected AND deleted = FALSE
                """, args);
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private void checkGateVersion(StoredPaymentGate value, long expectedVersion) {
        if (value.rowVersion() != Math.incrementExact(expectedVersion)
                || value.gate().state() != GateState.FENCED) throw new CommonException(INVALID_STATE);
    }

    @Override
    public Optional<PaymentControlReceipt> findReceipt(String tenant, String kind, String targetId, String requestKey) {
        scope(tenant);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("kind", kind).addValue("target", targetId).addValue("key", requestKey);
        var row = jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, kind, target_id, order_id, payee_merchant_id,
                       caller_service_id, request_key, request_digest, requested_epoch, reason_code, no_collection_evidence_id
                FROM payment_control_receipt WHERE tenant_id = :tenant AND kind = :kind AND target_id = :target
                    AND request_key = :key AND deleted = FALSE
                """, args, BeanPropertyRowMapper.newInstance(PaymentControlReceiptDO.class)).stream().findFirst().orElse(null);
        return receipt(row);
    }

    @Override
    public Optional<PaymentControlReceipt> findNoCollectionReceipt(String tenant, String paymentId) {
        scope(tenant);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("target", paymentId);
        var row = jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, kind, target_id, order_id, payee_merchant_id,
                       caller_service_id, request_key, request_digest, requested_epoch, reason_code, no_collection_evidence_id
                FROM payment_control_receipt WHERE tenant_id = :tenant AND kind = 'PAYMENT_CLOSE' AND target_id = :target
                    AND no_collection_evidence_id IS NOT NULL AND deleted = FALSE LIMIT 1
                """, args, BeanPropertyRowMapper.newInstance(PaymentControlReceiptDO.class)).stream().findFirst().orElse(null);
        return receipt(row);
    }

    private Optional<PaymentControlReceipt> receipt(PaymentControlReceiptDO row) {
        try {
            return Optional.ofNullable(row).map(receiptConverter::toReceipt);
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insertReceipt(String tenant, PaymentControlReceipt value) {
        gateTransaction(tenant);
        var row = receiptConverter.toReceiptDO(tenant, value);
        var now = LocalDateTime.now(databaseClock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO payment_control_receipt (id, tenant_id, created_at, updated_at, deleted, kind, target_id,
                    order_id, payee_merchant_id, caller_service_id, request_key, request_digest, requested_epoch,
                    reason_code, no_collection_evidence_id)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :kind, :targetId, :orderId, :payeeMerchantId,
                    :callerServiceId, :requestKey, :requestDigest, :requestedEpoch, :reasonCode, :noCollectionEvidenceId)
                ON CONFLICT (tenant_id, kind, target_id, request_key) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) throw new PaymentWriteConflictException("Payment control request already exists");
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public PaymentChildrenStatus childrenStatus(String tenant, String orderId) {
        gateTransaction(tenant);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("order", orderId);
        return jdbc.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE state IN ('INIT', 'PROCESSING', 'UNKNOWN', 'CLOSING')) AS active,
                    COUNT(*) FILTER (WHERE state IN ('SUCCEEDED', 'FAILED', 'CLOSED') AND terminal_evidence_id IS NULL) AS missing_proof,
                    COUNT(*) FILTER (WHERE state IN ('PROCESSING', 'UNKNOWN', 'CLOSING') AND active_attempt_id IS NOT NULL) AS uncertain
                FROM payment_order WHERE tenant_id = :tenant AND order_id = :order AND deleted = FALSE
                """, args, (rs, index) -> new PaymentChildrenStatus(
                Math.addExact(rs.getLong("active"), rs.getLong("missing_proof")),
                Math.addExact(rs.getLong("uncertain"), rs.getLong("missing_proof"))));
    }

    private void gateTransaction(String tenant) {
        scope(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
    }

    private void scope(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }
}
