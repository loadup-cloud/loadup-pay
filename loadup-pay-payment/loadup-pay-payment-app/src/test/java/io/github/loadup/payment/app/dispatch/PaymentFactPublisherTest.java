/*
 * Copyright (C) 2026 LoadUp Cloud
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
package io.github.loadup.payment.app.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.task.TaskSubmission;
import io.github.loadup.components.task.TaskTemplate;
import io.github.loadup.contracts.event.BusinessEventTypes;
import io.github.loadup.contracts.event.BusinessFact;
import io.github.loadup.contracts.event.payment.PaymentFinancialEvidence;
import io.github.loadup.contracts.event.payment.PaymentOutcomeEventDTO;
import io.github.loadup.contracts.event.payment.PaymentState;
import io.github.loadup.payment.client.error.PaymentResultCodeEnum;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

class PaymentFactPublisherTest {
    private final TaskTemplate outbox = mock(TaskTemplate.class);
    private final AtomicInteger ids = new AtomicInteger();
    private final BusinessFact<PaymentOutcomeEventDTO> fact = new BusinessFact<>(
            "evidence",
            "payment",
            BusinessEventTypes.PAYMENT_FINANCIAL,
            1,
            "payment-id",
            2,
            OffsetDateTime.parse("2026-11-15T12:00:00+08:00"),
            new PaymentOutcomeEventDTO(
                            "payment-id",
                            "order",
                            "allocation",
                            "operation",
                            "merchant",
                            100,
                            "CNY",
                            PaymentState.SUCCEEDED,
                            2,
                            "evidence",
                            false,
                            null)
                    .withFinancialEvidence(new PaymentFinancialEvidence(
                            1,
                            "payment-id",
                            "order",
                            "allocation",
                            "operation",
                            "merchant",
                            100,
                            "CNY",
                            PaymentState.SUCCEEDED,
                            2,
                            "evidence",
                            "attempt",
                            OffsetDateTime.parse("2026-11-15T11:59:00+08:00"),
                            OffsetDateTime.parse("2026-11-15T12:00:00+08:00"),
                            false)));

    @AfterEach
    void clear() {
        TransactionSynchronizationManager.clear();
    }

    private PaymentFactPublisher publisher(PaymentFactRoutes routes) {
        return new PaymentFactPublisher(outbox, () -> "delivery-" + ids.incrementAndGet(), routes);
    }

    @Test
    void oneFactCreatesIndependentTargetDeliveriesWithIdenticalBusinessIdentityAndPayload() throws Exception {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        publisher(PaymentFactRoutes.defaults()).publish(fact);
        var messages = ArgumentCaptor.forClass(TaskSubmission.class);
        verify(outbox, times(3)).submit(messages.capture());
        assertEquals(
                List.of(PaymentDeliveryRoutes.ACQUIRE, PaymentDeliveryRoutes.LIMIT, PaymentDeliveryRoutes.RISK),
                messages.getAllValues().stream().map(TaskSubmission::taskType).toList());
        var json = JsonMapper.builder().build();
        var deliveries = new ArrayList<Map>();
        for (var message : messages.getAllValues()) {
            var delivery = json.readValue(message.payload(), Map.class);
            assertEquals(BusinessEventTypes.PAYMENT_FINANCIAL, delivery.get("eventType"));
            assertEquals("evidence", delivery.get("businessEventId"));
            assertEquals("payment-id", delivery.get("aggregateId"));
            assertEquals("payment-id", message.bizId());
            assertEquals(1, message.payloadVersion());
            deliveries.add(delivery);
        }
        assertEquals(
                3,
                deliveries.stream()
                        .map(delivery -> delivery.get("deliveryId"))
                        .distinct()
                        .count());
        assertEquals(deliveries.get(0).get("data"), deliveries.get(1).get("data"));
        assertEquals(deliveries.get(0).get("data"), deliveries.get(2).get("data"));
    }

    @Test
    void addingASubscriptionDoesNotChangeTheFactOrItsProducer() {
        var routes = new ArrayList<>(PaymentFactRoutes.defaults().subscriptions());
        routes.add(new PaymentFactRoutes.Subscription(
                BusinessEventTypes.PAYMENT_FINANCIAL, "pay.payment.additional-delivery.v1"));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        publisher(new PaymentFactRoutes(routes)).publish(fact);
        verify(outbox, times(4)).submit(ArgumentMatchers.any(TaskSubmission.class));
        assertEquals("evidence", fact.businessEventId());
        assertEquals(BusinessEventTypes.PAYMENT_FINANCIAL, fact.eventType());
        assertNotEquals(fact.delivery("one").deliveryId(), fact.delivery("two").deliveryId());
        assertSame(fact.data(), fact.delivery("one").data());
    }

    @Test
    void missingOrDuplicateRequiredRoutesCannotSilentlyDropAnExistingConsumer() {
        var missing = new ArrayList<>(PaymentFactRoutes.defaults().subscriptions());
        missing.removeFirst();
        assertThrows(IllegalArgumentException.class, () -> new PaymentFactRoutes(missing));
        var duplicate = new ArrayList<>(PaymentFactRoutes.defaults().subscriptions());
        duplicate.add(duplicate.getFirst());
        assertThrows(IllegalArgumentException.class, () -> new PaymentFactRoutes(duplicate));
    }

    @Test
    void transactionAndEnqueueFailuresKeepTheirExistingFailureBoundary() {
        var publisher = publisher(PaymentFactRoutes.defaults());
        var failure = assertThrows(CommonException.class, () -> publisher.publish(fact));
        assertSame(PaymentResultCodeEnum.INVALID_STATE, failure.getResultCode());
        verifyNoInteractions(outbox);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertThrows(CommonException.class, () -> publisher.publish(fact));
        verifyNoInteractions(outbox);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        var unavailable = new CommonException(PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE);
        doThrow(unavailable).when(outbox).submit(ArgumentMatchers.any(TaskSubmission.class));
        assertSame(unavailable, assertThrows(CommonException.class, () -> publisher.publish(fact)));
    }

    @Test
    void routingConfigurationHasOneOwnerAndAllowsAnExplicitExtendedRegistry() {
        try (var context = new AnnotationConfigApplicationContext(PaymentFactDeliveryConfiguration.class)) {
            assertEquals(1, context.getBeansOfType(PaymentFactRoutes.class).size());
            assertEquals(
                    9, context.getBean(PaymentFactRoutes.class).subscriptions().size());
        }
        var routes = new ArrayList<>(PaymentFactRoutes.defaults().subscriptions());
        routes.add(new PaymentFactRoutes.Subscription(BusinessEventTypes.PAYMENT_FINANCIAL, "additional.route.v1"));
        var configured = new PaymentFactRoutes(routes);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(PaymentFactRoutes.class, () -> configured);
            context.register(PaymentFactDeliveryConfiguration.class);
            context.refresh();
            assertEquals(1, context.getBeansOfType(PaymentFactRoutes.class).size());
            assertSame(configured, context.getBean(PaymentFactRoutes.class));
        }
    }
}
