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

import com.mybatisflex.core.query.QueryWrapper;
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
import io.github.loadup.pay.acquire.infrastructure.mapper.FeeAdjustmentDOMapper;
import io.github.loadup.pay.acquire.infrastructure.mapper.OrderFeeDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.ORDER_FEE_DO;

@Repository
public class OrderFeeGatewayImpl implements OrderFeeGateway {
    private final OrderFeeDOMapper fees;
    private final FeeAdjustmentDOMapper adjustments;
    private final OrderFeeStorageConverter converter;
    private final FeeAdjustmentStorageConverter adjustmentConverter;
    private final IdGenerator ids;
    private final JsonMapper mapper;

    public OrderFeeGatewayImpl(OrderFeeDOMapper fees, FeeAdjustmentDOMapper adjustments, OrderFeeStorageConverter converter,
                               FeeAdjustmentStorageConverter adjustmentConverter, IdGenerator ids, JsonMapper mapper) {
        this.fees = fees;
        this.adjustments = adjustments;
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
        var row = fees.selectOneByQuery(QueryWrapper.create().where(ORDER_FEE_DO.TENANT_ID.eq(tenant)).and(ORDER_FEE_DO.DELETED.eq(0))
                .and(ORDER_FEE_DO.ORDER_ID.eq(order)).and(ORDER_FEE_DO.FEE_TYPE.eq("ORDER_AMOUNT")).forUpdate());
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
        if (adjustments.insert(adjustmentConverter.toDO(tenant, adjustment, eventId, based.refundBasisMinor(), row.getFeeRuleSnapshot(), at)) != 1)
            throw new CommonException(CONCURRENT_UPDATE);
    }

    private void save(String tenant, OrderFeeDO before, OrderFee next, LocalDateTime confirmedAt) {
        if (fees.updateByQuery(converter.toProgressPatch(next, Math.incrementExact(before.getRowVersion()), confirmedAt), true,
                QueryWrapper.create().where(ORDER_FEE_DO.TENANT_ID.eq(tenant)).and(ORDER_FEE_DO.DELETED.eq(0))
                        .and(ORDER_FEE_DO.ID.eq(before.getId())).and(ORDER_FEE_DO.STATE.eq(before.getState()))
                        .and(ORDER_FEE_DO.ROW_VERSION.eq(before.getRowVersion()))) != 1)
            throw new CommonException(CONCURRENT_UPDATE);
    }

    private void writable(String tenant) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        if (!tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }
}
