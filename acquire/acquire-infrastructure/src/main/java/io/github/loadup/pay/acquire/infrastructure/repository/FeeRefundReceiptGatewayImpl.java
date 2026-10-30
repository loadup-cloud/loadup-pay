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
import io.github.loadup.pay.acquire.domain.gateway.FeeRefundReceiptGateway;
import io.github.loadup.pay.acquire.domain.model.FeeRefundReceipt;
import io.github.loadup.pay.acquire.infrastructure.converter.FeeRefundReceiptStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.dataobject.FeeRefundReceiptDO;
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
public class FeeRefundReceiptGatewayImpl implements FeeRefundReceiptGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final FeeRefundReceiptStorageConverter converter;

    public FeeRefundReceiptGatewayImpl(NamedParameterJdbcTemplate jdbc, FeeRefundReceiptStorageConverter converter, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.converter = converter;
    }

    @Override
    public Optional<FeeRefundReceipt> find(String tenant, String id) {
        scope(tenant);
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("delivery", id);
        var row = jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, delivery_id, business_event_id, order_id,
                       source_version, applied_version, request_digest, caller_service_id, accepted_at
                FROM fee_refund_receipt WHERE tenant_id = :tenant AND delivery_id = :delivery AND deleted = FALSE
                """, args, BeanPropertyRowMapper.newInstance(FeeRefundReceiptDO.class)).stream().findFirst().orElse(null);
        try {
            return Optional.ofNullable(row).map(converter::toDomain);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, FeeRefundReceipt value) {
        scope(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new CommonException(INVALID_STATE);
        }
        if (jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
        var row = converter.toDO(tenant, value);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO fee_refund_receipt (id, tenant_id, created_at, updated_at, deleted, delivery_id, business_event_id,
                    order_id, source_version, applied_version, request_digest, caller_service_id, accepted_at)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :deliveryId, :businessEventId, :orderId,
                    :sourceVersion, :appliedVersion, :requestDigest, :callerServiceId, :acceptedAt)
                ON CONFLICT (tenant_id, delivery_id) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) {
            throw new AcquireWriteConflictException("Fee refund delivery already exists");
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
