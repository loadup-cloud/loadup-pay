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
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_PAGE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.commons.dto.PageQuery;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.acquire.app.port.AcquireWriteConflictException;
import io.github.loadup.pay.acquire.domain.gateway.CheckoutSessionRevocationGateway;
import io.github.loadup.pay.acquire.domain.model.CheckoutSessionRevocationReceipt;
import io.github.loadup.pay.acquire.infrastructure.converter.CheckoutSessionRevocationReceiptStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.dataobject.CheckoutSessionRevocationReceiptDO;
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
public class CheckoutSessionRevocationGatewayImpl implements CheckoutSessionRevocationGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final CheckoutSessionRevocationReceiptStorageConverter converter;

    public CheckoutSessionRevocationGatewayImpl(
            NamedParameterJdbcTemplate jdbc, CheckoutSessionRevocationReceiptStorageConverter converter, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.converter = converter;
    }

    @Override
    public Optional<CheckoutSessionRevocationReceipt> find(String callerScopeHash, String requestKey) {
        var args =
                new MapSqlParameterSource().addValue("caller", callerScopeHash).addValue("key", requestKey);
        var row =
                jdbc
                        .query("""
                SELECT id, created_at, updated_at, session_id, order_id, request_key, caller_scope_hash, request_digest, reason, newly_revoked, before_version, session_version, revoked_at, accepted_at
                FROM checkout_session_revocation_receipt WHERE deleted = FALSE
                    AND caller_scope_hash = :caller AND request_key = :key
                """, args, BeanPropertyRowMapper.newInstance(CheckoutSessionRevocationReceiptDO.class))
                        .stream()
                        .findFirst()
                        .orElse(null);
        return Optional.ofNullable(row).map(this::decode);
    }

    @Override
    public void insert(CheckoutSessionRevocationReceipt value) {
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
                INSERT INTO checkout_session_revocation_receipt (id, created_at, updated_at, deleted, session_id, order_id, request_key, caller_scope_hash, request_digest, reason, newly_revoked, before_version, session_version, revoked_at, accepted_at)
                VALUES (:id, :createdAt, :updatedAt, FALSE, :sessionId, :orderId, :requestKey, :callerScopeHash, :requestDigest, :reason, :newlyRevoked, :beforeVersion, :sessionVersion, :revokedAt, :acceptedAt)
                ON CONFLICT (caller_scope_hash, request_key) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) {
            throw new AcquireWriteConflictException("checkout_session_revocation_receipt business key already exists");
        }
        if (count != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    @Override
    public PageResult<CheckoutSessionRevocationReceipt> page(String sessionId, int page, int size) {
        PageQuery.normalized(page, size, INVALID_PAGE);
        var args = new MapSqlParameterSource()
                .addValue("subject", sessionId)
                .addValue("limit", size)
                .addValue("offset", (long) (page - 1) * size);
        var total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM checkout_session_revocation_receipt WHERE 1=1 "
                        + "AND deleted = FALSE AND session_id = :subject",
                args,
                Long.class);
        var rows = jdbc.query("""
                SELECT id, created_at, updated_at, session_id, order_id, request_key, caller_scope_hash, request_digest, reason, newly_revoked, before_version, session_version, revoked_at, accepted_at
                FROM checkout_session_revocation_receipt WHERE deleted = FALSE AND session_id = :subject
                ORDER BY accepted_at DESC, id DESC LIMIT :limit OFFSET :offset
                """, args, BeanPropertyRowMapper.newInstance(CheckoutSessionRevocationReceiptDO.class));
        return PageResult.of(rows.stream().map(this::decode).toList(), total, page, size);
    }

    private CheckoutSessionRevocationReceipt decode(CheckoutSessionRevocationReceiptDO row) {
        try {
            return converter.toDomain(row);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
