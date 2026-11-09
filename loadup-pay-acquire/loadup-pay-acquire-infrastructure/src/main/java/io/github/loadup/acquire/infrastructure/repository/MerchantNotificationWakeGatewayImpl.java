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
package io.github.loadup.acquire.infrastructure.repository;

import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.INVALID_PAGE;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

import io.github.loadup.acquire.app.port.AcquireWriteConflictException;
import io.github.loadup.acquire.domain.gateway.MerchantNotificationWakeGateway;
import io.github.loadup.acquire.domain.model.MerchantNotificationWakeReceipt;
import io.github.loadup.acquire.infrastructure.converter.MerchantNotificationWakeReceiptStorageConverter;
import io.github.loadup.acquire.infrastructure.dataobject.MerchantNotificationWakeReceiptDO;
import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.commons.dto.PageQuery;
import io.github.loadup.commons.error.CommonException;
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
public class MerchantNotificationWakeGatewayImpl implements MerchantNotificationWakeGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final MerchantNotificationWakeReceiptStorageConverter converter;

    public MerchantNotificationWakeGatewayImpl(
            NamedParameterJdbcTemplate jdbc, MerchantNotificationWakeReceiptStorageConverter converter, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.converter = converter;
    }

    @Override
    public Optional<MerchantNotificationWakeReceipt> find(String scope, String requestKey) {
        var args = new MapSqlParameterSource().addValue("caller", scope).addValue("key", requestKey);
        var row =
                jdbc
                        .query("""
                SELECT id, created_at, updated_at, delivery_id, event_id, request_key, caller_scope_hash, principal_type, principal_id, reason, request_digest, result, attempt_count, delivery_version, native_task_id, accepted_at
                FROM merchant_notification_wake_receipt WHERE deleted = FALSE
                    AND caller_scope_hash = :caller AND request_key = :key
                """, args, BeanPropertyRowMapper.newInstance(MerchantNotificationWakeReceiptDO.class))
                        .stream()
                        .findFirst()
                        .orElse(null);
        return Optional.ofNullable(row).map(this::decode);
    }

    @Override
    public void insert(MerchantNotificationWakeReceipt value) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new CommonException(INVALID_STATE);
        }
        if (jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(
                        jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
        var row = converter.toDO(value);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO merchant_notification_wake_receipt (id, created_at, updated_at, deleted, delivery_id, event_id, request_key, caller_scope_hash, principal_type, principal_id, reason, request_digest, result, attempt_count, delivery_version, native_task_id, accepted_at)
                VALUES (:id, :createdAt, :updatedAt, FALSE, :deliveryId, :eventId, :requestKey, :callerScopeHash, :principalType, :principalId, :reason, :requestDigest, :result, :attemptCount, :deliveryVersion, :nativeTaskId, :acceptedAt)
                ON CONFLICT (caller_scope_hash, request_key) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) {
            throw new AcquireWriteConflictException("merchant_notification_wake_receipt business key already exists");
        }
        if (count != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    @Override
    public PageResult<MerchantNotificationWakeReceipt> page(String deliveryId, int page, int size) {
        PageQuery.normalized(page, size, INVALID_PAGE);
        var args = new MapSqlParameterSource()
                .addValue("subject", deliveryId)
                .addValue("limit", size)
                .addValue("offset", (long) (page - 1) * size);
        var total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM merchant_notification_wake_receipt WHERE 1=1 "
                        + "AND deleted = FALSE AND delivery_id = :subject",
                args,
                Long.class);
        var rows = jdbc.query("""
                SELECT id, created_at, updated_at, delivery_id, event_id, request_key, caller_scope_hash, principal_type, principal_id, reason, request_digest, result, attempt_count, delivery_version, native_task_id, accepted_at
                FROM merchant_notification_wake_receipt WHERE deleted = FALSE AND delivery_id = :subject
                ORDER BY accepted_at DESC, id DESC LIMIT :limit OFFSET :offset
                """, args, BeanPropertyRowMapper.newInstance(MerchantNotificationWakeReceiptDO.class));
        return PageResult.of(rows.stream().map(this::decode).toList(), total, page, size);
    }

    private MerchantNotificationWakeReceipt decode(MerchantNotificationWakeReceiptDO row) {
        try {
            return converter.toDomain(row);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
