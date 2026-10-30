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
import io.github.loadup.pay.acquire.app.model.OrderSnapshotFacts;
import io.github.loadup.pay.acquire.app.port.OrderSnapshotReader;
import io.github.loadup.pay.acquire.infrastructure.converter.OrderSnapshotStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.dataobject.AcquireOrderDO;
import io.github.loadup.components.database.tenant.TenantContext;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class OrderSnapshotReaderImpl implements OrderSnapshotReader {
    private final NamedParameterJdbcTemplate jdbc;
    private final OrderSnapshotStorageConverter converter;

    public OrderSnapshotReaderImpl(NamedParameterJdbcTemplate jdbc, OrderSnapshotStorageConverter converter) {
        this.jdbc = jdbc;
        this.converter = converter;
    }

    @Override
    public OrderSnapshotFacts lockCurrent(String tenant, String orderId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", orderId);
        var row = jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, payee_merchant_id, merchant_order_no, admission_id,
                       contract_snapshot_id, subject, currency, amount_minor, paid_amount_minor, payment_reserved_minor,
                       successful_payment_count, reserved_payment_count, state, admitted_at, order_due_at, deadline_version,
                       close_epoch, paid_at, closed_at, row_version
                FROM acquire_order WHERE tenant_id = :tenant AND id = :id AND deleted = FALSE FOR UPDATE
                """, args, BeanPropertyRowMapper.newInstance(AcquireOrderDO.class)).stream().findFirst().orElse(null);
        if (row == null) throw new CommonException(SNAPSHOT_INVALID);
        try {
            return converter.toFacts(row);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
