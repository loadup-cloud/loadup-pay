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
package io.github.loadup.pay.limitcenter.infrastructure.repository;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.limitcenter.domain.gateway.LimitRefundGateway;
import io.github.loadup.pay.limitcenter.domain.gateway.LimitWriteRaceException;
import io.github.loadup.pay.limitcenter.domain.model.LimitRefundReceipt;
import io.github.loadup.pay.limitcenter.infrastructure.converter.LimitRefundStorageConverter;
import io.github.loadup.pay.limitcenter.infrastructure.dataobject.LimitRefundReceiptDO;
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

import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class LimitRefundGatewayImpl implements LimitRefundGateway {
    private static final String COLUMNS = "id, tenant_id, created_at, updated_at, caller_service_id, operation_id, "
            + "payment_id, refund_id, refund_version, business_event_id, refund_evidence_id, request_digest, "
            + "cumulative_payment_refunded_minor, verified_payment_refunded_minor, verified_payment_refund_version, "
            + "result, restored_amount_minor, restored_payment_count, accepted_at";
    private final NamedParameterJdbcTemplate jdbc;
    private final LimitRefundStorageConverter converter;
    private final Clock clock;

    public LimitRefundGatewayImpl(NamedParameterJdbcTemplate jdbc, LimitRefundStorageConverter converter,
                                  @Qualifier("databaseClock") Clock clock) {
        this.jdbc = jdbc;
        this.converter = converter;
        this.clock = clock;
    }

    @Override
    public Optional<LimitRefundReceipt> find(String tenant, String refundId) {
        scope(tenant);
        var args = new MapSqlParameterSource("tenant", tenant).addValue("refund", refundId);
        try {
            return jdbc.query("SELECT " + COLUMNS + " FROM limit_refund_receipt WHERE tenant_id = :tenant "
                            + "AND deleted = FALSE AND refund_id = :refund", args,
                    BeanPropertyRowMapper.newInstance(LimitRefundReceiptDO.class)).stream().findFirst().map(converter::toDomain);
        } catch (RuntimeException corrupt) {
            if (corrupt instanceof org.springframework.dao.DataAccessException) throw corrupt;
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, LimitRefundReceipt value) {
        scope(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
        var row = converter.toDO(tenant, value);
        LocalDateTime now = LocalDateTime.now(clock);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        int inserted = jdbc.update("""
                INSERT INTO limit_refund_receipt (id, tenant_id, created_at, updated_at, deleted, caller_service_id,
                    operation_id, payment_id, refund_id, refund_version, business_event_id, refund_evidence_id,
                    request_digest, cumulative_payment_refunded_minor, verified_payment_refunded_minor,
                    verified_payment_refund_version, result, restored_amount_minor, restored_payment_count, accepted_at)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :callerServiceId, :operationId, :paymentId,
                    :refundId, :refundVersion, :businessEventId, :refundEvidenceId, :requestDigest,
                    :cumulativePaymentRefundedMinor, :verifiedPaymentRefundedMinor, :verifiedPaymentRefundVersion,
                    :result, :restoredAmountMinor, :restoredPaymentCount, :acceptedAt)
                ON CONFLICT (tenant_id, refund_id) DO NOTHING
                """, new BeanPropertySqlParameterSource(row));
        if (inserted == 0) throw new LimitWriteRaceException("Refund receipt business key already exists");
        if (inserted != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private void scope(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }
}
