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
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.pay.payment.domain.gateway.RefundSummaryGateway;
import io.github.loadup.pay.payment.domain.model.OrderRefundSummary;
import io.github.loadup.pay.payment.infrastructure.converter.RefundProjectionStorageConverter;
import io.github.loadup.pay.payment.infrastructure.mapper.OrderRefundSummaryDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Optional;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.ORDER_REFUND_SUMMARY_DO;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;

@Repository
public class RefundSummaryGatewayImpl implements RefundSummaryGateway {
    private final OrderRefundSummaryDOMapper mapper;
    private final RefundProjectionStorageConverter converter;
    private final IdGenerator ids;
    public RefundSummaryGatewayImpl(OrderRefundSummaryDOMapper mapper, RefundProjectionStorageConverter converter, IdGenerator ids) {
        this.mapper = mapper; this.converter = converter; this.ids = ids;
    }
    @Override public Optional<OrderRefundSummary> lock(String tenant, String orderId) {
        writable(tenant);
        var row = mapper.selectOneByQuery(scope(tenant, orderId).forUpdate());
        if (row == null) return Optional.empty();
        try { return Optional.of(converter.toSummary(row)); }
        catch (RuntimeException corrupt) { throw new CommonException(SNAPSHOT_INVALID); }
    }
    @Override public void insert(String tenant, OrderRefundSummary value) {
        writable(tenant);
        if (value.version() != 1 || value.refundedMinor() != 0 || value.reservedMinor() <= 0) throw new CommonException(INVALID_STATE);
        if (mapper.insert(converter.toSummaryDO(tenant, ids.generate(), value)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }
    @Override public void update(String tenant, OrderRefundSummary before, OrderRefundSummary value) {
        writable(tenant);
        if (!before.orderId().equals(value.orderId()) || !before.payeeMerchantId().equals(value.payeeMerchantId())
                || !before.orderAmount().equals(value.orderAmount()) || value.version() != Math.incrementExact(before.version())
                || value.refundedMinor() < before.refundedMinor()) throw new CommonException(INVALID_STATE);
        if (mapper.updateByQuery(converter.toPatch(value), true, scope(tenant, before.orderId())
                .and(ORDER_REFUND_SUMMARY_DO.ROW_VERSION.eq(before.version())).and(ORDER_REFUND_SUMMARY_DO.REFUNDED_MINOR.eq(before.refundedMinor()))
                .and(ORDER_REFUND_SUMMARY_DO.RESERVED_MINOR.eq(before.reservedMinor()))) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }
    private QueryWrapper scope(String tenant, String order) {
        return QueryWrapper.create().where(ORDER_REFUND_SUMMARY_DO.TENANT_ID.eq(tenant)).and(ORDER_REFUND_SUMMARY_DO.DELETED.eq(0))
                .and(ORDER_REFUND_SUMMARY_DO.ORDER_ID.eq(order));
    }
    private void writable(String tenant) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        if (!tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }
}
