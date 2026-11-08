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
package io.github.loadup.payment.infrastructure.repository;

import static io.github.loadup.contracts.payment.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.contracts.payment.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.contracts.payment.PaymentResultCodeEnum.SNAPSHOT_INVALID;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.payment.app.port.PaymentWriteConflictException;
import io.github.loadup.payment.domain.gateway.PaymentRecoveryRetryGateway;
import io.github.loadup.payment.domain.model.PaymentRecoveryRetryReceipt;
import io.github.loadup.payment.infrastructure.converter.PaymentRecoveryRetryStorageConverter;
import io.github.loadup.payment.infrastructure.dataobject.PaymentRecoveryRetryReceiptDO;
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
public class PaymentRecoveryRetryGatewayImpl implements PaymentRecoveryRetryGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final PaymentRecoveryRetryStorageConverter converter;
    private final Clock clock;

    public PaymentRecoveryRetryGatewayImpl(
            NamedParameterJdbcTemplate jdbc, PaymentRecoveryRetryStorageConverter converter, Clock clock) {
        this.jdbc = jdbc;
        this.converter = converter;
        this.clock = clock;
    }

    @Override
    public Optional<PaymentRecoveryRetryReceipt> find(String callerScopeHash, String requestKey) {
        var args =
                new MapSqlParameterSource().addValue("caller", callerScopeHash).addValue("key", requestKey);
        var row = jdbc.query("""
                SELECT id, created_at, updated_at, request_key, caller_scope_hash, caller_service_id,
                       request_digest, kind, subject_id, generation, order_id, native_task_id, expected_attempts,
                       expected_next_execute_at, reason, result, accepted_at
                FROM payment_recovery_retry_receipt WHERE caller_scope_hash = :caller
                    AND request_key = :key AND deleted = FALSE
                """, args, BeanPropertyRowMapper.newInstance(PaymentRecoveryRetryReceiptDO.class)).stream()
                .findFirst()
                .orElse(null);
        try {
            return Optional.ofNullable(row).map(converter::toDomain);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(PaymentRecoveryRetryReceipt receipt) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(
                        jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
        var row = converter.toDO(receipt);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO payment_recovery_retry_receipt (id, created_at, updated_at, deleted, request_key,
                    caller_scope_hash, caller_service_id, request_digest, kind, subject_id, generation, order_id,
                    native_task_id, expected_attempts, expected_next_execute_at, reason, result, accepted_at)
                VALUES (:id, :createdAt, :updatedAt, FALSE, :requestKey, :callerScopeHash, :callerServiceId,
                    :requestDigest, :kind, :subjectId, :generation, :orderId, :nativeTaskId, :expectedAttempts,
                    :expectedNextExecuteAt, :reason, :result, :acceptedAt)
                ON CONFLICT (caller_scope_hash, request_key) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) {
            throw new PaymentWriteConflictException("Recovery retry request key already exists");
        }
        if (count != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }
}
