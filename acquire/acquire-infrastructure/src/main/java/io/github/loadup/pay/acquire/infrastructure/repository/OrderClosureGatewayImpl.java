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
import io.github.loadup.pay.acquire.app.port.PaymentInitiationCodec;
import io.github.loadup.pay.acquire.domain.gateway.OrderClosureGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrder;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.OrderCloseIntent;
import io.github.loadup.pay.acquire.domain.model.StoredPaymentAllocation;
import io.github.loadup.pay.acquire.infrastructure.converter.OrderClosureStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.converter.PaymentInitiationStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.mapper.AcquireOrderDOMapper;
import io.github.loadup.pay.acquire.infrastructure.mapper.OrderCloseIntentDOMapper;
import io.github.loadup.pay.acquire.infrastructure.mapper.OrderPaymentAllocationDOMapper;
import io.github.loadup.pay.acquire.infrastructure.mapper.OrderPaymentOutcomeDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.ACQUIRE_ORDER_DO;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.ORDER_CLOSE_INTENT_DO;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.ORDER_PAYMENT_ALLOCATION_DO;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.ORDER_PAYMENT_OUTCOME_DO;

@Repository
public class OrderClosureGatewayImpl implements OrderClosureGateway {
    private final OrderCloseIntentDOMapper intents;
    private final AcquireOrderDOMapper orders;
    private final OrderPaymentAllocationDOMapper allocations;
    private final OrderPaymentOutcomeDOMapper receipts;
    private final OrderClosureStorageConverter converter;
    private final PaymentInitiationStorageConverter allocationConverter;
    private final PaymentInitiationCodec codec;

    public OrderClosureGatewayImpl(OrderCloseIntentDOMapper intents, AcquireOrderDOMapper orders,
                                   OrderPaymentAllocationDOMapper allocations, OrderPaymentOutcomeDOMapper receipts,
                                   OrderClosureStorageConverter converter, PaymentInitiationStorageConverter allocationConverter,
                                   PaymentInitiationCodec codec) {
        this.intents = intents;
        this.orders = orders;
        this.allocations = allocations;
        this.receipts = receipts;
        this.converter = converter;
        this.allocationConverter = allocationConverter;
        this.codec = codec;
    }

