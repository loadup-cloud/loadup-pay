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

import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.payment.channel.api.model.ChannelPaymentOutcome;
import io.github.loadup.components.task.TaskSubmission;
import io.github.loadup.components.task.TaskTemplate;
import io.github.loadup.contracts.event.BusinessEventTypes;
import io.github.loadup.payment.app.converter.PaymentControlConverter;
import io.github.loadup.payment.app.converter.PaymentRealtimeConverter;
import io.github.loadup.payment.app.converter.PaymentResultConverter;
import io.github.loadup.payment.app.dispatch.PaymentDeliveryRoutes;
import io.github.loadup.payment.app.dispatch.PaymentFactRoutes;
import io.github.loadup.payment.client.error.PaymentResultCodeEnum;
import io.github.loadup.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.payment.domain.gateway.PaymentResultGateway;
import io.github.loadup.payment.domain.model.ChannelTransactionClaim;
import io.github.loadup.payment.domain.model.NoCollectionEvidence;
import io.github.loadup.payment.domain.model.RiskDecision;
import io.github.loadup.payment.domain.model.StoredPaymentAcceptance;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

class PaymentEventsTest {
    final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(PaymentResultConverter.class.getPackageName());
    final TaskTemplate outbox = mock(TaskTemplate.class);
    final AtomicInteger ids = new AtomicInteger();
    final PaymentAttemptGateway attempts = mock(PaymentAttemptGateway.class);
    final PaymentResultGateway results = mock(PaymentResultGateway.class);
    final PaymentEvents events = new PaymentEvents(
            outbox,
            context.getBean(PaymentResultConverter.class),
            () -> "delivery-" + ids.incrementAndGet(),
            attempts,
            results,
            context.getBean(PaymentRealtimeConverter.class),
            PaymentFactRoutes.defaults());

    StoredPaymentAcceptance failed() {
        var original = AttemptFixtures.accepted(true);
        var order = original.payment().fail(new NoCollectionEvidence("evidence", "payment", true));
        return context.getBean(PaymentControlConverter.class).withPayment(original, order, "evidence");
    }

    private void publish(StoredPaymentAcceptance payment, boolean fenced, LocalDateTime at) {
        events.publish(payment, fenced, at);
    }

    @AfterEach
    void close() {
        TransactionSynchronizationManager.clear();
        context.close();
    }

    @BeforeEach
    void setup() {
        when(attempts.find("attempt"))
                .thenReturn(Optional.of(AttemptFixtures.prepared().closeBeforeSubmission("evidence", null)));
    }

