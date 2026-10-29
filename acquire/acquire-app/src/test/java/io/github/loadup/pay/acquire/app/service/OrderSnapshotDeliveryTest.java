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
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.outbox.OutboxEvent;
import io.github.loadup.components.outbox.OutboxMessage;
import io.github.loadup.components.outbox.OutboxRecord;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.pay.acquire.app.port.AcquireRealtimeClient;
import io.github.loadup.pay.acquire.app.port.OrderSnapshotEnvelopeCodec;
import io.github.loadup.pay.acquire.client.event.OrderSnapshotEventDTO;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.realtime.client.dto.ProjectionAckDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderSnapshotDeliveryTest {
    final OutboxTemplate outbox = mock(OutboxTemplate.class);
    final OrderSnapshotEnvelopeCodec codec = mock(OrderSnapshotEnvelopeCodec.class);
    final AcquireRealtimeClient client = mock(AcquireRealtimeClient.class);
    final OrderSnapshotDelivery delivery = new OrderSnapshotDelivery(outbox, codec, client);
    final EventEnvelope<OrderSnapshotEventDTO> snapshot = new EventEnvelope<>("delivery", "change", "acquire",
            OrderSnapshotEvents.TYPE, 1, "order", 2, BusinessTime.external(CheckoutFixtures.NOW),
            new OrderSnapshotEventDTO("order", "merchant", "M001", 10000, "CNY", 4000, 2000, AcquireOrderState.PENDING,
                    BusinessTime.external(CheckoutFixtures.NOW.minusDays(1)), BusinessTime.external(CheckoutFixtures.NOW), 2));
    final OutboxEvent event = new OutboxEvent("queue", new OutboxMessage("tenant", OrderSnapshotEvents.TYPE, 1, "order", "payload"),
            snapshot.occurredAt().toInstant(), null);

    @BeforeEach
    void setup() {
        when(outbox.find("tenant", "queue")).thenReturn(Optional.of(new OutboxRecord("queue", "tenant", OrderSnapshotEvents.TYPE, "order", "PROCESSING", 1, null)));
        when(codec.decode("payload")).thenReturn(snapshot);
        when(client.supportsTenant("tenant")).thenReturn(true);
        when(client.apply("tenant", snapshot)).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new ProjectionAckDTO("delivery", "change", "acquire", "order", 2, 2, "APPLIED", true);
        });
    }

    void send() {
        TenantUtil.runWithTenant("tenant", () -> delivery.deliver(event));
    }

    @Test
    void retriesTheOriginalSnapshotAndIdentityWithoutReadingTheCurrentOrder() {
        send();
        send();
        verify(client, times(2)).apply("tenant", snapshot);
    }

    @Test
    void aLaterProjectionMayAcknowledgeAnOlderSnapshotAsStale() {
        when(client.apply("tenant", snapshot)).thenReturn(new ProjectionAckDTO("delivery", "change", "acquire", "order", 2, 5, "STALE", true));
        assertDoesNotThrow(this::send);
    }

    @Test
    void wrongDeliveryOrSourceVersionCannotAcknowledgeThisTarget() {
        when(client.apply("tenant", snapshot)).thenReturn(new ProjectionAckDTO("other", "change", "acquire", "order", 2, 2, "APPLIED", true));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, this::send).getResultCode());
        when(client.apply("tenant", snapshot)).thenReturn(new ProjectionAckDTO("delivery", "change", "acquire", "order", 3, 3, "APPLIED", true));
        assertThrows(CommonException.class, this::send);
    }

    @Test
    void anotherTenantOrUnclaimedQueueCannotSend() {
        assertThrows(CommonException.class, () -> TenantUtil.runWithTenant("other", () -> delivery.deliver(event)));
        when(outbox.find("tenant", "queue")).thenReturn(Optional.of(new OutboxRecord("queue", "tenant", OrderSnapshotEvents.TYPE, "order", "SUCCEEDED", 1, null)));
        assertThrows(CommonException.class, this::send);
        verify(client, never()).apply(anyString(), any());
    }

    @Test
    void downstreamFailureLeavesTheTargetAvailableForDispatcherRetry() {
        doThrow(new CommonException(DEPENDENCY_UNAVAILABLE)).when(client).apply(anyString(), any());
        assertThrows(CommonException.class, this::send);
    }

    @Test
    void outerTransactionCannotContainProjectionNetworkIo() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(INVALID_STATE, assertThrows(CommonException.class, this::send).getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(client);
    }
}
