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
import io.github.loadup.pay.acquire.app.port.AcquireWriteConflictException;
import io.github.loadup.pay.acquire.domain.gateway.RefundNotificationReceiptGateway;
import io.github.loadup.pay.acquire.domain.model.RefundNotificationReceipt;
import io.github.loadup.pay.acquire.infrastructure.converter.RefundNotificationReceiptStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.dataobject.RefundNotificationReceiptDO;
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
public class RefundNotificationReceiptGatewayImpl implements RefundNotificationReceiptGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final RefundNotificationReceiptStorageConverter converter;

    public RefundNotificationReceiptGatewayImpl(NamedParameterJdbcTemplate jdbc, RefundNotificationReceiptStorageConverter converter, @Qualifier("acquireClock") Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.converter = converter;
    }

    @Override
    public Optional<RefundNotificationReceipt> find(String tenant, String id) {
        scope(tenant);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("delivery", id);
        var row = jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, delivery_id, business_event_id, aggregate_id, source_version, order_id, payee_merchant_id, request_digest, caller_service_id, merchant_event_id, disposition, accepted_at
                FROM refund_notification_receipt WHERE tenant_id = :tenant AND deleted = FALSE AND delivery_id = :delivery
                """, args, BeanPropertyRowMapper.newInstance(RefundNotificationReceiptDO.class)).stream().findFirst().orElse(null);
        try {
            return Optional.ofNullable(row).map(converter::toDomain);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, RefundNotificationReceipt value) {
        scope(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        if (jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
        var row = converter.toDO(tenant, value);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO refund_notification_receipt (id, tenant_id, created_at, updated_at, deleted, delivery_id, business_event_id, aggregate_id, source_version, order_id, payee_merchant_id, request_digest, caller_service_id, merchant_event_id, disposition, accepted_at)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :deliveryId, :businessEventId, :aggregateId, :sourceVersion, :orderId, :payeeMerchantId, :requestDigest, :callerServiceId, :merchantEventId, :disposition, :acceptedAt)
                ON CONFLICT (tenant_id, delivery_id) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0)
            throw new AcquireWriteConflictException("refund_notification_receipt business key already exists");
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private void scope(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }
}
