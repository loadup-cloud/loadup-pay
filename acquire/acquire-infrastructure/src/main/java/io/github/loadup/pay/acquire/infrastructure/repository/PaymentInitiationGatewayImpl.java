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
package io.github.loadup.pay.acquire.infrastructure.repository;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.acquire.app.port.AcquireWriteConflictException;
import io.github.loadup.pay.acquire.app.port.PaymentInitiationCodec;
import io.github.loadup.pay.acquire.domain.gateway.PaymentInitiationGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrder;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.AllocationState;
import io.github.loadup.pay.acquire.domain.model.StoredPaymentAllocation;
import io.github.loadup.pay.acquire.infrastructure.converter.PaymentInitiationStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.dataobject.OrderPaymentAllocationDO;
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

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class PaymentInitiationGatewayImpl implements PaymentInitiationGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final PaymentInitiationStorageConverter converter;
    private final PaymentInitiationCodec codec;

    public PaymentInitiationGatewayImpl(
            PaymentInitiationStorageConverter converter,
            PaymentInitiationCodec codec, NamedParameterJdbcTemplate jdbc, @Qualifier("acquireClock") Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.converter = converter;
        this.codec = codec;
    }

    @Override
    public Optional<StoredPaymentAllocation> findAction(
            String tenant, String orderId, String actorScope, String actionKey) {
        var args = new MapSqlParameterSource("tenant", tenant).addValue("order", orderId)
                .addValue("actor", actorScope).addValue("key", actionKey);
        var row = jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, order_id, payment_id, actor_scope, action_key, request_digest,
                       caller_scope_hash, amount_minor, currency, state, payment_source_version, intent_payload, payload_hash, row_version
                FROM order_payment_allocation WHERE tenant_id = :tenant AND order_id = :order AND actor_scope = :actor
                    AND action_key = :key AND deleted = FALSE
                """, args, BeanPropertyRowMapper.newInstance(OrderPaymentAllocationDO.class)).stream().findFirst().orElse(null);
        if (row == null) return Optional.empty();
        try {
            var value = converter.toStored(row, converter.toAllocation(row));
            codec.decode(value);
            return Optional.of(value);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void attachInput(String tenant, StoredPaymentAllocation original, StoredPaymentAllocation prepared) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (!original.allocation().equals(prepared.allocation())
                || !original.callerScopeHash().equals(prepared.callerScopeHash())
                || original.allocation().state() != AllocationState.HELD
                || original.allocation().paymentSourceVersion() != 0
                || prepared.rowVersion() != Math.incrementExact(original.rowVersion())
                || codec.decode(original).preparedInputRef() != null
                || codec.decode(prepared).preparedInputRef() == null) throw new CommonException(SNAPSHOT_INVALID);
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly() || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", original.allocation().id())
                .addValue("payment", original.allocation().paymentId()).addValue("expected", original.rowVersion())
                .addValue("oldHash", original.payloadHash()).addValue("payload", prepared.intentPayload())
                .addValue("hash", prepared.payloadHash()).addValue("version", prepared.rowVersion()).addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE order_payment_allocation SET intent_payload = :payload, payload_hash = :hash, row_version = :version, updated_at = :updated
                WHERE tenant_id = :tenant AND id = :id AND payment_id = :payment AND state = 'HELD' AND payment_source_version = 0
                    AND row_version = :expected AND payload_hash = :oldHash AND deleted = FALSE
                """, args);
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void reserve(String tenant, AcquireOrder updated, StoredPaymentAllocation value, long expectedOrderVersion) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (!updated.id().equals(value.allocation().orderId())
                || updated.version() != Math.incrementExact(expectedOrderVersion)
                || updated.state() != AcquireOrderState.PENDING
                || value.allocation().state() != AllocationState.HELD
                || value.rowVersion() != 0
                || !updated.amount()
                .getCurrencyValue()
                .equals(value.allocation().amount().getCurrencyValue()))
            throw new CommonException(SNAPSHOT_INVALID);
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly() || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
        var patch = converter.toReservationPatch(updated);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", updated.id()).addValue("expected", expectedOrderVersion)
                .addValue("reserved", patch.getPaymentReservedMinor()).addValue("count", patch.getReservedPaymentCount())
                .addValue("state", patch.getState()).addValue("version", patch.getRowVersion()).addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE acquire_order SET payment_reserved_minor = :reserved, reserved_payment_count = :count,
                    state = :state, row_version = :version, updated_at = :updated WHERE tenant_id = :tenant AND id = :id
                    AND row_version = :expected AND state IN ('INIT', 'PENDING') AND deleted = FALSE
                """, args);
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
        var row = converter.toAllocationDO(tenant, value);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO order_payment_allocation (id, tenant_id, created_at, updated_at, deleted, order_id, payment_id,
                    actor_scope, action_key, request_digest, caller_scope_hash, amount_minor, currency, state, payment_source_version,
                    intent_payload, payload_hash, row_version)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :orderId, :paymentId, :actorScope, :actionKey, :requestDigest,
                    :callerScopeHash, :amountMinor, :currency, :state, :paymentSourceVersion, :intentPayload, :payloadHash, :rowVersion)
                ON CONFLICT (tenant_id, order_id, actor_scope, action_key) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) throw new AcquireWriteConflictException("Payment allocation action already exists");
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
    }
}