    @Override
    public Optional<OrderCloseIntent> findIntent(String tenant, String orderId) {
        var row = intents.selectOneByQuery(QueryWrapper.create()
                .where(ORDER_CLOSE_INTENT_DO.TENANT_ID.eq(tenant))
                .and(ORDER_CLOSE_INTENT_DO.DELETED.eq(0))
                .and(ORDER_CLOSE_INTENT_DO.ORDER_ID.eq(orderId)));
        if (row == null) return Optional.empty();
        try {
            return Optional.of(converter.toIntent(row));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void start(String tenant, AcquireOrder closing, OrderCloseIntent intent, long expectedOrderVersion) {
        requireTransaction();
        if (closing.state() != AcquireOrderState.CLOSING || !closing.id().equals(intent.orderId())
                || !closing.payeeMerchantId().equals(intent.payeeMerchantId()) || closing.closeEpoch() != intent.closeEpoch()
                || closing.version() != Math.incrementExact(expectedOrderVersion) || intent.rowVersion() != 0
                || !intent.cursorPaymentId().isEmpty()) throw new CommonException(SNAPSHOT_INVALID);
        int changed = orders.updateByQuery(converter.toClosingPatch(closing), true, orderScope(tenant, closing.id())
                .and(ACQUIRE_ORDER_DO.ROW_VERSION.eq(expectedOrderVersion))
                .and(ACQUIRE_ORDER_DO.STATE.in("INIT", "PENDING")));
        if (changed != 1 || intents.insert(converter.toDO(tenant, intent)) != 1)
            throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public List<StoredPaymentAllocation> heldAfter(String tenant, String orderId, String afterPaymentId, int limit) {
        if (limit < 1 || limit > 16 || afterPaymentId == null) throw new CommonException(INVALID_ARGUMENT);
        var query = allocationScope(tenant, orderId).and(ORDER_PAYMENT_ALLOCATION_DO.STATE.eq("HELD"));
        if (!afterPaymentId.isEmpty()) query.and(ORDER_PAYMENT_ALLOCATION_DO.PAYMENT_ID.gt(afterPaymentId));
        var rows = allocations.selectListByQuery(query.orderBy(ORDER_PAYMENT_ALLOCATION_DO.PAYMENT_ID.asc()).limit(limit));
        try {
            return rows.stream().map(row -> {
                var value = allocationConverter.toStored(row, allocationConverter.toAllocation(row));
                codec.decode(value);
                return value;
            }).toList();
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void advanceCursor(String tenant, OrderCloseIntent updated, long expectedVersion) {
        requireTransaction();
        if (updated.rowVersion() != Math.incrementExact(expectedVersion)) throw new CommonException(SNAPSHOT_INVALID);
        // Empty string starts a new pass; it is a real value, never an ignored-null clear operation.
        int changed = intents.updateByQuery(converter.toCursorPatch(updated), true, QueryWrapper.create()
                .where(ORDER_CLOSE_INTENT_DO.TENANT_ID.eq(tenant))
                .and(ORDER_CLOSE_INTENT_DO.DELETED.eq(0))
                .and(ORDER_CLOSE_INTENT_DO.ID.eq(updated.id()))
                .and(ORDER_CLOSE_INTENT_DO.ORDER_ID.eq(updated.orderId()))
                .and(ORDER_CLOSE_INTENT_DO.ROW_VERSION.eq(expectedVersion)));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public boolean allAllocationsSettled(String tenant, String orderId) {
        requireTransaction();
        if (allocations.selectCountByQuery(allocationScope(tenant, orderId)
                .and(ORDER_PAYMENT_ALLOCATION_DO.STATE.in("INIT", "HELD"))) != 0) return false;
        long count = allocations.selectCountByQuery(allocationScope(tenant, orderId));
        long proofs = receipts.selectCountByQuery(QueryWrapper.create()
                .where(ORDER_PAYMENT_OUTCOME_DO.TENANT_ID.eq(tenant))
                .and(ORDER_PAYMENT_OUTCOME_DO.DELETED.eq(0))
                .and(ORDER_PAYMENT_OUTCOME_DO.ORDER_ID.eq(orderId)));
        return count == proofs;
    }

    @Override
    public void finish(String tenant, AcquireOrder closed, long expectedOrderVersion, LocalDateTime closedAt) {
        requireTransaction();
        if (closed.state() != AcquireOrderState.CLOSED || closed.version() != Math.incrementExact(expectedOrderVersion)
                || closed.closeEpoch() <= 0 || closedAt == null) throw new CommonException(SNAPSHOT_INVALID);
        int changed = orders.updateByQuery(converter.toClosedPatch(closed, closedAt), true, orderScope(tenant, closed.id())
                .and(ACQUIRE_ORDER_DO.STATE.eq("CLOSING"))
                .and(ACQUIRE_ORDER_DO.CLOSE_EPOCH.eq(closed.closeEpoch()))
                .and(ACQUIRE_ORDER_DO.ROW_VERSION.eq(expectedOrderVersion))
                .and(ACQUIRE_ORDER_DO.PAYMENT_RESERVED_MINOR.eq(0))
                .and(ACQUIRE_ORDER_DO.RESERVED_PAYMENT_COUNT.eq(0)));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private QueryWrapper orderScope(String tenant, String orderId) {
        return QueryWrapper.create().where(ACQUIRE_ORDER_DO.TENANT_ID.eq(tenant))
                .and(ACQUIRE_ORDER_DO.DELETED.eq(0)).and(ACQUIRE_ORDER_DO.ID.eq(orderId));
    }

    private QueryWrapper allocationScope(String tenant, String orderId) {
        return QueryWrapper.create().where(ORDER_PAYMENT_ALLOCATION_DO.TENANT_ID.eq(tenant))
                .and(ORDER_PAYMENT_ALLOCATION_DO.DELETED.eq(0)).and(ORDER_PAYMENT_ALLOCATION_DO.ORDER_ID.eq(orderId));
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
    }
}
