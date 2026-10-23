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
import io.github.loadup.pay.acquire.domain.gateway.PaymentInitiationGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrder;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.AllocationState;
import io.github.loadup.pay.acquire.domain.model.StoredPaymentAllocation;
import io.github.loadup.pay.acquire.infrastructure.converter.PaymentInitiationStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.mapper.AcquireOrderDOMapper;
import io.github.loadup.pay.acquire.infrastructure.mapper.OrderPaymentAllocationDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.ACQUIRE_ORDER_DO;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.ORDER_PAYMENT_ALLOCATION_DO;

@Repository
public class PaymentInitiationGatewayImpl implements PaymentInitiationGateway {
    private final OrderPaymentAllocationDOMapper allocations;
    private final AcquireOrderDOMapper orders;
    private final PaymentInitiationStorageConverter converter;
    private final PaymentInitiationCodec codec;

    public PaymentInitiationGatewayImpl(
            OrderPaymentAllocationDOMapper allocations,
            AcquireOrderDOMapper orders,
            PaymentInitiationStorageConverter converter,
            PaymentInitiationCodec codec) {
        this.allocations = allocations;
        this.orders = orders;
        this.converter = converter;
        this.codec = codec;
    }

    @Override
    public Optional<StoredPaymentAllocation> findAction(
            String tenant, String orderId, String actorScope, String actionKey) {
        var row = allocations.selectOneByQuery(QueryWrapper.create()
                .where(ORDER_PAYMENT_ALLOCATION_DO.TENANT_ID.eq(tenant))
                .and(ORDER_PAYMENT_ALLOCATION_DO.DELETED.eq(0))
                .and(ORDER_PAYMENT_ALLOCATION_DO.ORDER_ID.eq(orderId))
                .and(ORDER_PAYMENT_ALLOCATION_DO.ACTOR_SCOPE.eq(actorScope))
                .and(ORDER_PAYMENT_ALLOCATION_DO.ACTION_KEY.eq(actionKey)));
        if (row == null) return Optional.empty();
        try {
            var value = converter.toStored(row, converter.toAllocation(row));
            codec.decode(value);
            return Optional.of(value);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void attachInput(String tenant, StoredPaymentAllocation original, StoredPaymentAllocation prepared) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (!original.allocation().equals(prepared.allocation())
                || !original.callerScopeHash().equals(prepared.callerScopeHash())
                || original.allocation().state() != AllocationState.HELD
                || original.allocation().paymentSourceVersion() != 0
                || prepared.rowVersion() != Math.incrementExact(original.rowVersion())
                || codec.decode(original).preparedInputRef() != null
                || codec.decode(prepared).preparedInputRef() == null) throw new CommonException(SNAPSHOT_INVALID);
        int changed = allocations.updateByQuery(converter.toInputPatch(prepared), true, QueryWrapper.create()
                .where(ORDER_PAYMENT_ALLOCATION_DO.TENANT_ID.eq(tenant))
                .and(ORDER_PAYMENT_ALLOCATION_DO.DELETED.eq(0))
                .and(ORDER_PAYMENT_ALLOCATION_DO.ID.eq(original.allocation().id()))
                .and(ORDER_PAYMENT_ALLOCATION_DO.PAYMENT_ID.eq(original.allocation().paymentId()))
                .and(ORDER_PAYMENT_ALLOCATION_DO.STATE.eq("HELD"))
                .and(ORDER_PAYMENT_ALLOCATION_DO.PAYMENT_SOURCE_VERSION.eq(0))
                .and(ORDER_PAYMENT_ALLOCATION_DO.ROW_VERSION.eq(original.rowVersion()))
                .and(ORDER_PAYMENT_ALLOCATION_DO.PAYLOAD_HASH.eq(original.payloadHash())));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void reserve(String tenant, AcquireOrder updated, StoredPaymentAllocation value, long expectedOrderVersion) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (!updated.id().equals(value.allocation().orderId())
                || updated.version() != Math.incrementExact(expectedOrderVersion)
                || updated.state() != AcquireOrderState.PENDING
                || value.allocation().state() != AllocationState.HELD
                || value.rowVersion() != 0
                || !updated.amount()
                .getCurrencyValue()
                .equals(value.allocation().amount().getCurrencyValue()))
            throw new CommonException(SNAPSHOT_INVALID);
        int changed = orders.updateByQuery(
                converter.toReservationPatch(updated),
                true,
                QueryWrapper.create()
                        .where(ACQUIRE_ORDER_DO.TENANT_ID.eq(tenant))
                        .and(ACQUIRE_ORDER_DO.DELETED.eq(0))
                        .and(ACQUIRE_ORDER_DO.ID.eq(updated.id()))
                        .and(ACQUIRE_ORDER_DO.ROW_VERSION.eq(expectedOrderVersion))
                        .and(ACQUIRE_ORDER_DO.STATE.in("INIT", "PENDING")));
        if (changed != 1 || allocations.insert(converter.toAllocationDO(tenant, value)) != 1)
            throw new CommonException(CONCURRENT_UPDATE);
    }
}
