/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.payment.app.service;

import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.outbox.OutboxEvent;
import io.github.loadup.components.outbox.OutboxMessage;
import io.github.loadup.components.outbox.OutboxRecord;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.components.payment.channel.contract.AccessMode;
import io.github.loadup.components.payment.channel.contract.Channel;
import io.github.loadup.contracts.event.payment.PaymentSnapshotEventDTO;
import io.github.loadup.contracts.event.payment.PaymentState;
import io.github.loadup.payment.app.model.realtime.dto.ProjectionAckDTO;
import io.github.loadup.payment.app.port.PaymentRealtimeClient;
import io.github.loadup.payment.app.port.PaymentSnapshotEnvelopeCodec;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PaymentRealtimeDeliveryTest {
    final OutboxTemplate outbox = mock(OutboxTemplate.class);
    final PaymentSnapshotEnvelopeCodec codec = mock(PaymentSnapshotEnvelopeCodec.class);
    final PaymentRealtimeClient client = mock(PaymentRealtimeClient.class);
    final PaymentRealtimeDelivery delivery = new PaymentRealtimeDelivery(outbox, codec, client);
    final EventEnvelope<PaymentSnapshotEventDTO> snapshot = new EventEnvelope<>(
            "delivery",
            "proof",
            "payment",
            PaymentEvents.REALTIME,
            1,
            "payment",
            2,
            BusinessTime.external(AttemptFixtures.NOW),
            new PaymentSnapshotEventDTO(
                    "payment",
                    "order",
                    "merchant",
                    4000,
                    "CNY",
                    PaymentState.SUCCEEDED,
                    Channel.WECHAT,
                    AccessMode.DIRECT,
                    BusinessTime.external(AttemptFixtures.NOW),
                    BusinessTime.external(AttemptFixtures.NOW),
                    2));
    final OutboxEvent event = new OutboxEvent(
            "queue",
            new OutboxMessage(PaymentEvents.REALTIME, 1, "payment", "payload"),
            snapshot.occurredAt().toInstant(),
            null);

    @BeforeEach
    void setup() {
        when(outbox.find("queue"))
                .thenReturn(Optional.of(
                        new OutboxRecord("queue", PaymentEvents.REALTIME, "payment", "PROCESSING", 1, null)));
        when(codec.decode("payload")).thenReturn(snapshot);
        when(client.apply(snapshot)).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new ProjectionAckDTO("delivery", "proof", "payment", "payment", 2, 2, "APPLIED", true);
        });
    }

    void send() {
        TenantUtil.runWithTenant("tenant", () -> delivery.deliver(event));
    }

    @Test
    void sendsTheCompleteOriginalSnapshotWithoutAnyLiveTradingDatabaseLookup() {
        send();
        verify(client).apply(snapshot);
    }

    @Test
    void aStaleSnapshotAckCanCompleteItsIndependentTarget() {
        when(client.apply(snapshot))
                .thenReturn(new ProjectionAckDTO("delivery", "proof", "payment", "payment", 2, 5, "STALE", true));
        assertDoesNotThrow(this::send);
    }

    @Test
    void wrongSourceVersionOrOlderProjectedVersionCannotBeAValidAck() {
        when(client.apply(snapshot))
                .thenReturn(new ProjectionAckDTO("delivery", "proof", "payment", "payment", 3, 3, "APPLIED", true));
        assertSame(
                DEPENDENCY_UNAVAILABLE,
                assertThrows(CommonException.class, this::send).getResultCode());
        when(client.apply(snapshot))
                .thenReturn(new ProjectionAckDTO("delivery", "proof", "payment", "payment", 2, 1, "APPLIED", true));
        assertThrows(CommonException.class, this::send);
    }

    @Test
    void downstreamFailureRemainsAnIndependentRetry() {
        doThrow(new CommonException(DEPENDENCY_UNAVAILABLE)).when(client).apply(any());
        assertThrows(CommonException.class, this::send);
    }

    @Test
    void queueRegistrationCannotBeBypassed() {
        when(outbox.find("queue")).thenReturn(Optional.empty());
        assertThrows(CommonException.class, this::send);
        verify(client, never()).apply(any());
    }
}
