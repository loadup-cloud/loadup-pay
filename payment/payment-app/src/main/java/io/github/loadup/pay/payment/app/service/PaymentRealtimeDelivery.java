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
package io.github.loadup.pay.payment.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.outbox.*;
import io.github.loadup.pay.payment.app.port.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Set;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;

/** Complete source snapshots are delivered independently; no source lookup or downstream read participates in Y3. */
@Component
public class PaymentRealtimeDelivery {
    private final OutboxTemplate outbox;
    private final PaymentSnapshotEnvelopeCodec codec;
    private final PaymentRealtimeClient client;
    public PaymentRealtimeDelivery(OutboxTemplate outbox, PaymentSnapshotEnvelopeCodec codec, PaymentRealtimeClient client) {
        this.outbox = outbox; this.codec = codec; this.client = client;
    }
    public void deliver(OutboxEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new CommonException(INVALID_STATE);
        if (event == null || event.message() == null || event.message().version() != 1
                || !PaymentResultEvents.REALTIME.equals(event.message().type())
                || !event.message().tenantId().equals(TenantUtil.getTenantId())) throw new CommonException(SNAPSHOT_INVALID);
        String tenant = event.message().tenantId();
        var row = outbox.find(tenant, event.id()).orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        if (!row.id().equals(event.id()) || !row.tenantId().equals(tenant) || !row.type().equals(PaymentResultEvents.REALTIME)
                || !row.businessId().equals(event.message().businessId()) || !"PROCESSING".equals(row.status()))
            throw new CommonException(SNAPSHOT_INVALID);
        var envelope = codec.decode(event.message().payload());
        if (!envelope.aggregateId().equals(row.businessId())) throw new CommonException(SNAPSHOT_INVALID);
        if (!client.supportsTenant(tenant)) throw new CommonException(ACTION_FORBIDDEN);
        var ack = client.apply(tenant, envelope);
        if (ack == null || !ack.accepted() || !envelope.deliveryId().equals(ack.deliveryId())
                || !envelope.businessEventId().equals(ack.businessEventId()) || !envelope.producer().equals(ack.producer())
                || !envelope.aggregateId().equals(ack.aggregateId()) || ack.sourceVersion() != envelope.aggregateVersion()
                || ack.projectedVersion() < ack.sourceVersion() || ack.disposition() == null
                || !Set.of("APPLIED", "DUPLICATE", "STALE").contains(ack.disposition()))
            throw new CommonException(DEPENDENCY_UNAVAILABLE);
    }
}
