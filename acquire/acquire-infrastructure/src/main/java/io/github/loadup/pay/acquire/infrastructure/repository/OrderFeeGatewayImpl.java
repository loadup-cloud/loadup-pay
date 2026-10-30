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
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.acquire.domain.gateway.OrderFeeGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrder;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.FeeState;
import io.github.loadup.pay.acquire.domain.model.OrderFee;
import io.github.loadup.pay.acquire.domain.model.OrderFeePolicy;
import io.github.loadup.pay.acquire.infrastructure.converter.FeeAdjustmentStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.converter.OrderFeeStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.dataobject.OrderFeeDO;
import io.github.loadup.components.database.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class OrderFeeGatewayImpl implements OrderFeeGateway {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final OrderFeeStorageConverter converter;
    private final FeeAdjustmentStorageConverter adjustmentConverter;
    private final IdGenerator ids;
    private final JsonMapper mapper;

    public OrderFeeGatewayImpl(NamedParameterJdbcTemplate jdbc, OrderFeeStorageConverter converter,
                               FeeAdjustmentStorageConverter adjustmentConverter, IdGenerator ids, JsonMapper mapper, @Qualifier("acquireClock") Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.converter = converter;
        this.adjustmentConverter = adjustmentConverter;
        this.ids = ids;
        this.mapper = mapper.rebuild().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    }

    @Override
    public void confirmPaid(String tenant, AcquireOrder paid, OrderFeePolicy expectedPolicy, LocalDateTime confirmedAt) {
        writable(tenant);
        if (paid.state() != AcquireOrderState.PAID || paid.paidAmountMinor() != paid.amount().getCent() || confirmedAt == null)
            throw new CommonException(INVALID_STATE);
        var row = lock(tenant, paid.id());
        var current = decode(row, paid, expectedPolicy);
        if (current.state() == FeeState.CONFIRMED) return;
        var confirmed = current.confirm();
        adjust(tenant, row, confirmed, "FEE_CONFIRMED", current.id(), confirmedAt);
        save(tenant, row, confirmed.acknowledgeAdjustment(), confirmedAt);
    }

    @Override
    public void applyRefundBasis(String tenant, AcquireOrder order, OrderFeePolicy expectedPolicy, long sourceVersion,
                                 long cumulativeMinor, String sourceEventId, LocalDateTime appliedAt) {
        writable(tenant);
        DomainChecks.id(sourceEventId);
        if (sourceVersion <= 0 || appliedAt == null) throw new CommonException(INVALID_ARGUMENT);
        var row = lock(tenant, order.id());
        var current = decode(row, order, expectedPolicy);
        var based = current.recordRefundBasis(sourceVersion, cumulativeMinor);
        if (based.equals(current)) return;
        adjust(tenant, row, based, "REFUND_SUMMARY:" + sourceVersion, sourceEventId, appliedAt);
        save(tenant, row, based.acknowledgeAdjustment(), row.getConfirmedAt());
    }

    private OrderFeeDO lock(String tenant, String order) {
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("order", order);
        var row = jdbc.query("""
                SELECT id, tenant_id, created_at, updated_at, order_id, fee_type, currency, base_amount_minor, fee_amount_minor,
                       fee_rule_snapshot, refund_basis_version, refund_basis_minor, adjusted_amount_minor, state, confirmed_at, row_version
                FROM order_fee WHERE tenant_id = :tenant AND order_id = :order AND fee_type = 'ORDER_AMOUNT' AND deleted = FALSE FOR UPDATE
                """, args, BeanPropertyRowMapper.newInstance(OrderFeeDO.class)).stream().findFirst().orElse(null);
        if (row == null) throw new CommonException(SNAPSHOT_INVALID);
        return row;
    }

    private OrderFee decode(OrderFeeDO row, AcquireOrder order, OrderFeePolicy expected) {
        try {
            var policy = mapper.readValue(row.getFeeRuleSnapshot(), OrderFeePolicy.class);
            var value = converter.toFee(row, policy);
            if (!policy.equals(expected) || !value.baseAmount().equals(order.amount()) || !value.orderId().equals(order.id())
                    || row.getRowVersion() == null || row.getRowVersion() < 0
                    || (value.state() == FeeState.CONFIRMED) != (row.getConfirmedAt() != null))
                throw new CommonException(SNAPSHOT_INVALID);
            return value;
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private void adjust(String tenant, OrderFeeDO row, OrderFee based, String trigger, String eventId, LocalDateTime at) {
        if (based.adjustmentDueMinor() == 0) return;
        var adjustment = adjustmentConverter.toAdjustment(ids.generate(), based, trigger);
        var record = adjustmentConverter.toDO(tenant, adjustment, eventId, based.refundBasisMinor(), row.getFeeRuleSnapshot(), at);
        var now = LocalDateTime.now(clock);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        int count = jdbc.update("""
                INSERT INTO fee_adjustment (id, tenant_id, created_at, updated_at, deleted, order_id, fee_id, trigger_key,
                    source_event_id, source_version, adjustment_minor, currency, cumulative_refunded_minor, rule_snapshot, applied_at)
                VALUES (:id, :tenantId, :createdAt, :updatedAt, FALSE, :orderId, :feeId, :triggerKey, :sourceEventId,
                    :sourceVersion, :adjustmentMinor, :currency, :cumulativeRefundedMinor, :ruleSnapshot, :appliedAt)
                """, new BeanPropertySqlParameterSource(record));
        if (count != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private void save(String tenant, OrderFeeDO before, OrderFee next, LocalDateTime confirmedAt) {
        var patch = converter.toProgressPatch(next, Math.incrementExact(before.getRowVersion()), confirmedAt);
        var args = new MapSqlParameterSource("tenant", TenantContext.tenantId()).addValue("id", before.getId()).addValue("oldState", before.getState())
                .addValue("expected", before.getRowVersion()).addValue("state", patch.getState()).addValue("basisVersion", patch.getRefundBasisVersion())
                .addValue("basis", patch.getRefundBasisMinor()).addValue("adjusted", patch.getAdjustedAmountMinor())
                .addValue("version", patch.getRowVersion()).addValue("confirmed", patch.getConfirmedAt()).addValue("updated", LocalDateTime.now(clock));
        if (jdbc.update("""
                UPDATE order_fee SET state = :state, refund_basis_version = :basisVersion, refund_basis_minor = :basis,
                    adjusted_amount_minor = :adjusted, row_version = :version, confirmed_at = COALESCE(:confirmed, confirmed_at), updated_at = :updated
                WHERE tenant_id = :tenant AND id = :id AND state = :oldState AND row_version = :expected AND deleted = FALSE
                """, args) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private void writable(String tenant) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || jdbc.getJdbcTemplate().getDataSource() == null
                || !TransactionSynchronizationManager.hasResource(jdbc.getJdbcTemplate().getDataSource()))
            throw new CommonException(INVALID_STATE);
        if (!tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }
}
