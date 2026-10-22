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
import io.github.loadup.components.outbox.*;
import io.github.loadup.pay.payment.app.converter.*;
import io.github.loadup.pay.payment.domain.model.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentResultEventsTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(PaymentResultConverter.class.getPackageName());
    final OutboxTemplate outbox = mock(OutboxTemplate.class);
    final AtomicInteger ids = new AtomicInteger();
    final PaymentResultEvents events = new PaymentResultEvents(outbox, context.getBean(PaymentResultConverter.class),
            () -> "delivery-" + ids.incrementAndGet());
    StoredPaymentAcceptance failed() {
        var original = AttemptFixtures.accepted(true);
        var order = original.payment().fail(new NoCollectionEvidence("evidence", "payment", true));
        return context.getBean(PaymentControlConverter.class).withPayment(original, order, "evidence");
    }
    @AfterEach void close() { TransactionSynchronizationManager.setActualTransactionActive(false); context.close(); }

    @Test void eachTargetGetsAnIndependentDeliveryOfTheSameFrozenBusinessFact() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        events.publish("tenant", failed(), true, AttemptFixtures.NOW);
        var messages = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox, times(4)).publish(messages.capture());
        assertEquals(Set.of(PaymentResultEvents.ACQUIRE, PaymentResultEvents.LIMIT, PaymentResultEvents.REALTIME, PaymentResultEvents.RISK),
                new HashSet<>(messages.getAllValues().stream().map(OutboxMessage::type).toList()));
        var deliveries = new HashSet<String>();
        var mapper = JsonMapper.builder().build();
        for (var message : messages.getAllValues()) {
            Map<?, ?> envelope = mapper.readValue(message.payload(), Map.class);
            var data = (Map<?, ?>) envelope.get("data");
            assertEquals("tenant", message.tenantId()); assertEquals("payment", message.businessId());
            assertEquals("evidence", envelope.get("businessEventId"));
            assertEquals(message.type(), envelope.get("eventType"));
            assertEquals("payment", envelope.get("aggregateId"));
            assertEquals(2, ((Number) envelope.get("aggregateVersion")).longValue());
            assertEquals("FAILED", data.get("state"));
            assertEquals(Boolean.TRUE, data.get("executionFenced"));
            deliveries.add((String) envelope.get("deliveryId"));
        }
        assertEquals(4, deliveries.size());
    }
    @Test void publishingWithoutTheY3TransactionIsRejected() {
        assertSame(INVALID_STATE, assertThrows(CommonException.class,
                () -> events.publish("tenant", failed(), true, AttemptFixtures.NOW)).getResultCode());
        verifyNoInteractions(outbox);
    }
}
