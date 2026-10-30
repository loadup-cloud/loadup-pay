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
import io.github.loadup.pay.payment.app.model.UnknownRecoveryEntry;
import io.github.loadup.pay.payment.app.model.UnknownRecoveryWindow;
import io.github.loadup.pay.payment.app.port.UnknownRecoveryReader;
import io.github.loadup.pay.payment.domain.model.PaymentRecoveryKind;
import io.github.loadup.pay.payment.infrastructure.converter.UnknownRecoveryStorageConverter;
import io.github.loadup.pay.payment.infrastructure.model.UnknownRecoveryProjection;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class UnknownRecoveryReaderImpl implements UnknownRecoveryReader {
    private final NamedParameterJdbcTemplate jdbc;
    private final UnknownRecoveryStorageConverter converter;

    public UnknownRecoveryReaderImpl(NamedParameterJdbcTemplate jdbc, UnknownRecoveryStorageConverter converter) {
        this.jdbc = jdbc;
        this.converter = converter;
    }

    @Override
    public List<UnknownRecoveryEntry> list(String tenant, PaymentRecoveryKind kind, UnknownRecoveryWindow window) {
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
        if (kind == null || window == null) {
            throw new CommonException(INVALID_ARGUMENT);
        }
        var args = new MapSqlParameterSource().addValue("limit", window.limit() + 1);
        String base = switch (kind) {
            case COLLECTION -> """
                    SELECT p.id AS record_id, p.id AS subject_id, p.order_id, p.payee_merchant_id,
                           p.amount_minor, p.currency, p.first_unknown_at, p.row_version,
                           c.generation AS recovery_generation, c.completed AS recovery_completed
                    FROM payment_order p LEFT JOIN payment_recovery_cursor c
                      ON c.id = p.id AND c.deleted = FALSE
                    WHERE p.deleted = FALSE AND p.state = 'UNKNOWN'
                    """;
            case REFUND_REQUEST -> """
                    SELECT p.id AS record_id, p.refund_request_id AS subject_id, r.order_id, r.payee_merchant_id,
                           p.amount_minor, p.currency, p.first_unknown_at, p.row_version,
                           c.generation AS recovery_generation, c.completed AS recovery_completed
                    FROM refund_order p LEFT JOIN refund_request r
                      ON r.id = p.refund_request_id AND r.deleted = FALSE
                    LEFT JOIN refund_recovery_cursor c
                      ON c.id = p.refund_request_id AND c.deleted = FALSE
                    WHERE p.deleted = FALSE AND p.state = 'UNKNOWN'
                    """;
        };
        var sql = new StringBuilder(base);
        if (window.cutoff() != null) {
            sql.append(" AND p.first_unknown_at <= :cutoff");
            args.addValue("cutoff", window.cutoff());
        }
        if (window.afterFirstUnknownAt() != null) {
            sql.append(" AND (p.first_unknown_at > :after OR (p.first_unknown_at = :after AND p.id > :afterId))");
            args.addValue("after", window.afterFirstUnknownAt()).addValue("afterId", window.afterRecordId());
        }
        sql.append(" ORDER BY p.first_unknown_at ASC, p.id ASC LIMIT :limit");
        var rows = jdbc.query(sql.toString(), args, BeanPropertyRowMapper.newInstance(UnknownRecoveryProjection.class));
        try {
            if (rows == null) {
                throw new CommonException(SNAPSHOT_INVALID);
            }
            return rows.stream().map(row -> converter.toEntry(kind, row)).toList();
        } catch (CommonException failure) {
            throw failure;
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

}
