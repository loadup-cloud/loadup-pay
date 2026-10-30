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
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.outbox.OutboxEvent;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.pay.acquire.app.port.AcquireRealtimeClient;
import io.github.loadup.pay.acquire.app.port.OrderSnapshotEnvelopeCodec;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;

/**
 * Complete source snapshots are delivered independently; no source lookup or downstream read participates in the order transaction.
 */
@Component
public class OrderSnapshotDelivery {
    private final OutboxTemplate outbox;
    private final OrderSnapshotEnvelopeCodec codec;
    private final AcquireRealtimeClient client;

    public OrderSnapshotDelivery(OutboxTemplate outbox, OrderSnapshotEnvelopeCodec codec, AcquireRealtimeClient client) {
        this.outbox = outbox;
        this.codec = codec;
        this.client = client;
    }

    public void deliver(OutboxEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new CommonException(INVALID_STATE);
        }
        if (event == null || event.message() == null || event.message().version() != 1
                || !OrderSnapshotEvents.TYPE.equals(event.message().type())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        String tenant = TenantUtil.getTenantId();
        var row = outbox.find(event.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!row.id().equals(event.id()) || !row.type().equals(OrderSnapshotEvents.TYPE)
                || !row.businessId().equals(event.message().businessId()) || !"PROCESSING".equals(row.status())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        var envelope = codec.decode(event.message().payload());
        if (!envelope.aggregateId().equals(row.businessId())) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        if (!client.supportsTenant(tenant)) {
            throw new CommonException(ACTION_FORBIDDEN);
        }
        var ack = client.apply(tenant, envelope);
        if (ack == null || !ack.accepted() || !envelope.deliveryId().equals(ack.deliveryId())
                || !envelope.businessEventId().equals(ack.businessEventId()) || !envelope.producer().equals(ack.producer())
                || !envelope.aggregateId().equals(ack.aggregateId()) || ack.sourceVersion() != envelope.aggregateVersion()
                || ack.projectedVersion() < ack.sourceVersion() || ack.disposition() == null
                || !Set.of("APPLIED", "DUPLICATE", "STALE").contains(ack.disposition())) {
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
        }
    }
}
