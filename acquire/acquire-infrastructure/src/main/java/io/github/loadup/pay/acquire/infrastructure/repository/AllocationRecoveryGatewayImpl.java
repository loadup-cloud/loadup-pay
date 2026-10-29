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
import io.github.loadup.pay.acquire.domain.gateway.AllocationRecoveryGateway;
import io.github.loadup.pay.acquire.domain.model.AllocationRecoveryCursor;
import io.github.loadup.pay.acquire.infrastructure.converter.AllocationRecoveryStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.dataobject.AllocationRecoveryCursorDO;
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

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class AllocationRecoveryGatewayImpl implements AllocationRecoveryGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final AllocationRecoveryStorageConverter converter;

    public AllocationRecoveryGatewayImpl(NamedParameterJdbcTemplate jdbc, AllocationRecoveryStorageConverter converter, @Qualifier("acquireClock") Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.converter = converter;
    }

    @Override
    public Optional<AllocationRecoveryCursor> find(String tenant, String paymentId) {
        trustedTenant(tenant);
        var row = row(tenant, paymentId, false);
        try {
            return Optional.ofNullable(row).map(converter::toDomain);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public Optional<AllocationRecoveryCursor> lock(String tenant, String paymentId) {
        writable(tenant);
        var row = row(tenant, paymentId, true);
        try {
            return Optional.ofNullable(row).map(converter::toDomain);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, AllocationRecoveryCursor value) {
        writable(tenant);
        var row = converter.toDO(tenant, value);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO allocation_recovery_cursor (id, tenant_id, created_at, updated_at, deleted, order_id, generation, due_at, completed)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :orderId, :generation, :dueAt, :completed)
                """, new BeanPropertySqlParameterSource(row));
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void update(String tenant, AllocationRecoveryCursor before, AllocationRecoveryCursor after) {
        writable(tenant);
        boolean completed = !before.completed() && after.completed()
                && before.generation() == after.generation() && before.dueAt().equals(after.dueAt());
        boolean rescheduled = !before.completed() && !after.completed() && before.generation() != Long.MAX_VALUE
                && after.generation() == before.generation() + 1 && after.dueAt().isAfter(before.dueAt());
        if (!before.paymentId().equals(after.paymentId()) || !before.orderId().equals(after.orderId()) || (!completed && !rescheduled))
            throw new CommonException(INVALID_STATE);

        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", before.paymentId())
                .addValue("order", before.orderId()).addValue("oldGeneration", before.generation()).addValue("oldDue", before.dueAt())
                .addValue("generation", after.generation()).addValue("due", after.dueAt()).addValue("completed", after.completed())
                .addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE allocation_recovery_cursor SET generation = :generation, due_at = :due, completed = :completed, updated_at = :updated
                WHERE tenant_id = :tenant AND id = :id AND deleted = FALSE AND order_id = :order
                    AND generation = :oldGeneration AND due_at = :oldDue AND completed = FALSE
                """, args);
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private AllocationRecoveryCursorDO row(String tenant, String paymentId, boolean lock) {
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", paymentId);
        return jdbc.query("""
                        SELECT id, tenant_id, created_at, updated_at, order_id, generation, due_at, completed
                        FROM allocation_recovery_cursor WHERE tenant_id = :tenant AND id = :id AND deleted = FALSE
                        """ + (lock ? " FOR UPDATE" : ""), args, BeanPropertyRowMapper.newInstance(AllocationRecoveryCursorDO.class))
                .stream().findFirst().orElse(null);
    }

    private void writable(String tenant) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
        trustedTenant(tenant);
    }

    private void trustedTenant(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }
}
