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
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.payment.app.port.PaymentWriteConflictException;
import io.github.loadup.pay.payment.domain.gateway.RefundSummaryGateway;
import io.github.loadup.pay.payment.domain.model.OrderRefundSummary;
import io.github.loadup.pay.payment.infrastructure.converter.RefundProjectionStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.OrderRefundSummaryDO;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class RefundSummaryGatewayImpl implements RefundSummaryGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final RefundProjectionStorageConverter converter;
    private final IdGenerator ids;
    private final Clock clock;

    public RefundSummaryGatewayImpl(NamedParameterJdbcTemplate jdbc, RefundProjectionStorageConverter converter, IdGenerator ids,
                                    Clock clock) {
        this.jdbc = jdbc;
        this.converter = converter;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public Optional<OrderRefundSummary> lock(String tenant, String orderId) {
        writable(tenant);
        var args = new MapSqlParameterSource().addValue("order", orderId);
        var row = jdbc.query("""
                SELECT id, created_at, updated_at, order_id, payee_merchant_id, order_amount_minor,
                       currency, refunded_minor, reserved_minor, row_version FROM order_refund_summary
                WHERE order_id = :order AND deleted = FALSE FOR UPDATE
                """, args, BeanPropertyRowMapper.newInstance(OrderRefundSummaryDO.class)).stream().findFirst().orElse(null);
        if (row == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(converter.toSummary(row));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, OrderRefundSummary value) {
        writable(tenant);
        if (value.version() != 1 || value.refundedMinor() != 0 || value.reservedMinor() <= 0) {
            throw new CommonException(INVALID_STATE);
        }
        var row = converter.toSummaryDO(tenant, ids.generate(), value);
        var now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO order_refund_summary (id, created_at, updated_at, deleted, order_id,
                    payee_merchant_id, order_amount_minor, currency, refunded_minor, reserved_minor, row_version)
                VALUES (:id, :createdAt, :updatedAt, FALSE, :orderId, :payeeMerchantId, :orderAmountMinor,
                    :currency, :refundedMinor, :reservedMinor, :rowVersion)
                ON CONFLICT (order_id) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (count == 0) {
            throw new PaymentWriteConflictException("Refund summary business key already exists");
        }
        if (count != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    @Override
    public void update(String tenant, OrderRefundSummary before, OrderRefundSummary value) {
        writable(tenant);
        if (!before.orderId().equals(value.orderId()) || !before.payeeMerchantId().equals(value.payeeMerchantId())
                || !before.orderAmount().equals(value.orderAmount()) || value.version() != Math.incrementExact(before.version())
                || value.refundedMinor() < before.refundedMinor()) {
            throw new CommonException(INVALID_STATE);
        }
        var args = new MapSqlParameterSource().addValue("order", before.orderId())
                .addValue("version", value.version()).addValue("refunded", value.refundedMinor()).addValue("reserved", value.reservedMinor())
                .addValue("expectedVersion", before.version()).addValue("expectedRefunded", before.refundedMinor())
                .addValue("expectedReserved", before.reservedMinor()).addValue("updated", LocalDateTime.now(clock));
        if (jdbc.update("""
                UPDATE order_refund_summary SET row_version = :version, refunded_minor = :refunded,
                    reserved_minor = :reserved, updated_at = :updated WHERE order_id = :order
                    AND row_version = :expectedVersion AND refunded_minor = :expectedRefunded
                    AND reserved_minor = :expectedReserved AND deleted = FALSE
                """, args) != 1) {
            throw new CommonException(CONCURRENT_UPDATE);
        }
    }

    private void writable(String tenant) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource())) {
            throw new CommonException(INVALID_STATE);
        }
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
    }
}
