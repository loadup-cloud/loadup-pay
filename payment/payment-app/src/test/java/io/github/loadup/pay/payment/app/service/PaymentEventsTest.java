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
import io.github.loadup.components.outbox.OutboxMessage;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.pay.payment.app.converter.PaymentControlConverter;
import io.github.loadup.pay.payment.app.converter.PaymentRealtimeConverter;
import io.github.loadup.pay.payment.app.converter.PaymentResultConverter;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentResultGateway;
import io.github.loadup.pay.channel.api.model.ChannelPaymentOutcome;
import io.github.loadup.pay.payment.domain.model.ChannelTransactionClaim;
import io.github.loadup.pay.payment.domain.model.NoCollectionEvidence;
import io.github.loadup.pay.payment.domain.model.RiskDecision;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
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

class PaymentEventsTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(PaymentResultConverter.class.getPackageName());
    final OutboxTemplate outbox = mock(OutboxTemplate.class);
    final AtomicInteger ids = new AtomicInteger();
    final PaymentAttemptGateway attempts = mock(PaymentAttemptGateway.class);
    final PaymentResultGateway results = mock(PaymentResultGateway.class);
    final PaymentEvents events = new PaymentEvents(outbox, context.getBean(PaymentResultConverter.class),
            () -> "delivery-" + ids.incrementAndGet(), attempts, results, context.getBean(PaymentRealtimeConverter.class));

    StoredPaymentAcceptance failed() {
        var original = AttemptFixtures.accepted(true);
        var order = original.payment().fail(new NoCollectionEvidence("evidence", "payment", true));
        return context.getBean(PaymentControlConverter.class).withPayment(original, order, "evidence");
    }

    private void publish(String tenant, StoredPaymentAcceptance payment, boolean fenced, java.time.LocalDateTime at) {
        TenantUtil.runWithTenant(tenant, () -> events.publish(tenant, payment, fenced, at));
    }

    @AfterEach
    void close() {
        TransactionSynchronizationManager.clear();
        context.close();
    }

    @BeforeEach
    void setup() {
        when(attempts.find("tenant", "attempt")).thenReturn(Optional.of(AttemptFixtures.prepared().closeBeforeSubmission("evidence", null)));
    }

    @Test
    void eachTargetGetsAnIndependentDeliveryOfTheSameFrozenBusinessFact() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        publish("tenant", failed(), true, AttemptFixtures.NOW);
        var messages = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox, times(4)).publish(messages.capture());
        assertEquals(Set.of(PaymentEvents.ACQUIRE, PaymentEvents.LIMIT, PaymentEvents.REALTIME, PaymentEvents.RISK),
                new HashSet<>(messages.getAllValues().stream().map(OutboxMessage::type).toList()));
        var deliveries = new HashSet<String>();
        var mapper = JsonMapper.builder().build();
        for (var message : messages.getAllValues()) {
            Map<?, ?> envelope = mapper.readValue(message.payload(), Map.class);
            var data = (Map<?, ?>) envelope.get("data");
            assertEquals("tenant", message.tenantId());
            assertEquals("payment", message.businessId());
            assertEquals("evidence", envelope.get("businessEventId"));
            assertEquals(message.type(), envelope.get("eventType"));
            assertEquals("payment", envelope.get("aggregateId"));
            assertEquals(2, ((Number) envelope.get("aggregateVersion")).longValue());
            assertEquals("FAILED", data.get("state"));
            if (message.type().equals(PaymentEvents.REALTIME)) {
                assertNotNull(data.get("initiatedAt"));
                assertEquals("WECHAT", data.get("channel"));
                assertFalse(data.containsKey("executionFenced"));
            } else assertEquals(Boolean.TRUE, data.get("executionFenced"));
            deliveries.add((String) envelope.get("deliveryId"));
        }
        assertEquals(4, deliveries.size());
    }

    @Test
    void publishingWithoutTheY3TransactionIsRejected() {
        assertSame(INVALID_STATE, assertThrows(CommonException.class,
                () -> publish("tenant", failed(), true, AttemptFixtures.NOW)).getResultCode());
        verifyNoInteractions(outbox);
    }

    @Test
    void initProcessingUnknownAndClosingPublishOnlyTheirOwnCompleteVersionedSnapshots() {
        var init = AttemptFixtures.accepted(false);
        var processing = AttemptFixtures.accepted(true);
        var submitted = AttemptFixtures.prepared().beginSubmission(1, "e".repeat(64), RiskDecision.notConnected(), AttemptFixtures.NOW);
        var unknownAttempt = submitted.applyOutcome(new ChannelPaymentOutcome(ChannelPaymentOutcome.Kind.UNKNOWN,
                submitted.attempt().identity(), submitted.attempt().amount(), "query-receipt"), false);
        var updates = context.getBean(PaymentControlConverter.class);
        var unknown = updates.withPayment(processing, processing.payment().applyAttempt(unknownAttempt.attempt()), null);
        var closing = updates.withPayment(unknown, unknown.payment().requestClose(), null);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TenantUtil.runWithTenant("tenant", () -> {
            events.publishSnapshot("tenant", init, AttemptFixtures.NOW);
            when(attempts.find("tenant", "attempt")).thenReturn(Optional.of(AttemptFixtures.prepared()));
            events.publishSnapshot("tenant", processing, AttemptFixtures.NOW);
            when(attempts.find("tenant", "attempt")).thenReturn(Optional.of(unknownAttempt));
            events.publishSnapshot("tenant", unknown, AttemptFixtures.NOW.plusSeconds(1));
            events.publishSnapshot("tenant", closing, AttemptFixtures.NOW.plusSeconds(2));
        });
        var messages = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox, times(4)).publish(messages.capture());
        var expectedStates = java.util.List.of("INIT", "PROCESSING", "UNKNOWN", "CLOSING");
        var seenEvents = new HashSet<String>();
        var mapper = JsonMapper.builder().build();
        for (int index = 0; index < messages.getAllValues().size(); index++) {
            var message = messages.getAllValues().get(index);
            assertEquals(PaymentEvents.REALTIME, message.type());
            Map<?, ?> envelope = mapper.readValue(message.payload(), Map.class);
            var data = (Map<?, ?>) envelope.get("data");
            assertEquals(expectedStates.get(index), data.get("state"));
            assertEquals(index, ((Number) envelope.get("aggregateVersion")).longValue());
            assertEquals(index, ((Number) data.get("sourceVersion")).longValue());
            assertEquals(4000L, ((Number) data.get("amountMinor")).longValue());
            assertEquals("CNY", data.get("currency"));
            assertNotNull(data.get("initiatedAt"));
            org.junit.jupiter.api.Assertions.assertNull(data.get("succeededAt"));
            assertFalse(data.containsKey("executionFenced"));
            if (index == 0) org.junit.jupiter.api.Assertions.assertNull(data.get("channel"));
            else assertEquals("WECHAT", data.get("channel"));
            assertNotNull(envelope.get("businessEventId"));
            seenEvents.add((String) envelope.get("businessEventId"));
        }
        assertEquals(4, seenEvents.size());
        verifyNoInteractions(results);
    }

    @Test
    void readOnlyWrongTenantMissingAttemptAndNonterminalFinancialPublicationAreRejected() {
        var processing = AttemptFixtures.accepted(true);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertSame(io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> TenantUtil.runWithTenant("other", () ->
                        events.publishSnapshot("tenant", processing, AttemptFixtures.NOW))).getResultCode());
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> TenantUtil.runWithTenant("tenant", () ->
                events.publishSnapshot("tenant", processing, AttemptFixtures.NOW))).getResultCode());
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        when(attempts.find("tenant", "attempt")).thenReturn(Optional.empty());
        assertSame(io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID,
                assertThrows(CommonException.class, () -> TenantUtil.runWithTenant("tenant", () ->
                        events.publishSnapshot("tenant", processing, AttemptFixtures.NOW))).getResultCode());
        assertSame(io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID,
                assertThrows(CommonException.class, () -> publish("tenant", processing, false, AttemptFixtures.NOW)).getResultCode());
        verifyNoInteractions(outbox);
    }

    @Test
    void completeSuccessSnapshotUsesOriginalChannelIdentityAndClaimTime() {
        var submitted = AttemptFixtures.prepared().beginSubmission(1, "e".repeat(64), RiskDecision.notConnected(), AttemptFixtures.NOW);
        var succeeded = submitted.applyOutcome(new ChannelPaymentOutcome(ChannelPaymentOutcome.Kind.SUCCEEDED,
                submitted.attempt().identity(), submitted.attempt().amount(), "evidence"), false);
        when(attempts.find("tenant", "attempt")).thenReturn(Optional.of(succeeded));
        when(results.attemptClaim("tenant", "attempt")).thenReturn(Optional.of(new ChannelTransactionClaim("claim", "tenant",
                succeeded.attempt().identity(), succeeded.attempt().amount(), "channel-transaction", AttemptFixtures.NOW.plusSeconds(1), "evidence")));
        var original = AttemptFixtures.accepted(true);
        var payment = context.getBean(PaymentControlConverter.class).withPayment(original, original.payment().applyAttempt(succeeded.attempt()), "evidence");
        TransactionSynchronizationManager.setActualTransactionActive(true);
        publish("tenant", payment, false, AttemptFixtures.NOW.plusMinutes(1));
        var messages = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox, times(4)).publish(messages.capture());
        var message = messages.getAllValues().stream().filter(m -> m.type().equals(PaymentEvents.REALTIME)).findFirst().orElseThrow();
        Map<?, ?> envelope = JsonMapper.builder().build().readValue(message.payload(), Map.class);
        var data = (Map<?, ?>) envelope.get("data");
        assertEquals("WECHAT", data.get("channel"));
        assertEquals("DIRECT", data.get("accessMode"));
        assertNotNull(data.get("succeededAt"));
        assertNotNull(data.get("initiatedAt"));
    }
}
