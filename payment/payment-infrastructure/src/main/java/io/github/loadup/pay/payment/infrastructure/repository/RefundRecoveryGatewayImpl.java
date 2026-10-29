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
import io.github.loadup.pay.payment.domain.gateway.RefundRecoveryGateway;
import io.github.loadup.pay.payment.domain.model.RefundRecoveryCursor;
import io.github.loadup.pay.payment.infrastructure.converter.RefundRecoveryStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundRecoveryCursorDO;
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

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class RefundRecoveryGatewayImpl implements RefundRecoveryGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final RefundRecoveryStorageConverter converter;
    private final Clock clock;

    public RefundRecoveryGatewayImpl(NamedParameterJdbcTemplate jdbc, RefundRecoveryStorageConverter converter,
                                     @Qualifier("databaseClock") Clock clock) {
        this.jdbc = jdbc;
        this.converter = converter;
        this.clock = clock;
    }

    @Override
    public Optional<RefundRecoveryCursor> find(String tenant, String requestId) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        var row = row(tenant, requestId, false);
        try {
            return Optional.ofNullable(row).map(converter::toDomain);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public Optional<RefundRecoveryCursor> lock(String tenant, String requestId) {
        writable(tenant);
        var row = row(tenant, requestId, true);
        try {
            return Optional.ofNullable(row).map(converter::toDomain);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, RefundRecoveryCursor value) {
        writable(tenant);
        var row = converter.toDO(tenant, value);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO refund_recovery_cursor (id, tenant_id, created_at, updated_at, deleted, generation, due_at, completed)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :generation, :dueAt, :completed)
                """, new BeanPropertySqlParameterSource(row));
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void update(String tenant, RefundRecoveryCursor before, RefundRecoveryCursor after) {
        writable(tenant);
        boolean completed = !before.completed() && after.completed()
                && before.generation() == after.generation() && before.dueAt().equals(after.dueAt());
        boolean rescheduled = !before.completed() && !after.completed() && before.generation() != Long.MAX_VALUE
                && after.generation() == before.generation() + 1 && after.dueAt().isAfter(before.dueAt());
        if (!before.requestId().equals(after.requestId()) || (!completed && !rescheduled))
            throw new CommonException(INVALID_STATE);

        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", before.requestId())
                .addValue("expected", before.generation()).addValue("generation", after.generation())
                .addValue("due", after.dueAt()).addValue("completed", after.completed()).addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE refund_recovery_cursor SET generation = :generation, due_at = :due, completed = :completed,
                    updated_at = :updated WHERE tenant_id = :tenant AND id = :id AND generation = :expected
                    AND completed = FALSE AND deleted = FALSE
                """, args);
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private RefundRecoveryCursorDO row(String tenant, String requestId, boolean lock) {
        var args = new MapSqlParameterSource("tenant", tenant).addValue("id", requestId);
        return jdbc.query("SELECT id, tenant_id, created_at, updated_at, generation, due_at, completed "
                        + "FROM refund_recovery_cursor WHERE tenant_id = :tenant AND id = :id AND deleted = FALSE"
                        + (lock ? " FOR UPDATE" : ""), args, BeanPropertyRowMapper.newInstance(RefundRecoveryCursorDO.class))
                .stream().findFirst().orElse(null);
    }

    private void writable(String tenant) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }
}
