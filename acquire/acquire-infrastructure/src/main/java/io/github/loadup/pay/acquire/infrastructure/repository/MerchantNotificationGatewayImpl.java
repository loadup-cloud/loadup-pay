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
import io.github.loadup.pay.acquire.domain.gateway.MerchantNotificationGateway;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationDelivery;
import io.github.loadup.pay.acquire.infrastructure.converter.MerchantNotificationStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.mapper.MerchantNotificationDeliveryDOMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.MERCHANT_NOTIFICATION_DELIVERY_DO;

@Repository
public class MerchantNotificationGatewayImpl implements MerchantNotificationGateway {
    private final MerchantNotificationDeliveryDOMapper mapper;
    private final MerchantNotificationStorageConverter converter;

    public MerchantNotificationGatewayImpl(MerchantNotificationDeliveryDOMapper mapper,
            MerchantNotificationStorageConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    @Override
    public Optional<MerchantNotificationDelivery> findEvent(String tenant, String eventId, String targetRef) {
        tenant(tenant);
        var row = mapper.selectOneByQuery(QueryWrapper.create()
                .where(MERCHANT_NOTIFICATION_DELIVERY_DO.TENANT_ID.eq(tenant))
                .and(MERCHANT_NOTIFICATION_DELIVERY_DO.DELETED.eq(0))
                .and(MERCHANT_NOTIFICATION_DELIVERY_DO.EVENT_ID.eq(eventId))
                .and(MERCHANT_NOTIFICATION_DELIVERY_DO.TARGET_REF.eq(targetRef)));
        return row == null ? Optional.empty() : Optional.of(decode(row));
    }

    @Override
    public Optional<MerchantNotificationDelivery> lock(String tenant, String deliveryId) {
        writable(tenant);
        var row = mapper.selectOneByQuery(QueryWrapper.create()
                .where(MERCHANT_NOTIFICATION_DELIVERY_DO.TENANT_ID.eq(tenant))
                .and(MERCHANT_NOTIFICATION_DELIVERY_DO.DELETED.eq(0))
                .and(MERCHANT_NOTIFICATION_DELIVERY_DO.ID.eq(deliveryId)).forUpdate());
        return row == null ? Optional.empty() : Optional.of(decode(row));
    }

    @Override
    public void insert(String tenant, MerchantNotificationDelivery delivery) {
        writable(tenant);
        if (delivery.rowVersion() != 0) throw new CommonException(SNAPSHOT_INVALID);
        if (mapper.insert(converter.toDO(tenant, delivery)) != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    @Override
    public void update(String tenant, MerchantNotificationDelivery before, MerchantNotificationDelivery after) {
        writable(tenant);
        if (!sameIdentity(before, after) || after.rowVersion() != increment(before.rowVersion())
                || before.routeFingerprint() != null && !before.routeFingerprint().equals(after.routeFingerprint()))
            throw new CommonException(SNAPSHOT_INVALID);
        int changed = mapper.updateByQuery(converter.toPatch(after), true, QueryWrapper.create()
                .where(MERCHANT_NOTIFICATION_DELIVERY_DO.TENANT_ID.eq(tenant))
                .and(MERCHANT_NOTIFICATION_DELIVERY_DO.DELETED.eq(0))
                .and(MERCHANT_NOTIFICATION_DELIVERY_DO.ID.eq(before.id()))
                .and(MERCHANT_NOTIFICATION_DELIVERY_DO.ROW_VERSION.eq(before.rowVersion())));
        if (changed != 1) throw new CommonException(CONCURRENT_UPDATE);
    }

    private MerchantNotificationDelivery decode(io.github.loadup.pay.acquire.infrastructure.dataobject.MerchantNotificationDeliveryDO row) {
        try {
            var value = converter.toDomain(row);
            if (row.getId() == null || row.getTenantId() == null || row.getRowVersion() == null
                    || row.getRowVersion() < 0 || !row.getId().equals(value.id())
                    || row.getRowVersion() != value.rowVersion() || !row.getTargetRef().equals(value.policy().targetRef()))
                throw new CommonException(SNAPSHOT_INVALID);
            return value;
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    private boolean sameIdentity(MerchantNotificationDelivery before, MerchantNotificationDelivery after) {
        return before != null && after != null && before.id().equals(after.id())
                && before.eventId().equals(after.eventId()) && before.orderId().equals(after.orderId())
                && before.payeeMerchantId().equals(after.payeeMerchantId()) && before.eventType().equals(after.eventType())
                && before.aggregateId().equals(after.aggregateId()) && before.sourceVersion() == after.sourceVersion()
                && before.body().equals(after.body()) && before.bodyDigest().equals(after.bodyDigest())
                && before.policy().equals(after.policy()) && before.occurredAt().equals(after.occurredAt())
                && before.expiresAt().equals(after.expiresAt()) && before.rowVersion() >= 0;
    }

    private long increment(long version) {
        try { return Math.incrementExact(version); }
        catch (ArithmeticException invalid) { throw new CommonException(SNAPSHOT_INVALID); }
    }

    private void tenant(String tenant) {
        if (tenant == null || !tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
    }

    private void writable(String tenant) {
        tenant(tenant);
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
    }
}
