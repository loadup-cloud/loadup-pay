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
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.acquire.app.port.AcquireWriteConflictException;
import io.github.loadup.pay.acquire.app.port.PaymentInitiationCodec;
import io.github.loadup.pay.acquire.domain.gateway.OrderClosureGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrder;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.OrderCloseIntent;
import io.github.loadup.pay.acquire.domain.model.StoredPaymentAllocation;
import io.github.loadup.pay.acquire.infrastructure.converter.OrderClosureStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.converter.PaymentInitiationStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.dataobject.OrderCloseIntentDO;
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
import java.util.List;
import java.util.Optional;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class OrderClosureGatewayImpl implements OrderClosureGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final OrderClosureStorageConverter converter;
    private final PaymentInitiationStorageConverter allocationConverter;
    private final PaymentInitiationCodec codec;

    public OrderClosureGatewayImpl(NamedParameterJdbcTemplate jdbc,
                                   OrderClosureStorageConverter converter, PaymentInitiationStorageConverter allocationConverter,
                                   PaymentInitiationCodec codec, @Qualifier("acquireClock") Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.converter = converter;
        this.allocationConverter = allocationConverter;
        this.codec = codec;
    }

    @Override
    public Optional<OrderCloseIntent> findIntent(String tenant, String orderId) {
        scope(tenant);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("order", orderId);
        var row = jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, order_id, payee_merchant_id, caller_scope_hash, request_key,
                       request_digest, reason_code, close_epoch, requested_at, cursor_payment_id, row_version
                FROM order_close_intent WHERE tenant_id = :tenant AND order_id = :order AND deleted = FALSE
                """, args, BeanPropertyRowMapper.newInstance(OrderCloseIntentDO.class)).stream().findFirst().orElse(null);
        if (row == null) return Optional.empty();
        try {
            return Optional.of(converter.toIntent(row));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void keepPending(String tenant, AcquireOrder before, AcquireOrder after) {
        scope(tenant);
        requireTransaction();
        if (before.state() != AcquireOrderState.INIT
                || before.unpaidAction() != io.github.loadup.pay.acquire.domain.model.UnpaidOrderAction.KEEP_PENDING
                || !after.equals(before.checkDue(before.orderDueAt()))) throw new CommonException(SNAPSHOT_INVALID);
        var patch = converter.toClosingPatch(after);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", before.id()).addValue("expected", before.version())
                .addValue("due", before.orderDueAt()).addValue("oldEpoch", before.closeEpoch()).addValue("state", patch.getState())
                .addValue("epoch", patch.getCloseEpoch()).addValue("version", patch.getRowVersion()).addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE acquire_order SET state = :state, close_epoch = :epoch, row_version = :version, updated_at = :updated
                WHERE tenant_id = :tenant AND id = :id AND deleted = FALSE AND state = 'INIT' AND row_version = :expected
                    AND order_due_at = :due AND close_epoch = :oldEpoch
                """, args);
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void start(String tenant, AcquireOrder closing, OrderCloseIntent intent, long expectedOrderVersion) {
        scope(tenant);
        requireTransaction();
        if (closing.state() != AcquireOrderState.CLOSING || !closing.id().equals(intent.orderId())
                || !closing.payeeMerchantId().equals(intent.payeeMerchantId()) || closing.closeEpoch() != intent.closeEpoch()
                || closing.version() != Math.incrementExact(expectedOrderVersion) || intent.rowVersion() != 0
                || !intent.cursorPaymentId().isEmpty()) throw new CommonException(SNAPSHOT_INVALID);
        var patch = converter.toClosingPatch(closing);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", closing.id()).addValue("expected", expectedOrderVersion)
                .addValue("state", patch.getState()).addValue("epoch", patch.getCloseEpoch()).addValue("version", patch.getRowVersion())
                .addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE acquire_order SET state = :state, close_epoch = :epoch, row_version = :version, updated_at = :updated
                WHERE tenant_id = :tenant AND id = :id AND deleted = FALSE AND row_version = :expected AND state IN ('INIT', 'PENDING')
                """, args);
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
        var row = converter.toDO(tenant, intent);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO order_close_intent (id, tenant_id, created_at, updated_at, deleted, order_id, payee_merchant_id,
                    caller_scope_hash, request_key, request_digest, reason_code, close_epoch, requested_at, cursor_payment_id, row_version)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :orderId, :payeeMerchantId, :callerScopeHash, :requestKey,
                    :requestDigest, :reasonCode, :closeEpoch, :requestedAt, :cursorPaymentId, :rowVersion)
                ON CONFLICT (tenant_id, order_id) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) throw new AcquireWriteConflictException("Order close intent already exists");
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public List<StoredPaymentAllocation> heldAfter(String tenant, String orderId, String afterPaymentId, int limit) {
        scope(tenant);
        if (limit < 1 || limit > 16 || afterPaymentId == null) throw new CommonException(INVALID_ARGUMENT);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("order", orderId)
                .addValue("after", afterPaymentId).addValue("limit", limit);
        var rows = jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, order_id, payment_id, actor_scope, action_key,
                       request_digest, caller_scope_hash, amount_minor, currency, state, payment_source_version,
                       intent_payload, payload_hash, row_version
                FROM order_payment_allocation WHERE tenant_id = :tenant AND order_id = :order AND deleted = FALSE
                    AND state = 'HELD' AND (:after = '' OR payment_id > :after)
                ORDER BY payment_id ASC LIMIT :limit
                """, args, BeanPropertyRowMapper.newInstance(OrderPaymentAllocationDO.class));
        try {
            return rows.stream().map(row -> {
                var value = allocationConverter.toStored(row, allocationConverter.toAllocation(row));
                codec.decode(value);
                return value;
            }).toList();
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void advanceCursor(String tenant, OrderCloseIntent updated, long expectedVersion) {
        scope(tenant);
        requireTransaction();
        if (updated.rowVersion() != Math.incrementExact(expectedVersion)) throw new CommonException(SNAPSHOT_INVALID);
        // Empty string starts a new pass; it is a real value, never an ignored-null clear operation.
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", updated.id()).addValue("order", updated.orderId())
                .addValue("expected", expectedVersion).addValue("cursor", updated.cursorPaymentId())
                .addValue("version", updated.rowVersion()).addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE order_close_intent SET cursor_payment_id = :cursor, row_version = :version, updated_at = :updated
                WHERE tenant_id = :tenant AND id = :id AND order_id = :order AND row_version = :expected AND deleted = FALSE
                """, args);
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public boolean allAllocationsSettled(String tenant, String orderId) {
        scope(tenant);
        requireTransaction();
        var args = new MapSqlParameterSource("tenant", tenant).addValue("order", orderId);
        long pending = jdbc.queryForObject("""
                SELECT COUNT(*) FROM order_payment_allocation WHERE tenant_id = :tenant AND order_id = :order
                    AND deleted = FALSE AND state IN ('INIT', 'HELD')
                """, args, Long.class);
        if (pending != 0) return false;
        long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM order_payment_allocation WHERE tenant_id = :tenant AND order_id = :order AND deleted = FALSE
                """, args, Long.class);
        long proofs = jdbc.queryForObject("""
                SELECT COUNT(*) FROM order_payment_outcome WHERE tenant_id = :tenant AND order_id = :order AND deleted = FALSE
                """, args, Long.class);
        return count == proofs;
    }

    @Override
    public void finish(String tenant, AcquireOrder closed, long expectedOrderVersion, LocalDateTime closedAt) {
        scope(tenant);
        requireTransaction();
        if (closed.state() != AcquireOrderState.CLOSED || closed.version() != Math.incrementExact(expectedOrderVersion)
                || closed.closeEpoch() <= 0 || closedAt == null) throw new CommonException(SNAPSHOT_INVALID);
        var patch = converter.toClosedPatch(closed, closedAt);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", closed.id()).addValue("expected", expectedOrderVersion)
                .addValue("epoch", closed.closeEpoch()).addValue("state", patch.getState()).addValue("version", patch.getRowVersion())
                .addValue("closedAt", patch.getClosedAt()).addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE acquire_order SET state = :state, row_version = :version, closed_at = :closedAt, updated_at = :updated
                WHERE tenant_id = :tenant AND id = :id AND deleted = FALSE AND state = 'CLOSING' AND close_epoch = :epoch
                    AND row_version = :expected AND payment_reserved_minor = 0 AND reserved_payment_count = 0
                """, args);
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private void scope(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
    }
}
