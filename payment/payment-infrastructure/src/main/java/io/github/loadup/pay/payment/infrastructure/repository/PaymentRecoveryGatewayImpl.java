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
import io.github.loadup.pay.payment.domain.gateway.PaymentRecoveryGateway;
import io.github.loadup.pay.payment.domain.model.PaymentRecoveryCursor;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentRecoveryStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentRecoveryCursorDO;
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
public class PaymentRecoveryGatewayImpl implements PaymentRecoveryGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final PaymentRecoveryStorageConverter converter;
    private final Clock clock;

    public PaymentRecoveryGatewayImpl(NamedParameterJdbcTemplate jdbc, PaymentRecoveryStorageConverter converter,
                                      Clock clock) {
        this.jdbc = jdbc;
        this.converter = converter;
        this.clock = clock;
    }

    @Override
    public Optional<PaymentRecoveryCursor> find(String tenant, String paymentId) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        var row = row(tenant, paymentId, false);
        try {
            return Optional.ofNullable(row).map(converter::toDomain);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public Optional<PaymentRecoveryCursor> lock(String tenant, String paymentId) {
        writable(tenant);
        var row = row(tenant, paymentId, true);
        try {
            return Optional.ofNullable(row).map(converter::toDomain);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, PaymentRecoveryCursor value) {
        writable(tenant);
        var row = converter.toDO(tenant, value);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO payment_recovery_cursor (id, created_at, updated_at, deleted, generation, due_at, completed)
                VALUES (:id, :createdAt, :updatedAt, FALSE, :generation, :dueAt, :completed)
                """, new BeanPropertySqlParameterSource(row));
        if (count != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    @Override
    public void update(String tenant, PaymentRecoveryCursor before, PaymentRecoveryCursor after) {
        writable(tenant);
        boolean completed = !before.completed() && after.completed()
                && before.generation() == after.generation() && before.dueAt().equals(after.dueAt());
        boolean rescheduled = !before.completed() && !after.completed() && before.generation() != Long.MAX_VALUE
                && after.generation() == before.generation() + 1 && after.dueAt().isAfter(before.dueAt());
        if (!before.paymentId().equals(after.paymentId()) || (!completed && !rescheduled)) {
            throw new CommonException(INVALID_STATE);
        }

        var args = new MapSqlParameterSource().addValue("id", before.paymentId())
                .addValue("expected", before.generation()).addValue("generation", after.generation())
                .addValue("due", after.dueAt()).addValue("completed", after.completed()).addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE payment_recovery_cursor SET generation = :generation, due_at = :due, completed = :completed,
                    updated_at = :updated WHERE id = :id AND generation = :expected
                    AND completed = FALSE AND deleted = FALSE
                """, args);
        if (changed != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    private PaymentRecoveryCursorDO row(String tenant, String paymentId, boolean lock) {
        var args = new MapSqlParameterSource().addValue("id", paymentId);
        return jdbc.query("SELECT id, created_at, updated_at, generation, due_at, completed "
                        + "FROM payment_recovery_cursor WHERE id = :id AND deleted = FALSE"
                        + (lock ? " FOR UPDATE" : ""), args, BeanPropertyRowMapper.newInstance(PaymentRecoveryCursorDO.class))
                .stream().findFirst().orElse(null);
    }

    private void writable(String tenant) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
    }
}
