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
import io.github.loadup.components.outbox.OutboxEvent;
import io.github.loadup.components.outbox.OutboxMessage;
import io.github.loadup.pay.acquire.client.dto.PaymentOutcomeAckDTO;
import io.github.loadup.pay.payment.app.port.PaymentAcquireClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentAcquireOutcomeDeliveryTest {
    final PaymentOutcomeDeliverySupport support = mock(PaymentOutcomeDeliverySupport.class);
    final PaymentAcquireClient acquire = mock(PaymentAcquireClient.class);
    final PaymentAcquireOutcomeDelivery delivery = new PaymentAcquireOutcomeDelivery(support, acquire);
    final OutboxEvent event = new OutboxEvent("queue-event", new OutboxMessage("tenant", PaymentResultEvents.ACQUIRE, 1, "payment", "{}"),
            OutcomeDeliveryFixtures.snapshot(true, true, PaymentResultEvents.ACQUIRE).envelope().occurredAt().toInstant(), null);

    @BeforeEach
    void setup() {
        when(support.load(event, PaymentResultEvents.ACQUIRE)).thenReturn(OutcomeDeliveryFixtures.snapshot(true, true, PaymentResultEvents.ACQUIRE));
        when(acquire.supportsTenant("tenant")).thenReturn(true);
        when(acquire.accept(eq("tenant"), any())).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new PaymentOutcomeAckDTO("delivery", "evidence", "payment", 2, "evidence", 2, true);
        });
    }

    @Test
    void originalEnvelopeAndIdempotencyIdentityAreRetainedAcrossRetry() {
        delivery.deliver(event);
        delivery.deliver(event);
        verify(acquire, times(2)).accept("tenant", OutcomeDeliveryFixtures.snapshot(true, true, PaymentResultEvents.ACQUIRE).envelope());
    }

    @Test
    void wrongPaymentVersionOrDeliveryCannotCompleteTheQueue() {
        when(acquire.accept(eq("tenant"), any())).thenReturn(new PaymentOutcomeAckDTO("delivery", "evidence", "payment", 3, "evidence", 2, true));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, () -> delivery.deliver(event)).getResultCode());
        when(acquire.accept(eq("tenant"), any())).thenReturn(new PaymentOutcomeAckDTO("other-delivery", "evidence", "payment", 2, "evidence", 2, true));
        assertThrows(CommonException.class, () -> delivery.deliver(event));
    }

    @Test
    void rejectedOrMismatchedEvidenceAckIsNotASuccessfulDelivery() {
        when(acquire.accept(eq("tenant"), any())).thenReturn(new PaymentOutcomeAckDTO("delivery", "evidence", "payment", 2, "other-proof", 2, true));
        assertThrows(CommonException.class, () -> delivery.deliver(event));
        when(acquire.accept(eq("tenant"), any())).thenReturn(new PaymentOutcomeAckDTO("delivery", "evidence", "payment", 2, "evidence", 2, false));
        assertThrows(CommonException.class, () -> delivery.deliver(event));
    }

    @Test
    void downstreamFailurePropagatesForIndependentOutboxRetry() {
        doThrow(new CommonException(DEPENDENCY_UNAVAILABLE)).when(acquire).accept(anyString(), any());
        assertThrows(CommonException.class, () -> delivery.deliver(event));
    }

    @Test
    void unconfiguredTenantCannotSendAnEventUsingAnotherTenantsCredential() {
        when(acquire.supportsTenant("tenant")).thenReturn(false);
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> delivery.deliver(event)).getResultCode());
        verify(acquire, never()).accept(anyString(), any());
    }
}
