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
import io.github.loadup.pay.acquire.domain.gateway.PaymentEventReceiptGateway;
import io.github.loadup.pay.acquire.domain.model.PaymentEventReceipt;
import io.github.loadup.pay.acquire.infrastructure.converter.PaymentEventStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.dataobject.PaymentEventReceiptDO;
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
public class PaymentEventReceiptGatewayImpl implements PaymentEventReceiptGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final PaymentEventStorageConverter converter;

    public PaymentEventReceiptGatewayImpl(NamedParameterJdbcTemplate jdbc, PaymentEventStorageConverter converter, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.converter = converter;
    }

    @Override
    public Optional<PaymentEventReceipt> find(String tenant, String deliveryId) {
        scope(tenant);
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("delivery", deliveryId);
        var row = jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, delivery_id, business_event_id, payment_id, order_id,
                       allocation_id, payment_version, evidence_id, request_digest, caller_service_id, order_version, accepted_at
                FROM acquire_payment_event_receipt WHERE tenant_id = :tenant AND delivery_id = :delivery AND deleted = FALSE
                """, args, BeanPropertyRowMapper.newInstance(PaymentEventReceiptDO.class)).stream().findFirst().orElse(null);
        try {
            return Optional.ofNullable(row).map(converter::toDomain);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, PaymentEventReceipt receipt) {
        scope(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
        var row = converter.toDO(tenant, receipt);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO acquire_payment_event_receipt (id, tenant_id, created_at, updated_at, deleted, delivery_id,
                    business_event_id, payment_id, order_id, allocation_id, payment_version, evidence_id, request_digest,
                    caller_service_id, order_version, accepted_at)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :deliveryId, :businessEventId, :paymentId, :orderId,
                    :allocationId, :paymentVersion, :evidenceId, :requestDigest, :callerServiceId, :orderVersion, :acceptedAt)
                ON CONFLICT (tenant_id, delivery_id) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) {
            throw new AcquireWriteConflictException("Payment delivery already exists");
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
