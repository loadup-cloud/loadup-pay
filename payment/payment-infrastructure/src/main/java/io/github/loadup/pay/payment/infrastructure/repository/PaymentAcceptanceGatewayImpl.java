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
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentGate;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentAcceptanceStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentOrderDO;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentGateDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentOrderDOMapper;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentParentGateDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_GATE_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_ORDER_DO;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_PARENT_GATE_DO;

@Repository
public class PaymentAcceptanceGatewayImpl implements PaymentAcceptanceGateway {
    private final PaymentOrderDOMapper payments;
    private final PaymentParentGateDOMapper parents;
    private final PaymentGateDOMapper gates;
    private final PaymentAcceptanceStorageConverter converter;
    private final PaymentAcceptanceCodec codec;

    public PaymentAcceptanceGatewayImpl(PaymentOrderDOMapper payments, PaymentParentGateDOMapper parents,
                                        PaymentGateDOMapper gates, PaymentAcceptanceStorageConverter converter, PaymentAcceptanceCodec codec) {
        this.payments = payments;
        this.parents = parents;
        this.gates = gates;
        this.converter = converter;
        this.codec = codec;
    }

    private QueryWrapper paymentScope(String tenant) {
        return QueryWrapper.create().where(PAYMENT_ORDER_DO.TENANT_ID.eq(tenant)).and(PAYMENT_ORDER_DO.DELETED.eq(0));
    }

    @Override
    public Optional<StoredPaymentAcceptance> find(String tenant, String id) {
        return stored(payments.selectOneByQuery(paymentScope(tenant).and(PAYMENT_ORDER_DO.ID.eq(id))));
    }

    @Override
    public Optional<StoredPaymentAcceptance> findAllocation(String tenant, String id) {
        return stored(payments.selectOneByQuery(paymentScope(tenant).and(PAYMENT_ORDER_DO.ALLOCATION_ID.eq(id))));
    }

    private Optional<StoredPaymentAcceptance> stored(PaymentOrderDO row) {
        if (row == null) return Optional.empty();
        try {
            var stored = converter.toStored(row, converter.toPayment(row));
            codec.decode(stored);
            return Optional.of(stored);
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public Optional<StoredPaymentGate> lockParent(String tenant, String orderId) {
        requireTransaction();
        var row = parents.selectOneByQuery(QueryWrapper.create()
                .where(PAYMENT_PARENT_GATE_DO.TENANT_ID.eq(tenant)).and(PAYMENT_PARENT_GATE_DO.DELETED.eq(0))
                .and(PAYMENT_PARENT_GATE_DO.ORDER_ID.eq(orderId)).forUpdate());
        try {
            return Optional.ofNullable(row).map(value -> converter.toStoredParent(value, converter.toParentGate(value)));
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public Optional<StoredPaymentGate> lockPayment(String tenant, String paymentId) {
        requireTransaction();
        var row = gates.selectOneByQuery(QueryWrapper.create()
                .where(PAYMENT_GATE_DO.TENANT_ID.eq(tenant)).and(PAYMENT_GATE_DO.DELETED.eq(0))
                .and(PAYMENT_GATE_DO.PAYMENT_ID.eq(paymentId)).forUpdate());
        try {
            return Optional.ofNullable(row).map(value -> converter.toStoredGate(value, converter.toPaymentGate(value)));
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insertParent(String tenant, StoredPaymentGate gate) {
        requireTransaction();
        if (gate.gate().paymentId() != null) throw new CommonException(INVALID_ARGUMENT);
        if (parents.insert(converter.toParentDO(tenant, gate)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void insertPaymentGate(String tenant, StoredPaymentGate gate) {
        requireTransaction();
        if (gate.gate().paymentId() == null) throw new CommonException(INVALID_ARGUMENT);
        if (gates.insert(converter.toGateDO(tenant, gate)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void insert(String tenant, StoredPaymentAcceptance value) {
        requireTransaction();
        if (payments.insert(converter.toPaymentDO(tenant, value)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
    }
}
