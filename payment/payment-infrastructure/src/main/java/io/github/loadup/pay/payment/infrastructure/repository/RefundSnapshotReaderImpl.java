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
import io.github.loadup.pay.payment.app.model.RefundSnapshotFacts;
import io.github.loadup.pay.payment.app.model.RefundSummaryFacts;
import io.github.loadup.pay.payment.app.port.RefundSnapshotReader;
import io.github.loadup.pay.payment.infrastructure.converter.RefundProjectionStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.OrderRefundSummaryDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundOrderDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundRequestDO;
import io.github.loadup.components.database.tenant.TenantContext;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class RefundSnapshotReaderImpl implements RefundSnapshotReader {
    private final NamedParameterJdbcTemplate jdbc;
    private final RefundProjectionStorageConverter converter;

    public RefundSnapshotReaderImpl(NamedParameterJdbcTemplate jdbc,
                                    RefundProjectionStorageConverter converter) {
        this.jdbc = jdbc;
        this.converter = converter;
    }

    @Override
    public RefundSnapshotFacts child(String tenant, String refundId) {
        writable(tenant);
        var childArgs = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", refundId);
        var row = jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, refund_request_id, payment_id, original_attempt_id,
                       binding_revision_id, channel, account_scope_hash, original_channel_request_no, original_channel_transaction_no,
                       original_succeeded_at, submission_started_at, channel_refund_no, channel_refund_transaction_no, amount_minor,
                       currency, state, first_unknown_at, terminal_evidence_id, next_query_at, query_sequence, succeeded_at, row_version
                FROM refund_order WHERE tenant_id = :tenant AND id = :id AND deleted = FALSE FOR UPDATE
                """, childArgs, BeanPropertyRowMapper.newInstance(RefundOrderDO.class)).stream().findFirst().orElse(null);
        if (row == null) throw new CommonException(SNAPSHOT_INVALID);
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", row.getRefundRequestId());
        var header = jdbc.query("""
                SELECT id, order_id, payee_merchant_id, currency FROM refund_request
                WHERE tenant_id = :tenant AND id = :id AND deleted = FALSE
                """, args, BeanPropertyRowMapper.newInstance(RefundRequestDO.class)).stream().findFirst().orElse(null);
        if (header == null || !header.getCurrency().equals(row.getCurrency()))
            throw new CommonException(SNAPSHOT_INVALID);
        try {
            return converter.toFacts(row, header);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public RefundSummaryFacts summary(String tenant, String orderId) {
        writable(tenant);
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("order", orderId);
        var row = jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, order_id, payee_merchant_id, order_amount_minor,
                       currency, refunded_minor, reserved_minor, row_version FROM order_refund_summary
                WHERE tenant_id = :tenant AND order_id = :order AND deleted = FALSE FOR UPDATE
                """, args, BeanPropertyRowMapper.newInstance(OrderRefundSummaryDO.class)).stream().findFirst().orElse(null);
        if (row == null) throw new CommonException(SNAPSHOT_INVALID);
        try {
            return converter.toFacts(row, converter.toSummary(row));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private void writable(String tenant) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }
}
