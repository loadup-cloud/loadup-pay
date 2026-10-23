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
import io.github.loadup.pay.acquire.domain.gateway.PaymentOutcomeGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.AllocationState;
import io.github.loadup.pay.acquire.domain.model.OrderPaymentChange;
import io.github.loadup.pay.acquire.domain.model.PaymentOutcomeReceipt;
import io.github.loadup.pay.acquire.domain.model.StoredPaymentAllocation;
import io.github.loadup.pay.acquire.domain.model.VerifiedPaymentOutcome;
import io.github.loadup.pay.acquire.infrastructure.converter.PaymentInitiationStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.converter.PaymentOutcomeStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.mapper.AcquireOrderDOMapper;
import io.github.loadup.pay.acquire.infrastructure.mapper.OrderPaymentAllocationDOMapper;
import io.github.loadup.pay.acquire.infrastructure.mapper.OrderPaymentOutcomeDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.ACQUIRE_ORDER_DO;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.ORDER_PAYMENT_ALLOCATION_DO;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.ORDER_PAYMENT_OUTCOME_DO;

@Repository
public class PaymentOutcomeGatewayImpl implements PaymentOutcomeGateway {
    private final AcquireOrderDOMapper orders;
    private final OrderPaymentAllocationDOMapper allocations;
    private final OrderPaymentOutcomeDOMapper receipts;
    private final PaymentInitiationStorageConverter allocationConverter;
    private final PaymentOutcomeStorageConverter converter;
    private final PaymentInitiationCodec codec;

    public PaymentOutcomeGatewayImpl(AcquireOrderDOMapper orders, OrderPaymentAllocationDOMapper allocations,
                                     OrderPaymentOutcomeDOMapper receipts, PaymentInitiationStorageConverter allocationConverter,
                                     PaymentOutcomeStorageConverter converter, PaymentInitiationCodec codec) {
        this.orders = orders;
        this.allocations = allocations;
        this.receipts = receipts;
        this.allocationConverter = allocationConverter;
        this.converter = converter;
        this.codec = codec;
    }

    @Override
    public Optional<StoredPaymentAllocation> findPayment(String tenant, String orderId, String paymentId) {
        return readAllocation(tenant, orderId, paymentId, false);
    }

    @Override
    public Optional<StoredPaymentAllocation> lockPayment(String tenant, String orderId, String paymentId) {
        requireTransaction();
        return readAllocation(tenant, orderId, paymentId, true);
    }

    private Optional<StoredPaymentAllocation> readAllocation(String tenant, String orderId, String paymentId, boolean lock) {
        var query = QueryWrapper.create()
                .where(ORDER_PAYMENT_ALLOCATION_DO.TENANT_ID.eq(tenant))
                .and(ORDER_PAYMENT_ALLOCATION_DO.DELETED.eq(0))
                .and(ORDER_PAYMENT_ALLOCATION_DO.ORDER_ID.eq(orderId))
                .and(ORDER_PAYMENT_ALLOCATION_DO.PAYMENT_ID.eq(paymentId));
        if (lock) query.forUpdate();
        var row = allocations.selectOneByQuery(query);
        if (row == null) return Optional.empty();
        try {
            var value = allocationConverter.toStored(row, allocationConverter.toAllocation(row));
            codec.decode(value);
            return Optional.of(value);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public Optional<PaymentOutcomeReceipt> findReceipt(String tenant, String allocationId) {
        var row = receipts.selectOneByQuery(QueryWrapper.create()
                .where(ORDER_PAYMENT_OUTCOME_DO.TENANT_ID.eq(tenant))
                .and(ORDER_PAYMENT_OUTCOME_DO.DELETED.eq(0))
                .and(ORDER_PAYMENT_OUTCOME_DO.ALLOCATION_ID.eq(allocationId)));
        if (row == null) return Optional.empty();
        try {
            return Optional.of(converter.toReceipt(row, converter.toOutcome(row)));
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void apply(String tenant, OrderPaymentChange change, StoredPaymentAllocation original,
                      PaymentOutcomeReceipt receipt, long expectedOrderVersion) {
        requireTransaction();
        var order = change.order();
        var allocation = change.allocation();
        receipt.requireBoundTo(allocation);
        var expectedState = receipt.outcome().kind() == VerifiedPaymentOutcome.Kind.SUCCEEDED
                ? AllocationState.CONSUMED : AllocationState.RELEASED;
        if (original.allocation().state() != AllocationState.HELD
                || !original.allocation().transition(expectedState, receipt.outcome().paymentVersion()).equals(allocation)
                || (receipt.outcome().kind() == VerifiedPaymentOutcome.Kind.NOT_ACCEPTED
                ? original.allocation().paymentSourceVersion() != 0
                : receipt.outcome().paymentVersion() <= original.allocation().paymentSourceVersion())
                || order.version() != Math.incrementExact(expectedOrderVersion)
                || !(order.state() == AcquireOrderState.PENDING || order.state() == AcquireOrderState.CLOSING || order.state() == AcquireOrderState.PAID))
            throw new CommonException(SNAPSHOT_INVALID);
        int orderChanged = orders.updateByQuery(
                converter.toOrderPatch(order, order.state() == AcquireOrderState.PAID ? receipt.observedAt() : null), true,
                QueryWrapper.create()
                        .where(ACQUIRE_ORDER_DO.TENANT_ID.eq(tenant))
                        .and(ACQUIRE_ORDER_DO.DELETED.eq(0))
                        .and(ACQUIRE_ORDER_DO.ID.eq(order.id()))
                        .and(ACQUIRE_ORDER_DO.ROW_VERSION.eq(expectedOrderVersion))
                        .and(ACQUIRE_ORDER_DO.STATE.in("PENDING", "CLOSING")));
        if (orderChanged != 1) throw new CommonException(CONCURRENT_UPDATE);
        int allocationChanged = allocations.updateByQuery(
                converter.toAllocationPatch(allocation, Math.incrementExact(original.rowVersion())), true,
                QueryWrapper.create()
                        .where(ORDER_PAYMENT_ALLOCATION_DO.TENANT_ID.eq(tenant))
                        .and(ORDER_PAYMENT_ALLOCATION_DO.DELETED.eq(0))
                        .and(ORDER_PAYMENT_ALLOCATION_DO.ID.eq(allocation.id()))
                        .and(ORDER_PAYMENT_ALLOCATION_DO.ORDER_ID.eq(order.id()))
                        .and(ORDER_PAYMENT_ALLOCATION_DO.PAYMENT_ID.eq(allocation.paymentId()))
                        .and(ORDER_PAYMENT_ALLOCATION_DO.STATE.eq("HELD"))
                        .and(ORDER_PAYMENT_ALLOCATION_DO.ROW_VERSION.eq(original.rowVersion()))
                        .and(ORDER_PAYMENT_ALLOCATION_DO.PAYMENT_SOURCE_VERSION.eq(original.allocation().paymentSourceVersion())));
        if (allocationChanged != 1 || receipts.insert(converter.toDO(tenant, receipt)) != 1)
            throw new CommonException(CONCURRENT_UPDATE);
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
    }
}
