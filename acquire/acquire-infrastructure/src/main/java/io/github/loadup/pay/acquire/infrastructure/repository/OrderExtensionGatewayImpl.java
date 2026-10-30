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
import io.github.loadup.components.database.tenant.TenantContext;
import io.github.loadup.pay.acquire.app.port.AcquireWriteConflictException;
import io.github.loadup.pay.acquire.domain.gateway.OrderExtensionGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrder;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.OrderExtensionReceipt;
import io.github.loadup.pay.acquire.infrastructure.converter.OrderExtensionStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.dataobject.AcquireOrderDO;
import io.github.loadup.pay.acquire.infrastructure.dataobject.OrderExtensionReceiptDO;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class OrderExtensionGatewayImpl implements OrderExtensionGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final OrderExtensionStorageConverter converter;

    public OrderExtensionGatewayImpl(NamedParameterJdbcTemplate jdbc, OrderExtensionStorageConverter converter,
                                     Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.converter = converter;
    }

    @Override
    public Optional<OrderExtensionReceipt> findReceipt(String tenant, String orderId, String requestKey) {
        scope(tenant);
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("order", orderId).addValue("key", requestKey);
        var row = jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, order_id, caller_scope_hash, request_key, request_digest,
                       original_due_at, previous_due_at, requested_due_at, expected_order_version, deadline_version, applied_at
                FROM order_extension_receipt WHERE tenant_id = :tenant AND order_id = :order AND request_key = :key AND deleted = FALSE
                """, args, BeanPropertyRowMapper.newInstance(OrderExtensionReceiptDO.class)).stream().findFirst().orElse(null);
        if (row == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(converter.toReceipt(row));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public long deadlineVersion(String tenant, AcquireOrder order) {
        scope(tenant);
        requireTransaction();
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", order.id());
        var row = jdbc.query("""
                SELECT id, deadline_version, row_version, order_due_at FROM acquire_order
                WHERE tenant_id = :tenant AND id = :id AND deleted = FALSE
                """, args, BeanPropertyRowMapper.newInstance(AcquireOrderDO.class)).stream().findFirst().orElse(null);
        if (row == null || row.getDeadlineVersion() == null || row.getDeadlineVersion() < 1
                || row.getRowVersion() == null || row.getRowVersion() != order.version()
                || !order.orderDueAt().equals(row.getOrderDueAt())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return row.getDeadlineVersion();
    }

    @Override
    public void extend(String tenant, AcquireOrder updated, OrderExtensionReceipt receipt) {
        scope(tenant);
        requireTransaction();
        if (!updated.id().equals(receipt.orderId()) || !updated.orderDueAt().equals(receipt.requestedDueAt())
                || updated.version() != Math.incrementExact(receipt.expectedOrderVersion())
                || !(updated.state() == AcquireOrderState.INIT || updated.state() == AcquireOrderState.PENDING)) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var patch = converter.toDeadlinePatch(updated, receipt.deadlineVersion());
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", updated.id())
                .addValue("expectedVersion", receipt.expectedOrderVersion()).addValue("previousDeadlineVersion", receipt.deadlineVersion() - 1)
                .addValue("previousDue", receipt.previousDueAt()).addValue("due", patch.getOrderDueAt())
                .addValue("version", patch.getRowVersion()).addValue("deadlineVersion", patch.getDeadlineVersion())
                .addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE acquire_order SET order_due_at = :due, row_version = :version, deadline_version = :deadlineVersion, updated_at = :updated
                WHERE tenant_id = :tenant AND id = :id AND deleted = FALSE AND row_version = :expectedVersion
                    AND deadline_version = :previousDeadlineVersion AND order_due_at = :previousDue AND state IN ('INIT', 'PENDING')
                """, args);
        if (changed != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
        var row = converter.toDO(tenant, receipt);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO order_extension_receipt (id, tenant_id, created_at, updated_at, deleted, order_id, caller_scope_hash,
                    request_key, request_digest, original_due_at, previous_due_at, requested_due_at, expected_order_version, deadline_version, applied_at)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :orderId, :callerScopeHash, :requestKey, :requestDigest,
                    :originalDueAt, :previousDueAt, :requestedDueAt, :expectedOrderVersion, :deadlineVersion, :appliedAt)
                ON CONFLICT (tenant_id, order_id, request_key) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) {
            throw new AcquireWriteConflictException("Order extension request already exists");
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

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
    }
}
