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
import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.commons.dto.PageQuery;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.acquire.domain.gateway.MerchantNotificationWakeGateway;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationWakeReceipt;
import io.github.loadup.pay.acquire.infrastructure.converter.MerchantNotificationWakeReceiptStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.dataobject.MerchantNotificationWakeReceiptDO;
import io.github.loadup.pay.acquire.infrastructure.mapper.MerchantNotificationWakeReceiptDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_PAGE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.MERCHANT_NOTIFICATION_WAKE_RECEIPT_DO;

@Repository
public class MerchantNotificationWakeGatewayImpl implements MerchantNotificationWakeGateway {
    private final MerchantNotificationWakeReceiptDOMapper mapper;
    private final MerchantNotificationWakeReceiptStorageConverter converter;

    public MerchantNotificationWakeGatewayImpl(MerchantNotificationWakeReceiptDOMapper mapper, MerchantNotificationWakeReceiptStorageConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    @Override
    public Optional<MerchantNotificationWakeReceipt> find(String tenant, String scope, String requestKey) {
        var row = mapper.selectOneByQuery(scope(tenant).and(MERCHANT_NOTIFICATION_WAKE_RECEIPT_DO.CALLER_SCOPE_HASH.eq(scope))
                .and(MERCHANT_NOTIFICATION_WAKE_RECEIPT_DO.REQUEST_KEY.eq(requestKey)));
        return Optional.ofNullable(row).map(this::decode);
    }

    @Override
    public void insert(String tenant, MerchantNotificationWakeReceipt value) {
        scope(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        if (mapper.insert(converter.toDO(tenant, value)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public PageResult<MerchantNotificationWakeReceipt> page(String tenant, String deliveryId, int page, int size) {
        PageQuery.normalized(page, size, INVALID_PAGE);
        var query = scope(tenant).and(MERCHANT_NOTIFICATION_WAKE_RECEIPT_DO.DELIVERY_ID.eq(deliveryId));
        long total = mapper.selectCountByQuery(query);
        query.orderBy(MERCHANT_NOTIFICATION_WAKE_RECEIPT_DO.ACCEPTED_AT.desc(), MERCHANT_NOTIFICATION_WAKE_RECEIPT_DO.ID.desc())
                .limit(size).offset((long) (page - 1) * size);
        return PageResult.of(mapper.selectListByQuery(query).stream().map(this::decode).toList(), total, page, size);
    }

    private QueryWrapper scope(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        return QueryWrapper.create().where(MERCHANT_NOTIFICATION_WAKE_RECEIPT_DO.TENANT_ID.eq(tenant))
                .and(MERCHANT_NOTIFICATION_WAKE_RECEIPT_DO.DELETED.eq(0));
    }

    private MerchantNotificationWakeReceipt decode(MerchantNotificationWakeReceiptDO row) {
        try {
            return converter.toDomain(row);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }
}
