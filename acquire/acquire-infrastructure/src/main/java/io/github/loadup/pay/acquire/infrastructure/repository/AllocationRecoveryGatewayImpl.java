/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.pay.acquire.infrastructure.repository;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.acquire.domain.gateway.AllocationRecoveryGateway;
import io.github.loadup.pay.acquire.domain.model.AllocationRecoveryCursor;
import io.github.loadup.pay.acquire.infrastructure.converter.AllocationRecoveryStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.dataobject.AllocationRecoveryCursorDO;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
public class AllocationRecoveryGatewayImpl implements AllocationRecoveryGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final AllocationRecoveryStorageConverter converter;

    public AllocationRecoveryGatewayImpl(
            NamedParameterJdbcTemplate jdbc, AllocationRecoveryStorageConverter converter, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.converter = converter;
    }

    @Override
    public Optional<AllocationRecoveryCursor> find(String paymentId) {
        var row = row(paymentId, false);
        try {
            return Optional.ofNullable(row).map(converter::toDomain);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public Optional<AllocationRecoveryCursor> lock(String paymentId) {
        writable();
        var row = row(paymentId, true);
        try {
            return Optional.ofNullable(row).map(converter::toDomain);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(AllocationRecoveryCursor value) {
        writable();
        var row = converter.toDO(value);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO allocation_recovery_cursor (id, created_at, updated_at, deleted, order_id, generation, due_at, completed)
                VALUES (:id, :createdAt, :updatedAt, FALSE, :orderId, :generation, :dueAt, :completed)
                """, new BeanPropertySqlParameterSource(row));
        if (count != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    @Override
    public void update(AllocationRecoveryCursor before, AllocationRecoveryCursor after) {
        writable();
        boolean completed = !before.completed()
                && after.completed()
                && before.generation() == after.generation()
                && before.dueAt().equals(after.dueAt());
        boolean rescheduled = !before.completed()
                && !after.completed()
                && before.generation() != Long.MAX_VALUE
                && after.generation() == before.generation() + 1
                && after.dueAt().isAfter(before.dueAt());
        if (!before.paymentId().equals(after.paymentId())
                || !before.orderId().equals(after.orderId())
                || (!completed && !rescheduled)) {
            throw new CommonException(INVALID_STATE);
        }

        var args = new MapSqlParameterSource()
                .addValue("id", before.paymentId())
                .addValue("order", before.orderId())
                .addValue("oldGeneration", before.generation())
                .addValue("oldDue", before.dueAt())
                .addValue("generation", after.generation())
                .addValue("due", after.dueAt())
                .addValue("completed", after.completed())
                .addValue("updated", LocalDateTime.now(clock));
        int changed = jdbc.update("""
                UPDATE allocation_recovery_cursor SET generation = :generation, due_at = :due, completed = :completed, updated_at = :updated
                WHERE id = :id AND deleted = FALSE AND order_id = :order
                    AND generation = :oldGeneration AND due_at = :oldDue AND completed = FALSE
                """, args);
        if (changed != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    private AllocationRecoveryCursorDO row(String paymentId, boolean lock) {
        var args = new MapSqlParameterSource().addValue("id", paymentId);
        return jdbc
                .query(
                        """
                        SELECT id, created_at, updated_at, order_id, generation, due_at, completed
                        FROM allocation_recovery_cursor WHERE id = :id AND deleted = FALSE
                        """ + (lock ? " FOR UPDATE" : ""),
                        args,
                        BeanPropertyRowMapper.newInstance(AllocationRecoveryCursorDO.class))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private void writable() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(
                        jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
    }
}
