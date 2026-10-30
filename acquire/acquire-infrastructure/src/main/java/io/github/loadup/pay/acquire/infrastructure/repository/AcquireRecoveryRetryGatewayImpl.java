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
import io.github.loadup.pay.acquire.domain.gateway.AcquireRecoveryRetryGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireRecoveryRetryReceipt;
import io.github.loadup.pay.acquire.infrastructure.converter.AcquireRecoveryRetryStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.dataobject.AcquireRecoveryRetryReceiptDO;
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
public class AcquireRecoveryRetryGatewayImpl implements AcquireRecoveryRetryGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final AcquireRecoveryRetryStorageConverter converter;

    public AcquireRecoveryRetryGatewayImpl(NamedParameterJdbcTemplate jdbc, AcquireRecoveryRetryStorageConverter converter, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.converter = converter;
    }

    @Override
    public Optional<AcquireRecoveryRetryReceipt> find(String tenant, String callerScopeHash, String requestKey) {
        scope(tenant);
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("caller", callerScopeHash).addValue("key", requestKey);
        var row = jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, request_key, caller_scope_hash, caller_service_id, request_digest, kind, subject_id, generation, order_id, native_task_id, expected_attempts, expected_next_execute_at, reason, result, accepted_at
                FROM acquire_recovery_retry_receipt WHERE tenant_id = :tenant AND deleted = FALSE AND caller_scope_hash = :caller AND request_key = :key
                """, args, BeanPropertyRowMapper.newInstance(AcquireRecoveryRetryReceiptDO.class)).stream().findFirst().orElse(null);
        try {
            return Optional.ofNullable(row).map(converter::toDomain);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, AcquireRecoveryRetryReceipt receipt) {
        scope(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new CommonException(INVALID_STATE);
        }
        if (jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
        var row = converter.toDO(tenant, receipt);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO acquire_recovery_retry_receipt (id, tenant_id, created_at, updated_at, deleted, request_key, caller_scope_hash, caller_service_id, request_digest, kind, subject_id, generation, order_id, native_task_id, expected_attempts, expected_next_execute_at, reason, result, accepted_at)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :requestKey, :callerScopeHash, :callerServiceId, :requestDigest, :kind, :subjectId, :generation, :orderId, :nativeTaskId, :expectedAttempts, :expectedNextExecuteAt, :reason, :result, :acceptedAt)
                ON CONFLICT (tenant_id, caller_scope_hash, request_key) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) {
            throw new AcquireWriteConflictException("acquire_recovery_retry_receipt business key already exists");
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
}
