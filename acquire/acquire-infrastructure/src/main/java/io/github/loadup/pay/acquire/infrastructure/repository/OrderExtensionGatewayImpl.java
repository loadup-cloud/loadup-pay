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
import io.github.loadup.pay.acquire.domain.gateway.OrderExtensionGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrder;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.OrderExtensionReceipt;
import io.github.loadup.pay.acquire.infrastructure.converter.OrderExtensionStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.mapper.AcquireOrderDOMapper;
import io.github.loadup.pay.acquire.infrastructure.mapper.OrderExtensionReceiptDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.ACQUIRE_ORDER_DO;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.ORDER_EXTENSION_RECEIPT_DO;

@Repository
public class OrderExtensionGatewayImpl implements OrderExtensionGateway {
    private final AcquireOrderDOMapper orders;
    private final OrderExtensionReceiptDOMapper receipts;
    private final OrderExtensionStorageConverter converter;

    public OrderExtensionGatewayImpl(AcquireOrderDOMapper orders, OrderExtensionReceiptDOMapper receipts,
                                     OrderExtensionStorageConverter converter) {
        this.orders = orders;
        this.receipts = receipts;
        this.converter = converter;
    }

    @Override
    public Optional<OrderExtensionReceipt> findReceipt(String tenant, String orderId, String requestKey) {
        var row = receipts.selectOneByQuery(QueryWrapper.create()
                .where(ORDER_EXTENSION_RECEIPT_DO.TENANT_ID.eq(tenant))
                .and(ORDER_EXTENSION_RECEIPT_DO.DELETED.eq(0))
                .and(ORDER_EXTENSION_RECEIPT_DO.ORDER_ID.eq(orderId))
                .and(ORDER_EXTENSION_RECEIPT_DO.REQUEST_KEY.eq(requestKey)));
        if (row == null) return Optional.empty();
        try {
            return Optional.of(converter.toReceipt(row));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public long deadlineVersion(String tenant, AcquireOrder order) {
        requireTransaction();
        var row = orders.selectOneByQuery(orderScope(tenant, order.id()));
        if (row == null || row.getDeadlineVersion() == null || row.getDeadlineVersion() < 1
                || row.getRowVersion() == null || row.getRowVersion() != order.version()
                || !order.orderDueAt().equals(row.getOrderDueAt())) throw new CommonException(SNAPSHOT_INVALID);
        return row.getDeadlineVersion();
    }

    @Override
    public void extend(String tenant, AcquireOrder updated, OrderExtensionReceipt receipt) {
        requireTransaction();
        if (!updated.id().equals(receipt.orderId()) || !updated.orderDueAt().equals(receipt.requestedDueAt())
                || updated.version() != Math.incrementExact(receipt.expectedOrderVersion())
                || !(updated.state() == AcquireOrderState.INIT || updated.state() == AcquireOrderState.PENDING))
            throw new CommonException(SNAPSHOT_INVALID);
        int changed = orders.updateByQuery(converter.toDeadlinePatch(updated, receipt.deadlineVersion()), true,
                orderScope(tenant, updated.id())
                        .and(ACQUIRE_ORDER_DO.ROW_VERSION.eq(receipt.expectedOrderVersion()))
                        .and(ACQUIRE_ORDER_DO.DEADLINE_VERSION.eq(receipt.deadlineVersion() - 1))
                        .and(ACQUIRE_ORDER_DO.ORDER_DUE_AT.eq(receipt.previousDueAt()))
                        .and(ACQUIRE_ORDER_DO.STATE.in("INIT", "PENDING")));
        if (changed != 1 || receipts.insert(converter.toDO(tenant, receipt)) != 1)
            throw new CommonException(CONCURRENT_UPDATE);
    }

    private QueryWrapper orderScope(String tenant, String orderId) {
        return QueryWrapper.create().where(ACQUIRE_ORDER_DO.TENANT_ID.eq(tenant))
                .and(ACQUIRE_ORDER_DO.DELETED.eq(0)).and(ACQUIRE_ORDER_DO.ID.eq(orderId));
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
    }
}
