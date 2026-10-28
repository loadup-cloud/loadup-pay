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
package io.github.loadup.pay.acquire.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.outbox.OutboxMessage;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.pay.acquire.app.converter.OrderSnapshotConverter;
import io.github.loadup.pay.acquire.app.port.OrderSnapshotReader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

@Component
public class OrderSnapshotEvents {
    public static final String TYPE = "pay.acquire.snapshot.realtime.v1";
    private final OrderSnapshotReader headers;
    private final OrderSnapshotConverter converter;
    private final OutboxTemplate outbox;
    private final IdGenerator ids;
    private final MerchantNotificationEvents notifications;

    public OrderSnapshotEvents(OrderSnapshotReader headers, OrderSnapshotConverter converter, OutboxTemplate outbox, IdGenerator ids,
                               MerchantNotificationEvents notifications) {
        this.headers = headers;
        this.converter = converter;
        this.outbox = outbox;
        this.ids = ids;
        this.notifications = notifications;
    }

    public void publish(String tenant, String orderId, long expectedVersion) {
        if (!TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            throw new CommonException(INVALID_STATE);
        if (!tenant.equals(TenantUtil.getTenantId())) throw new CommonException(ACTION_FORBIDDEN);
        DomainChecks.id(orderId);
        var header = headers.lockCurrent(tenant, orderId);
        if (header == null || !orderId.equals(header.orderId()) || header.sourceVersion() != expectedVersion)
            throw new CommonException(SNAPSHOT_INVALID);
        final io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO data;
        try {
            data = converter.toEvent(header);
        } catch (RuntimeException corrupt) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var event = new EventEnvelope<>(ids.generate(), ids.generate(), "acquire", TYPE, 1, orderId, expectedVersion,
                BusinessTime.external(header.sourceUpdatedAt()), data);
        outbox.publish(new OutboxMessage(tenant, TYPE, 1, orderId, JsonUtil.toJson(event)));
        notifications.order(tenant, header);
    }
}
