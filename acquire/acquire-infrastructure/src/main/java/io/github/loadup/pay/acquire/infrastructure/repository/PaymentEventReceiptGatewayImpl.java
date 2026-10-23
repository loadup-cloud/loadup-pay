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
import io.github.loadup.pay.acquire.domain.gateway.PaymentEventReceiptGateway;
import io.github.loadup.pay.acquire.domain.model.PaymentEventReceipt;
import io.github.loadup.pay.acquire.infrastructure.converter.PaymentEventStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.mapper.PaymentEventReceiptDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Optional;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.PAYMENT_EVENT_RECEIPT_DO;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.*;

@Repository
public class PaymentEventReceiptGatewayImpl implements PaymentEventReceiptGateway {
    private final PaymentEventReceiptDOMapper mapper;
    private final PaymentEventStorageConverter converter;
    public PaymentEventReceiptGatewayImpl(PaymentEventReceiptDOMapper mapper, PaymentEventStorageConverter converter) {
        this.mapper = mapper; this.converter = converter;
    }
    @Override public Optional<PaymentEventReceipt> find(String tenant, String deliveryId) {
        var row = mapper.selectOneByQuery(QueryWrapper.create().where(PAYMENT_EVENT_RECEIPT_DO.TENANT_ID.eq(tenant))
                .and(PAYMENT_EVENT_RECEIPT_DO.DELETED.eq(0)).and(PAYMENT_EVENT_RECEIPT_DO.DELIVERY_ID.eq(deliveryId)));
        try { return Optional.ofNullable(row).map(converter::toDomain); }
        catch (RuntimeException corrupt) { throw new CommonException(SNAPSHOT_INVALID); }
    }
    @Override public void insert(String tenant, PaymentEventReceipt receipt) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (mapper.insert(converter.toDO(tenant, receipt)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }
}
