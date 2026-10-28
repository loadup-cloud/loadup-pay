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
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.components.outbox.OutboxMessage;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.pay.channel.api.model.ChannelRefundOutcome;
import io.github.loadup.pay.payment.app.converter.RefundExecutionConverter;
import io.github.loadup.pay.payment.app.converter.RefundProjectionConverter;
import io.github.loadup.pay.payment.app.converter.RefundSettlementConverter;
import io.github.loadup.pay.payment.app.model.RefundSnapshotFacts;
import io.github.loadup.pay.payment.app.model.RefundSummaryFacts;
import io.github.loadup.pay.payment.app.port.RefundSnapshotReader;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.RefundSummaryGateway;
import io.github.loadup.pay.payment.domain.model.OrderRefundSummary;
import io.github.loadup.pay.payment.domain.model.PaymentOrder;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.domain.model.RefundExecutionFacts;
import io.github.loadup.pay.payment.domain.model.RefundProgressChange;
import io.github.loadup.pay.payment.domain.model.RefundState;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RefundProjectionEventsTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(RefundProjectionConverter.class.getPackageName());
    final RefundSummaryGateway summaries = mock(RefundSummaryGateway.class);
    final RefundSnapshotReader reader = mock(RefundSnapshotReader.class);
    final PaymentAcceptanceGateway payments = mock(PaymentAcceptanceGateway.class);
    final OutboxTemplate outbox = mock(OutboxTemplate.class);
    final AtomicReference<OrderRefundSummary> summary = new AtomicReference<>();
    final AtomicReference<RefundProgressChange> current = new AtomicReference<>();
    final AtomicInteger ids = new AtomicInteger();
    final PaymentAcceptanceServiceTest.UnitTransactions manager = new PaymentAcceptanceServiceTest.UnitTransactions();
    final PaymentAcceptanceTransactions transactions = new PaymentAcceptanceTransactions(manager);
    final RefundProjectionEvents events = new RefundProjectionEvents(summaries, reader, context.getBean(RefundProjectionConverter.class),
            outbox, () -> "delivery-" + ids.incrementAndGet(), payments, context.getBean(RefundSettlementConverter.class));
    final LocalDateTime createdAt = RefundResultFixtures.NOW.minusSeconds(30);

    @BeforeEach
    void setup() {
        when(payments.find("tenant", "payment")).thenReturn(Optional.of(successfulPayment()));
        when(summaries.lock("tenant", "order")).thenAnswer(c -> Optional.ofNullable(summary.get()));
        doAnswer(c -> {
            summary.set(c.getArgument(1));
            return null;
        }).when(summaries).insert(eq("tenant"), any());
        doAnswer(c -> {
            summary.set(c.getArgument(2));
            return null;
        }).when(summaries).update(eq("tenant"), any(), any());
        when(reader.summary("tenant", "order")).thenAnswer(c -> new RefundSummaryFacts(summary.get(), RefundResultFixtures.NOW));
        when(reader.child(eq("tenant"), anyString())).thenAnswer(c -> {
            var original = RefundResultFixtures.initial().allocations().stream().filter(a -> a.refund().id().equals(c.getArgument(1))).findFirst().orElseThrow();
            var child = current.get() != null && current.get().refund().id().equals(original.refund().id()) ? current.get().refund() : original.refund();
            return new RefundSnapshotFacts(child.id(), child.refundRequestId(), child.paymentId(), "order", "merchant", child.amount(),
                    child.state().name(), createdAt, RefundResultFixtures.NOW, child.state() == RefundState.SUCCEEDED ? RefundResultFixtures.NOW : null, child.version());
        });
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("tenant", TenantUtil.getTenantId());
            return null;
        }).when(outbox).publish(any());
    }

    StoredPaymentAcceptance successfulPayment() {
        var capacity = RefundResultFixtures.capacity();
        var payment = new PaymentOrder("payment", "order", "allocation", "merchant", capacity.succeededAmount(), "operation",
                RefundResultFixtures.NOW.minusHours(1), PaymentState.SUCCEEDED, "attempt", "attempt", 2);
        return new StoredPaymentAcceptance(payment, "admission", "a".repeat(64), "payment-service", "{}", "b".repeat(64), "payment-evidence");
    }

    @AfterEach
    void close() {
        context.close();
    }

    void plan() {
        transactions.writeLocal("tenant", () -> {
            events.planned("tenant", RefundResultFixtures.initial(), DomainChecks.cnyAmount(10000, "CNY"));
            return true;
        });
    }

    RefundProgressChange change(ChannelRefundOutcome.Kind kind) {
        var stored = RefundResultFixtures.initial();
        var child = stored.allocations().getFirst();
        var started = child.refund().beginSubmission();
        var request = stored.request().apply(started);
        var before = new RefundExecutionFacts(context.getBean(RefundExecutionConverter.class).withRefund(child, started), RefundResultFixtures.NOW, null, null);
        var outcome = RefundResultFixtures.capacity().applyOutcome(started, new ChannelRefundOutcome(started.id(), kind, started.amount(), "receipt"));
        return new RefundProgressChange(before, outcome.refund(), request, request.apply(outcome.refund()), RefundResultFixtures.capacity(), outcome.capacity(),
                RefundResultFixtures.NOW, kind == ChannelRefundOutcome.Kind.SUCCEEDED ? "refund-transaction" : null,
                kind == ChannelRefundOutcome.Kind.SUCCEEDED ? RefundResultFixtures.NOW : null);
    }

    void changed(RefundProgressChange change) {
        current.set(change);
        transactions.writeLocal("tenant", () -> {
            events.changed("tenant", change);
            return true;
        });
    }

    @Test
    void planningFreezesEveryInitChildAndOneBoundedOrderMonetarySnapshot() {
        plan();
        var capture = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox, times(4)).publish(capture.capture());
        assertEquals(1500, summary.get().reservedMinor());
        assertEquals(1, summary.get().version());
        var message = capture.getAllValues().stream().filter(m -> m.type().equals(RefundProjectionEvents.SUMMARY)).findFirst().orElseThrow();
        var feeMessage = capture.getAllValues().stream().filter(m -> m.type().equals(RefundProjectionEvents.FEE)).findFirst().orElseThrow();
        var json = JsonMapper.builder().build();
        Map<?, ?> envelope = json.readValue(message.payload(), Map.class);
        Map<?, ?> feeEnvelope = json.readValue(feeMessage.payload(), Map.class);
        var data = (Map<?, ?>) envelope.get("data");
        var feeData = (Map<?, ?>) feeEnvelope.get("data");
        assertFalse(data.containsKey("requests"));
        assertTrue(message.payload().length() < 2048);
        assertEquals(1500, ((Number) data.get("refundReservedMinor")).longValue());
        assertEquals("order", envelope.get("aggregateId"));
        assertEquals(RefundProjectionEvents.FEE, feeMessage.type());
        assertEquals(RefundProjectionEvents.FEE, feeEnvelope.get("eventType"));
        assertEquals("order", feeEnvelope.get("aggregateId"));
        assertEquals(envelope.get("aggregateVersion"), feeEnvelope.get("aggregateVersion"));
        assertFalse(envelope.get("deliveryId").equals(feeEnvelope.get("deliveryId")));
        assertFalse(envelope.get("businessEventId").equals(feeEnvelope.get("businessEventId")));
        assertEquals(data.get("orderId"), feeData.get("orderId"));
        assertEquals(data.get("refundedMinor"), feeData.get("refundedMinor"));
        assertEquals(data.get("refundReservedMinor"), feeData.get("refundReservedMinor"));
        assertEquals(data.get("refundSummaryVersion"), feeData.get("refundSummaryVersion"));
        var child = capture.getAllValues().stream().filter(m -> m.type().equals(RefundProjectionEvents.CHILD)).findFirst().orElseThrow();
        Map<?, ?> childEnvelope = json.readValue(child.payload(), Map.class);
        var childData = (Map<?, ?>) childEnvelope.get("data");
        assertEquals(0, ((Number) childData.get("sourceVersion")).longValue());
        assertEquals("INIT", childData.get("state"));
        assertEquals(BusinessTime.external(createdAt).toInstant(), java.time.OffsetDateTime.parse((String) childData.get("refundCreatedAt")).toInstant());
    }

    @Test
    void successPublishesIndependentFinancialAndMerchantEvents() {
        plan();
        clearInvocations(outbox, summaries);
        changed(change(ChannelRefundOutcome.Kind.SUCCEEDED));
        assertEquals(1000, summary.get().refundedMinor());
        assertEquals(500, summary.get().reservedMinor());
        assertEquals(2, summary.get().version());
        var capture = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox, times(5)).publish(capture.capture());
        verify(summaries).update(eq("tenant"), any(), any());
        var limitMessage = capture.getAllValues().stream().filter(m -> m.type().equals(RefundProjectionEvents.LIMIT)).findFirst().orElseThrow();
        var json = JsonMapper.builder().build();
        Map<?, ?> limitEnvelope = json.readValue(limitMessage.payload(), Map.class);
        var data = (Map<?, ?>) limitEnvelope.get("data");
        assertEquals("refund", limitEnvelope.get("aggregateId"));
        assertEquals(2, ((Number) limitEnvelope.get("aggregateVersion")).longValue());
        assertEquals("refund", data.get("refundId"));
        assertEquals("SUCCEEDED", data.get("state"));
        assertEquals("refund-transaction", data.get("evidenceId"));
        assertEquals(1000, ((Number) data.get("cumulativePaymentRefundedMinor")).longValue());
        assertEquals(BusinessTime.external(RefundResultFixtures.NOW).toInstant(),
                java.time.OffsetDateTime.parse((String) limitEnvelope.get("occurredAt")).toInstant());
        verify(payments).find("tenant", "payment");
        var merchant = capture.getAllValues().stream().filter(m -> m.type().equals(RefundProjectionEvents.MERCHANT)).findFirst().orElseThrow();
        Map<?, ?> merchantEnvelope = json.readValue(merchant.payload(), Map.class);
        var refund = (Map<?, ?>) merchantEnvelope.get("data");
        assertEquals("RefundOrder", refund.get("aggregateType"));
        assertEquals("R001", refund.get("merchantRefundNo"));
        assertEquals(1000, ((Number) refund.get("succeededMinor")).longValue());
        assertEquals(0, ((Number) refund.get("failedMinor")).longValue());
        assertFalse(merchantEnvelope.get("deliveryId").equals(limitEnvelope.get("deliveryId")));
        assertFalse(merchant.payload().contains("refund-transaction"));
    }

    @Test
    void unknownPublishesOnlyTheChildAndKeepsTheOrderMonetaryVersion() {
        plan();
        clearInvocations(outbox, summaries);
        changed(change(ChannelRefundOutcome.Kind.UNKNOWN));
        assertEquals(0, summary.get().refundedMinor());
        assertEquals(1500, summary.get().reservedMinor());
        assertEquals(1, summary.get().version());
        verify(outbox, times(1)).publish(argThat(m -> m.type().equals(RefundProjectionEvents.CHILD)));
        verify(summaries, never()).update(anyString(), any(), any());
        verifyNoInteractions(payments);
    }

    @Test
    void lastFailedChildAlsoPublishesTheCompletedPartialRequest() {
        plan();
        var first = change(ChannelRefundOutcome.Kind.SUCCEEDED);
        changed(first);
        clearInvocations(outbox);
        var second = RefundResultFixtures.initial().allocations().get(1);
        var started = second.refund().beginSubmission();
        var request = first.request().apply(started);
        var capacity = new io.github.loadup.pay.payment.domain.model.PaymentRefundCapacity("payment2", "order", "merchant",
                second.originalSucceededAt(), DomainChecks.cnyAmount(4000, "CNY"), 0, 500, 1);
        var outcome = capacity.applyOutcome(started, new ChannelRefundOutcome(started.id(),
                ChannelRefundOutcome.Kind.FAILED_WITH_NO_REFUND, started.amount(), "failure-receipt"));
        var before = new RefundExecutionFacts(context.getBean(RefundExecutionConverter.class).withRefund(second, started),
                RefundResultFixtures.NOW, null, null);
        changed(new RefundProgressChange(before, outcome.refund(), request, request.apply(outcome.refund()), capacity, outcome.capacity(),
                RefundResultFixtures.NOW, null, null));

        var capture = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox, times(5)).publish(capture.capture());
        var merchant = capture.getAllValues().stream().filter(m -> m.type().equals(RefundProjectionEvents.MERCHANT)).toList();
        assertEquals(2, merchant.size());
        var message = merchant.stream().filter(m -> m.businessId().equals("request")).findFirst().orElseThrow();
        var envelope = JsonMapper.builder().build().readValue(message.payload(), Map.class);
        var data = (Map<?, ?>) envelope.get("data");
        assertEquals("RefundRequest", data.get("aggregateType"));
        assertEquals("PARTIAL_SUCCEEDED", data.get("state"));
        assertEquals(1500, ((Number) data.get("amountMinor")).longValue());
        assertEquals(1000, ((Number) data.get("succeededMinor")).longValue());
        assertEquals(500, ((Number) data.get("failedMinor")).longValue());
        assertEquals(1000, summary.get().refundedMinor());
        assertEquals(0, summary.get().reservedMinor());
    }

    @Test
    void definiteFailureOnlyReleasesTheHoldAndDoesNotCountAsReturnedMoney() {
        plan();
        clearInvocations(outbox, summaries);
        changed(change(ChannelRefundOutcome.Kind.FAILED_WITH_NO_REFUND));
        assertEquals(0, summary.get().refundedMinor());
        assertEquals(500, summary.get().reservedMinor());
        assertEquals(2, summary.get().version());
        var capture = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox, times(4)).publish(capture.capture());
        var merchant = capture.getAllValues().stream().filter(m -> m.type().equals(RefundProjectionEvents.MERCHANT)).findFirst().orElseThrow();
        var envelope = JsonMapper.builder().build().readValue(merchant.payload(), Map.class);
        var data = (Map<?, ?>) envelope.get("data");
        assertEquals("FAILED", data.get("state"));
        assertEquals(0, ((Number) data.get("succeededMinor")).longValue());
        assertEquals(1000, ((Number) data.get("failedMinor")).longValue());
        verifyNoInteractions(payments);
    }

    @Test
    void outboxFailureAbortsTheWholeOwningTransactionBoundary() {
        doThrow(new CommonException(DEPENDENCY_UNAVAILABLE)).when(outbox).publish(any());
        assertThrows(CommonException.class, this::plan);
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
        // The mock manager checks propagation only; real summary/request/capacity rollback requires PostgreSQL acceptance.
    }

    @Test
    void anotherTenantOrMissingTransactionCannotPublish() {
        assertSame(INVALID_STATE, assertThrows(CommonException.class,
                () -> events.planned("tenant", RefundResultFixtures.initial(), DomainChecks.cnyAmount(10000, "CNY"))).getResultCode());
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> transactions.writeLocal("other",
                () -> {
                    events.planned("tenant", RefundResultFixtures.initial(), DomainChecks.cnyAmount(10000, "CNY"));
                    return true;
                })).getResultCode());
        verifyNoInteractions(summaries, outbox);
    }
}
