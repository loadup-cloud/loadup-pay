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
import io.github.loadup.pay.channel.api.model.PreparedChannelRefund;
import io.github.loadup.pay.payment.domain.gateway.RefundCallbackTargetGateway;
import io.github.loadup.pay.payment.infrastructure.converter.RefundCallbackStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentRefundCapacityDO;
import io.github.loadup.pay.payment.infrastructure.dataobject.RefundOrderDO;
import io.github.loadup.components.database.tenant.TenantContext;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.Optional;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Bounded immutable refund lookup; no current route selection or local locks around channel calls.
 */
@Repository
public class RefundCallbackTargetGatewayImpl implements RefundCallbackTargetGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final RefundCallbackStorageConverter converter;

    public RefundCallbackTargetGatewayImpl(NamedParameterJdbcTemplate jdbc, RefundCallbackStorageConverter converter) {
        this.jdbc = jdbc;
        this.converter = converter;
    }

    @Override
    public Optional<PreparedChannelRefund> find(String tenant, String revision, String orderNo, String refundNo) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        var childArgs = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("revision", revision)
                .addValue("orderNo", orderNo).addValue("refundNo", refundNo);
        var rows = jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, refund_request_id, payment_id, original_attempt_id,
                       binding_revision_id, channel, account_scope_hash, original_channel_request_no, original_channel_transaction_no,
                       original_succeeded_at, submission_started_at, channel_refund_no, channel_refund_transaction_no, amount_minor,
                       currency, state, first_unknown_at, terminal_evidence_id, next_query_at, query_sequence, succeeded_at, row_version FROM refund_order
                WHERE tenant_id = :tenant AND deleted = FALSE AND binding_revision_id = :revision
                    AND original_channel_request_no = :orderNo AND channel_refund_no = :refundNo LIMIT 2
                """, childArgs, BeanPropertyRowMapper.newInstance(RefundOrderDO.class));
        if (rows.isEmpty()) return Optional.empty();
        if (rows.size() != 1) throw new CommonException(SNAPSHOT_INVALID);
        var row = rows.getFirst();
        if (row.getSubmissionStartedAt() == null || "INIT".equals(row.getState()))
            throw new CommonException(INVALID_STATE);
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("payment", row.getPaymentId());
        var capacity = jdbc.query("""
                SELECT id, payment_id, succeeded_at, succeeded_minor, currency FROM payment_refund_capacity
                WHERE tenant_id = :tenant AND payment_id = :payment AND deleted = FALSE
                """, args, BeanPropertyRowMapper.newInstance(PaymentRefundCapacityDO.class)).stream().findFirst().orElse(null);
        if (capacity == null || !Objects.equals(capacity.getSucceededAt(), row.getOriginalSucceededAt())
                || !Objects.equals(capacity.getCurrency(), row.getCurrency()))
            throw new CommonException(SNAPSHOT_INVALID);
        try {
            return Optional.of(converter.toPrepared(row, capacity, converter.toIdentity(row)));
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
