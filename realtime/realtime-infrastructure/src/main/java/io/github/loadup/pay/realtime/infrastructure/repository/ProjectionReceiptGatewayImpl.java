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
package io.github.loadup.pay.realtime.infrastructure.repository;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.realtime.domain.gateway.ProjectionReceiptGateway;
import io.github.loadup.pay.realtime.domain.gateway.ProjectionWriteRaceException;
import io.github.loadup.pay.realtime.domain.model.ProjectionReceipt;
import io.github.loadup.pay.realtime.infrastructure.converter.TradeStorageConverter;
import io.github.loadup.pay.realtime.infrastructure.dataobject.ProjectionReceiptDO;
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

import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.QUERY_FORBIDDEN;
import static io.github.loadup.pay.realtime.client.error.RealtimeResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class ProjectionReceiptGatewayImpl implements ProjectionReceiptGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final TradeStorageConverter converter;
    private final Clock clock;

    public ProjectionReceiptGatewayImpl(NamedParameterJdbcTemplate jdbc, TradeStorageConverter converter,
                                        @Qualifier("databaseClock") Clock clock) {
        this.jdbc = jdbc;
        this.converter = converter;
        this.clock = clock;
    }

    @Override
    public Optional<ProjectionReceipt> find(String tenant, String producer, String delivery) {
        scope(tenant);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("producer", producer).addValue("delivery", delivery);
        var row = jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, delivery_id, business_event_id, producer, aggregate_id,
                       source_version, projected_version, disposition, request_digest, caller_service_id, accepted_at
                FROM trade_projection_receipt WHERE tenant_id = :tenant AND producer = :producer
                    AND delivery_id = :delivery AND deleted = FALSE
                """, args, BeanPropertyRowMapper.newInstance(ProjectionReceiptDO.class)).stream().findFirst().orElse(null);
        try {
            return Optional.ofNullable(row).map(converter::toReceipt);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, ProjectionReceipt receipt) {
        scope(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
        var row = converter.toDO(tenant, receipt);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO trade_projection_receipt (id, tenant_id, created_at, updated_at, deleted, delivery_id,
                    business_event_id, producer, aggregate_id, source_version, projected_version, disposition,
                    request_digest, caller_service_id, accepted_at)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :deliveryId, :businessEventId, :producer,
                    :aggregateId, :sourceVersion, :projectedVersion, :disposition, :requestDigest, :callerServiceId, :acceptedAt)
                ON CONFLICT (tenant_id, producer, delivery_id) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) throw new ProjectionWriteRaceException("Projection delivery already exists");
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private void scope(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(QUERY_FORBIDDEN);
    }
}
