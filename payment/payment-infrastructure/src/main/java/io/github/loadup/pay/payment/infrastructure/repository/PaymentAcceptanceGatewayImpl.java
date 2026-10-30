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
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentGate;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentAcceptanceStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentGateDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentOrderDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentParentGateDO;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class PaymentAcceptanceGatewayImpl implements PaymentAcceptanceGateway {
    private static final String PAYMENT_COLUMNS = "id, created_at, updated_at, order_id, allocation_id, admission_id, "
            + "payee_merchant_id, amount_minor, currency, request_digest, caller_service_id, state, active_attempt_id, "
            + "successful_attempt_id, limit_operation_id, initiated_at, first_unknown_at, succeeded_at, terminal_evidence_id, "
            + "intent_payload, payload_hash, row_version";
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final PaymentAcceptanceStorageConverter converter;
    private final PaymentAcceptanceCodec codec;

    public PaymentAcceptanceGatewayImpl(NamedParameterJdbcTemplate jdbc,
                                        PaymentAcceptanceStorageConverter converter, PaymentAcceptanceCodec codec,
                                        Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.converter = converter;
        this.codec = codec;
    }

    @Override
    public Optional<StoredPaymentAcceptance> find(String tenant, String id) {
        scope(tenant);
        var args = new MapSqlParameterSource().addValue("id", id);
        return stored(jdbc.query("SELECT " + PAYMENT_COLUMNS + " FROM payment_order WHERE "
                        + "id = :id AND deleted = FALSE", args, BeanPropertyRowMapper.newInstance(PaymentOrderDO.class))
                .stream().findFirst().orElse(null));
    }

    @Override
    public Optional<StoredPaymentAcceptance> findAllocation(String tenant, String id) {
        scope(tenant);
        var args = new MapSqlParameterSource().addValue("allocation", id);
        return stored(jdbc.query("SELECT " + PAYMENT_COLUMNS + " FROM payment_order WHERE "
                        + "allocation_id = :allocation AND deleted = FALSE", args, BeanPropertyRowMapper.newInstance(PaymentOrderDO.class))
                .stream().findFirst().orElse(null));
    }

    private Optional<StoredPaymentAcceptance> stored(PaymentOrderDO row) {
        if (row == null) {
            return Optional.empty();
        }
        try {
            var stored = converter.toStored(row, converter.toPayment(row));
            codec.decode(stored);
            return Optional.of(stored);
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public Optional<StoredPaymentGate> lockParent(String tenant, String orderId) {
        gateTransaction(tenant);
        var args = new MapSqlParameterSource().addValue("orderId", orderId);
        var row = jdbc.query("""
                SELECT id, created_at, updated_at, order_id, payee_merchant_id, caller_service_id,
                       state, close_epoch, row_version FROM payment_parent_gate
                WHERE order_id = :orderId AND deleted = FALSE FOR UPDATE
                """, args, BeanPropertyRowMapper.newInstance(PaymentParentGateDO.class)).stream().findFirst().orElse(null);
        try {
            return Optional.ofNullable(row).map(value -> converter.toStoredParent(value, converter.toParentGate(value)));
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public Optional<StoredPaymentGate> lockPayment(String tenant, String paymentId) {
        gateTransaction(tenant);
        var args = new MapSqlParameterSource().addValue("payment", paymentId);
        var row = jdbc.query("""
                SELECT id, created_at, updated_at, order_id, payment_id, payee_merchant_id, caller_service_id,
                       state, fence_epoch, reason_code, row_version FROM payment_gate
                WHERE payment_id = :payment AND deleted = FALSE FOR UPDATE
                """, args, BeanPropertyRowMapper.newInstance(PaymentGateDO.class)).stream().findFirst().orElse(null);
        try {
            return Optional.ofNullable(row).map(value -> converter.toStoredGate(value, converter.toPaymentGate(value)));
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insertParent(String tenant, StoredPaymentGate gate) {
        gateTransaction(tenant);
        if (gate.gate().paymentId() != null) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        var row = converter.toParentDO(tenant, gate);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO payment_parent_gate (id, created_at, updated_at, deleted, order_id,
                    payee_merchant_id, caller_service_id, state, close_epoch, row_version)
                VALUES (:id, :createdAt, :updatedAt, FALSE, :orderId, :payeeMerchantId,
                    :callerServiceId, :state, :closeEpoch, :rowVersion)
                ON CONFLICT (order_id) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) {
            throw new PaymentWriteConflictException("Payment parent gate already exists");
        }
        if (count != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    @Override
    public void insertPaymentGate(String tenant, StoredPaymentGate gate) {
        gateTransaction(tenant);
        if (gate.gate().paymentId() == null) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        var row = converter.toGateDO(tenant, gate);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO payment_gate (id, created_at, updated_at, deleted, order_id, payment_id,
                    payee_merchant_id, caller_service_id, state, fence_epoch, reason_code, row_version)
                VALUES (:id, :createdAt, :updatedAt, FALSE, :orderId, :paymentId, :payeeMerchantId,
                    :callerServiceId, :state, :fenceEpoch, :reasonCode, :rowVersion)
                ON CONFLICT (payment_id) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) {
            throw new PaymentWriteConflictException("Payment gate already exists");
        }
        if (count != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    @Override
    public void insert(String tenant, StoredPaymentAcceptance value) {
        gateTransaction(tenant);
        var row = converter.toPaymentDO(tenant, value);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO payment_order (id, created_at, updated_at, deleted, order_id, allocation_id,
                    admission_id, payee_merchant_id, amount_minor, currency, request_digest, caller_service_id, state,
                    active_attempt_id, successful_attempt_id, limit_operation_id, initiated_at, first_unknown_at, succeeded_at,
                    terminal_evidence_id, intent_payload, payload_hash, row_version)
                VALUES (:id, :createdAt, :updatedAt, FALSE, :orderId, :allocationId, :admissionId,
                    :payeeMerchantId, :amountMinor, :currency, :requestDigest, :callerServiceId, :state, :activeAttemptId,
                    :successfulAttemptId, :limitOperationId, :initiatedAt, :firstUnknownAt, :succeededAt,
                    :terminalEvidenceId, :intentPayload, :payloadHash, :rowVersion)
                ON CONFLICT (allocation_id) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) {
            throw new PaymentWriteConflictException("Payment allocation already exists");
        }
        if (count != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    private void scope(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
    }

    private void gateTransaction(String tenant) {
        scope(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
    }
}
