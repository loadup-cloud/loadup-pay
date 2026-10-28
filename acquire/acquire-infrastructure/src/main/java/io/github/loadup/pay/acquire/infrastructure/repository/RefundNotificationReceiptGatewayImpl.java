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
import io.github.loadup.pay.acquire.domain.gateway.RefundNotificationReceiptGateway;
import io.github.loadup.pay.acquire.domain.model.RefundNotificationReceipt;
import io.github.loadup.pay.acquire.infrastructure.converter.RefundNotificationReceiptStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.mapper.RefundNotificationReceiptDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.REFUND_NOTIFICATION_RECEIPT_DO;

@Repository
public class RefundNotificationReceiptGatewayImpl implements RefundNotificationReceiptGateway {
    private final RefundNotificationReceiptDOMapper mapper;
    private final RefundNotificationReceiptStorageConverter converter;

    public RefundNotificationReceiptGatewayImpl(RefundNotificationReceiptDOMapper mapper, RefundNotificationReceiptStorageConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    @Override
    public Optional<RefundNotificationReceipt> find(String tenant, String id) {
        scope(tenant);
        var row = mapper.selectOneByQuery(QueryWrapper.create().where(REFUND_NOTIFICATION_RECEIPT_DO.TENANT_ID.eq(tenant))
                .and(REFUND_NOTIFICATION_RECEIPT_DO.DELETED.eq(0)).and(REFUND_NOTIFICATION_RECEIPT_DO.DELIVERY_ID.eq(id)));
        try {
            return Optional.ofNullable(row).map(converter::toDomain);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public void insert(String tenant, RefundNotificationReceipt value) {
        scope(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        if (mapper.insert(converter.toDO(tenant, value)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private void scope(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }
}
