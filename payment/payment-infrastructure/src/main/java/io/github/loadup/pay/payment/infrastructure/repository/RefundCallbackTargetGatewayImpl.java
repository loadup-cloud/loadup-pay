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

import com.mybatisflex.core.query.QueryWrapper;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.model.PreparedChannelRefund;
import io.github.loadup.pay.payment.domain.gateway.RefundCallbackTargetGateway;
import io.github.loadup.pay.payment.infrastructure.converter.RefundCallbackStorageConverter;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentRefundCapacityDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.RefundOrderDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Objects;
import java.util.Optional;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_REFUND_CAPACITY_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.REFUND_ORDER_DO;

/**
 * Bounded immutable refund lookup; no current route selection or local locks around channel calls.
 */
@Repository
public class RefundCallbackTargetGatewayImpl implements RefundCallbackTargetGateway {
    private final RefundOrderDOMapper children;
    private final PaymentRefundCapacityDOMapper capacities;
    private final RefundCallbackStorageConverter converter;

    public RefundCallbackTargetGatewayImpl(RefundOrderDOMapper children, PaymentRefundCapacityDOMapper capacities, RefundCallbackStorageConverter converter) {
        this.children = children;
        this.capacities = capacities;
        this.converter = converter;
    }

    @Override
    public Optional<PreparedChannelRefund> find(String tenant, String revision, String orderNo, String refundNo) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        var rows = children.selectListByQuery(QueryWrapper.create().where(REFUND_ORDER_DO.TENANT_ID.eq(tenant))
                .and(REFUND_ORDER_DO.DELETED.eq(0)).and(REFUND_ORDER_DO.BINDING_REVISION_ID.eq(revision))
                .and(REFUND_ORDER_DO.ORIGINAL_CHANNEL_REQUEST_NO.eq(orderNo)).and(REFUND_ORDER_DO.CHANNEL_REFUND_NO.eq(refundNo)).limit(2));
        if (rows.isEmpty()) return Optional.empty();
        if (rows.size() != 1) throw new CommonException(SNAPSHOT_INVALID);
        var row = rows.getFirst();
        if (row.getSubmissionStartedAt() == null || "INIT".equals(row.getState()))
            throw new CommonException(INVALID_STATE);
        var capacity = capacities.selectOneByQuery(QueryWrapper.create().where(PAYMENT_REFUND_CAPACITY_DO.TENANT_ID.eq(tenant))
                .and(PAYMENT_REFUND_CAPACITY_DO.DELETED.eq(0)).and(PAYMENT_REFUND_CAPACITY_DO.PAYMENT_ID.eq(row.getPaymentId())));
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
