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
import io.github.loadup.pay.payment.domain.gateway.PaymentInputGateway;
import io.github.loadup.pay.payment.domain.model.*;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentInputStorageConverter;
import io.github.loadup.pay.payment.infrastructure.dataobject.PaymentInputDO;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentInputDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Optional;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.PAYMENT_INPUT_DO;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;

@Repository
public class PaymentInputGatewayImpl implements PaymentInputGateway {
    private final PaymentInputDOMapper inputs;
    private final PaymentInputStorageConverter converter;
    public PaymentInputGatewayImpl(PaymentInputDOMapper inputs, PaymentInputStorageConverter converter) {
        this.inputs = inputs; this.converter = converter;
    }
    private QueryWrapper scope(String tenant) {
        return QueryWrapper.create().where(PAYMENT_INPUT_DO.TENANT_ID.eq(tenant)).and(PAYMENT_INPUT_DO.DELETED.eq(0));
    }
    @Override public Optional<PaymentInput> findByPayment(String tenant, String paymentId) {
        return read(inputs.selectOneByQuery(scope(tenant).and(PAYMENT_INPUT_DO.PAYMENT_ID.eq(paymentId))));
    }
    @Override public Optional<PaymentInput> find(String tenant, String inputRef) {
        return read(inputs.selectOneByQuery(scope(tenant).and(PAYMENT_INPUT_DO.ID.eq(inputRef))));
    }
    @Override public Optional<PaymentInput> lock(String tenant, String inputRef) {
        requireTransaction();
        return read(inputs.selectOneByQuery(scope(tenant).and(PAYMENT_INPUT_DO.ID.eq(inputRef)).forUpdate()));
    }
    private Optional<PaymentInput> read(PaymentInputDO row) {
        try { return Optional.ofNullable(row).map(converter::toInput); }
        catch (RuntimeException corrupt) { throw new CommonException(SNAPSHOT_INVALID); }
    }
    @Override public void insert(String tenant, PaymentInput value) {
        requireTransaction();
        if (value.state() != PaymentInputState.INIT || value.rowVersion() != 0) throw new CommonException(INVALID_STATE);
        if (inputs.insert(converter.toDO(tenant, value)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }
    @Override public void bind(String tenant, PaymentInput value, long expectedVersion) {
        requireTransaction();
        if (expectedVersion != 0 || value.rowVersion() != 1 || value.state() != PaymentInputState.BOUND)
            throw new CommonException(INVALID_STATE);
        int changed = inputs.updateByQuery(converter.toBindingPatch(value), true,
                scope(tenant).and(PAYMENT_INPUT_DO.ID.eq(value.id()))
                        .and(PAYMENT_INPUT_DO.PAYMENT_ID.eq(value.paymentId()))
                        .and(PAYMENT_INPUT_DO.STATE.eq("INIT")).and(PAYMENT_INPUT_DO.ROW_VERSION.eq(expectedVersion)));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }
    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
    }
}