    @Test
    void eachTargetGetsAnIndependentDeliveryOfTheSameFrozenBusinessFact() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        publish(failed(), true, AttemptFixtures.NOW);
        var messages = ArgumentCaptor.forClass(TaskSubmission.class);
        verify(outbox, times(4)).submit(messages.capture());
        assertEquals(
                Set.of(
                        PaymentDeliveryRoutes.ACQUIRE,
                        PaymentDeliveryRoutes.LIMIT,
                        PaymentDeliveryRoutes.REALTIME,
                        PaymentDeliveryRoutes.RISK),
                new HashSet<>(messages.getAllValues().stream()
                        .map(TaskSubmission::taskType)
                        .toList()));
        var deliveries = new HashSet<String>();
        var mapper = JsonMapper.builder().build();
        for (var message : messages.getAllValues()) {
            Map<?, ?> envelope = mapper.readValue(message.payload(), Map.class);
            var data = (Map<?, ?>) envelope.get("data");
            assertEquals("payment", message.bizId());
            assertEquals("evidence", envelope.get("businessEventId"));
            assertEquals(
                    message.taskType().equals(PaymentDeliveryRoutes.REALTIME)
                            ? BusinessEventTypes.PAYMENT_SNAPSHOT
                            : BusinessEventTypes.PAYMENT_FINANCIAL,
                    envelope.get("eventType"));
            assertEquals("payment", envelope.get("aggregateId"));
            assertEquals(2, ((Number) envelope.get("aggregateVersion")).longValue());
            assertEquals("FAILED", data.get("state"));
            if (message.taskType().equals(PaymentDeliveryRoutes.REALTIME)) {
                assertNotNull(data.get("initiatedAt"));
                assertEquals("WECHAT", data.get("channel"));
                assertFalse(data.containsKey("executionFenced"));
            } else {
                assertEquals(Boolean.TRUE, data.get("executionFenced"));
            }
            deliveries.add((String) envelope.get("deliveryId"));
        }
        assertEquals(4, deliveries.size());
    }

    @Test
    void publishingWithoutTheY3TransactionIsRejected() {
        assertSame(
                INVALID_STATE,
                assertThrows(CommonException.class, () -> publish(failed(), true, AttemptFixtures.NOW))
                        .getResultCode());
        verifyNoInteractions(outbox);
    }

    @Test
    void initProcessingUnknownAndClosingPublishOnlyTheirOwnCompleteVersionedSnapshots() {
        var init = AttemptFixtures.accepted(false);
        var processing = AttemptFixtures.accepted(true);
        var submitted = AttemptFixtures.prepared()
                .beginSubmission(1, "e".repeat(64), RiskDecision.notConnected(), AttemptFixtures.NOW);
        var unknownAttempt = submitted.applyOutcome(
                new ChannelPaymentOutcome(
                        ChannelPaymentOutcome.Kind.UNKNOWN,
                        submitted.attempt().identity(),
                        submitted.attempt().amount(),
                        "query-receipt"),
                false);
        var updates = context.getBean(PaymentControlConverter.class);
        var unknown =
                updates.withPayment(processing, processing.payment().applyAttempt(unknownAttempt.attempt()), null);
        var closing = updates.withPayment(unknown, unknown.payment().requestClose(), null);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        events.publishSnapshot(init, AttemptFixtures.NOW);
        when(attempts.find("attempt")).thenReturn(Optional.of(AttemptFixtures.prepared()));
        events.publishSnapshot(processing, AttemptFixtures.NOW);
        when(attempts.find("attempt")).thenReturn(Optional.of(unknownAttempt));
        events.publishSnapshot(unknown, AttemptFixtures.NOW.plusSeconds(1));
        events.publishSnapshot(closing, AttemptFixtures.NOW.plusSeconds(2));
        var messages = ArgumentCaptor.forClass(TaskSubmission.class);
        verify(outbox, times(4)).submit(messages.capture());
        var expectedStates = List.of("INIT", "PROCESSING", "UNKNOWN", "CLOSING");
        var seenEvents = new HashSet<String>();
        var mapper = JsonMapper.builder().build();
        for (int index = 0; index < messages.getAllValues().size(); index++) {
            var message = messages.getAllValues().get(index);
            assertEquals(PaymentDeliveryRoutes.REALTIME, message.taskType());
            Map<?, ?> envelope = mapper.readValue(message.payload(), Map.class);
            var data = (Map<?, ?>) envelope.get("data");
            assertEquals(expectedStates.get(index), data.get("state"));
            assertEquals(index, ((Number) envelope.get("aggregateVersion")).longValue());
            assertEquals(index, ((Number) data.get("sourceVersion")).longValue());
            assertEquals(4000L, ((Number) data.get("amountMinor")).longValue());
            assertEquals("CNY", data.get("currency"));
            assertNotNull(data.get("initiatedAt"));
            Assertions.assertNull(data.get("succeededAt"));
            assertFalse(data.containsKey("executionFenced"));
            if (index == 0) {
                Assertions.assertNull(data.get("channel"));
            } else {
                assertEquals("WECHAT", data.get("channel"));
            }
            assertNotNull(envelope.get("businessEventId"));
            seenEvents.add((String) envelope.get("businessEventId"));
        }
        assertEquals(4, seenEvents.size());
        verifyNoInteractions(results);
    }

    @Test
    void readOnlyMissingAttemptAndNonterminalFinancialPublicationAreRejected() {
        var processing = AttemptFixtures.accepted(true);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertSame(
                INVALID_STATE,
                assertThrows(CommonException.class, () -> events.publishSnapshot(processing, AttemptFixtures.NOW))
                        .getResultCode());
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        when(attempts.find("attempt")).thenReturn(Optional.empty());
        assertSame(
                PaymentResultCodeEnum.SNAPSHOT_INVALID,
                assertThrows(CommonException.class, () -> events.publishSnapshot(processing, AttemptFixtures.NOW))
                        .getResultCode());
        assertSame(
                PaymentResultCodeEnum.SNAPSHOT_INVALID,
                assertThrows(CommonException.class, () -> publish(processing, false, AttemptFixtures.NOW))
                        .getResultCode());
        verifyNoInteractions(outbox);
    }

    @Test
    void completeSuccessSnapshotUsesOriginalChannelIdentityAndClaimTime() {
        var submitted = AttemptFixtures.prepared()
                .beginSubmission(1, "e".repeat(64), RiskDecision.notConnected(), AttemptFixtures.NOW);
        var succeeded = submitted.applyOutcome(
                new ChannelPaymentOutcome(
                        ChannelPaymentOutcome.Kind.SUCCEEDED,
                        submitted.attempt().identity(),
                        submitted.attempt().amount(),
                        "evidence"),
                false);
        when(attempts.find("attempt")).thenReturn(Optional.of(succeeded));
        when(results.attemptClaim("attempt"))
                .thenReturn(Optional.of(new ChannelTransactionClaim(
                        "claim",
                        succeeded.attempt().identity(),
                        succeeded.attempt().amount(),
                        "channel-transaction",
                        AttemptFixtures.NOW.plusSeconds(1),
                        "evidence")));
        var original = AttemptFixtures.accepted(true);
        var payment = context.getBean(PaymentControlConverter.class)
                .withPayment(original, original.payment().applyAttempt(succeeded.attempt()), "evidence");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        publish(payment, false, AttemptFixtures.NOW.plusMinutes(1));
        var messages = ArgumentCaptor.forClass(TaskSubmission.class);
        verify(outbox, times(4)).submit(messages.capture());
        var message = messages.getAllValues().stream()
                .filter(m -> m.taskType().equals(PaymentDeliveryRoutes.REALTIME))
                .findFirst()
                .orElseThrow();
        Map<?, ?> envelope = JsonMapper.builder().build().readValue(message.payload(), Map.class);
        var data = (Map<?, ?>) envelope.get("data");
        assertEquals("WECHAT", data.get("channel"));
        assertEquals("DIRECT", data.get("accessMode"));
        assertNotNull(data.get("succeededAt"));
        assertNotNull(data.get("initiatedAt"));
    }
}
