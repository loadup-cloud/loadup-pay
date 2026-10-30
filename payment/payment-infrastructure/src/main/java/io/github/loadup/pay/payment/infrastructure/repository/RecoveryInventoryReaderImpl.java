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
package io.github.loadup.pay.payment.infrastructure.repository;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.payment.app.model.RecoveryInventoryCell;
import io.github.loadup.pay.payment.app.port.RecoveryInventoryReader;
import io.github.loadup.pay.payment.infrastructure.converter.RecoveryInventoryConverter;
import io.github.loadup.pay.payment.infrastructure.model.RecoveryInventoryAggregate;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class RecoveryInventoryReaderImpl implements RecoveryInventoryReader {
    private final NamedParameterJdbcTemplate jdbc;
    private final RecoveryInventoryConverter converter;

    public RecoveryInventoryReaderImpl(NamedParameterJdbcTemplate jdbc, RecoveryInventoryConverter converter) {
        this.jdbc = jdbc;
        this.converter = converter;
    }

    @Override
    public List<RecoveryInventoryCell> read(String tenant) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || !TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(TransactionDefinition.ISOLATION_REPEATABLE_READ)
                .equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        var args = new MapSqlParameterSource();
        var mapper = BeanPropertyRowMapper.newInstance(RecoveryInventoryAggregate.class);
        var collection = jdbc.query("""
                SELECT COUNT(id) AS row_count, SUM(amount_minor) AS amount_minor, MIN(first_unknown_at) AS oldest_at
                FROM payment_order WHERE deleted = FALSE AND state = 'UNKNOWN'
                """, args, mapper);
        var refund = jdbc.query("""
                SELECT COUNT(id) AS row_count, SUM(amount_minor) AS amount_minor, MIN(first_unknown_at) AS oldest_at
                FROM refund_order WHERE deleted = FALSE AND state = 'UNKNOWN'
                """, args, mapper);
        return List.of(cell(RecoveryInventoryCell.Kind.PAYMENT, collection), cell(RecoveryInventoryCell.Kind.REFUND, refund));
    }

    private RecoveryInventoryCell cell(RecoveryInventoryCell.Kind kind, List<RecoveryInventoryAggregate> rows) {
        try {
            if (rows == null || rows.size() != 1 || rows.getFirst().getRowCount() == null) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            return converter.toCell(kind, rows.getFirst());
        } catch (CommonException failure) {
            throw failure;
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
