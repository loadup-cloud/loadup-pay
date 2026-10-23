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
import io.github.loadup.pay.acquire.app.converter.AcquireOrderConverter;
import io.github.loadup.pay.acquire.app.converter.PaymentEventConverter;
import io.github.loadup.pay.acquire.app.converter.PaymentInitiationConverter;
import io.github.loadup.pay.acquire.app.converter.PaymentProgressConverter;
import io.github.loadup.pay.acquire.app.port.OrderCreationCodec;
import io.github.loadup.pay.acquire.app.port.PaymentEventCodec;
import io.github.loadup.pay.acquire.app.port.PaymentInitiationCodec;
import io.github.loadup.pay.acquire.app.port.PaymentOutcomeClient;
import io.github.loadup.pay.acquire.app.security.AcquireActor;
import io.github.loadup.pay.acquire.app.security.AcquireServiceActor;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.gateway.OrderFeeGateway;
import io.github.loadup.pay.acquire.domain.gateway.PaymentEventReceiptGateway;
import io.github.loadup.pay.acquire.domain.gateway.PaymentOutcomeGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.AllocationState;
import io.github.loadup.pay.acquire.domain.model.OrderPaymentChange;
import io.github.loadup.pay.acquire.domain.model.PaymentEventReceipt;
import io.github.loadup.pay.acquire.domain.model.PaymentIntent;
import io.github.loadup.pay.acquire.domain.model.PaymentOutcomeReceipt;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.acquire.domain.model.StoredPaymentAllocation;
import io.github.loadup.pay.acquire.domain.model.UnpaidOrderAction;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentOutcomeDTO;
import io.github.loadup.pay.payment.client.dto.PaymentSettlementFactsDTO;
import io.github.loadup.pay.payment.client.enums.PaymentState;
import io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.NOW;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.OWNER;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.UnitTransactions;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.admission;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.clock;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
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

class PaymentOutcomeEventServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(PaymentEventConverter.class.getPackageName());
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final OrderCreationGateway orders = mock(OrderCreationGateway.class);
    final PaymentOutcomeGateway allocations = mock(PaymentOutcomeGateway.class);
    final OrderFeeGateway fees = mock(OrderFeeGateway.class);
    final OrderSnapshotEvents snapshots = mock(OrderSnapshotEvents.class);
    final PaymentOutcomeClient payment = mock(PaymentOutcomeClient.class);
    final PaymentEventReceiptGateway events = mock(PaymentEventReceiptGateway.class);
    final PaymentInitiationCodec intents = mock(PaymentInitiationCodec.class);
    final OrderCreationCodec admissions = mock(OrderCreationCodec.class);
    final PaymentEventCodec digests = mock(PaymentEventCodec.class);
    final AtomicReference<StoredAcquireOrder> order = new AtomicReference<>();
    final AtomicReference<StoredPaymentAllocation> allocation = new AtomicReference<>();
    final AtomicReference<PaymentOutcomeReceipt> outcome = new AtomicReference<>();
    final AtomicReference<PaymentSettlementFactsDTO> facts = new AtomicReference<>();
    final AtomicReference<AcquireServiceActor> actor = new AtomicReference<>(new AcquireServiceActor("tenant", "payment",
            Set.of("pay:acquire:payment:outcome")));
    final Map<String, PaymentEventReceipt> accepted = new HashMap<>();
    final UnitTransactions manager = new UnitTransactions();
    final OrderCreationTransactions transactions = new OrderCreationTransactions(manager);
    final AtomicInteger ids = new AtomicInteger();
    final PaymentOutcomeOperations operations = new PaymentOutcomeOperations(orders, allocations, fees, payment, intents, admissions,
            context.getBean(PaymentInitiationConverter.class), context.getBean(PaymentProgressConverter.class),
            context.getBean(AcquireOrderConverter.class), transactions, () -> "outcome-" + ids.incrementAndGet(), validation.getValidator(), clock(NOW), snapshots);
    final PaymentOutcomeEventService service = new PaymentOutcomeEventService(actor::get, payment, orders, allocations, events, operations,
            digests, context.getBean(PaymentEventConverter.class), transactions, () -> "event-" + ids.incrementAndGet(), clock(NOW), "payment");
    PaymentExecuteCommand command;

    void prepare(long amount, PaymentState state) {
        var original = CheckoutFixtures.order(AcquireOrderState.INIT, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10));
        var intent = new PaymentIntent("allocation", "payment", "merchant/" + OWNER, "action", "f".repeat(64), DomainChecks.cnyAmount(amount, "CNY"));
        var held = original.order().reservePayment(intent, NOW);
        order.set(context.getBean(PaymentProgressConverter.class).withOrder(original, held.order()));
        allocation.set(new StoredPaymentAllocation(held.allocation(), OWNER, "intent", "d".repeat(64), 0));
        command = new PaymentExecuteCommand("payment", "order", "allocation", "admission", "merchant", amount, "CNY", "f".repeat(64),
                new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5), null,
                context.getBean(PaymentInitiationConverter.class).toAdmitted(original, admission()),
                original.subject(), BusinessTime.external(original.order().orderDueAt()));
        facts.set(new PaymentSettlementFactsDTO(new PaymentExecutionDTO("payment", "order", "allocation", amount, "CNY",
                state, "attempt", null, "proof", 2, BusinessTime.external(NOW)), "operation", "merchant", state != PaymentState.SUCCEEDED));
    }

    EventEnvelope<PaymentOutcomeEventDTO> event(String delivery) {
        var f = facts.get();
        var p = f.payment();
        return new EventEnvelope<>(delivery, p.terminalEvidenceId(), "payment", PaymentOutcomeEventService.TYPE, 1,
                "payment", p.version(), BusinessTime.external(NOW), new PaymentOutcomeEventDTO("payment", "order", "allocation",
                f.limitOperationId(), f.payeeMerchantId(), p.amountMinor(), "CNY", p.state(), p.version(), p.terminalEvidenceId(), f.executionFenced()));
    }

    @BeforeEach
    void setup() {
        prepare(10000, PaymentState.SUCCEEDED);
        when(payment.supportsTenant("tenant")).thenReturn(true);
        when(orders.findOrder("tenant", "order")).thenAnswer(c -> Optional.of(order.get()));
        when(orders.lockOrder("tenant", "order")).thenAnswer(c -> {
            active();
            return order.get();
        });
        when(allocations.findPayment("tenant", "order", "payment")).thenAnswer(c -> Optional.of(allocation.get()));
        when(allocations.lockPayment("tenant", "order", "payment")).thenAnswer(c -> {
            active();
            return Optional.of(allocation.get());
        });
        when(allocations.findReceipt("tenant", "allocation")).thenAnswer(c -> Optional.ofNullable(outcome.get()));
        when(intents.decode(any())).thenAnswer(c -> command);
        when(admissions.decodeAdmission(anyString(), anyString())).thenReturn(admission());
        when(events.find(eq("tenant"), anyString())).thenAnswer(c -> Optional.ofNullable(accepted.get(c.getArgument(1))));
        when(digests.digest(anyString(), anyString(), any())).thenAnswer(c ->
                "%064x".formatted(Integer.toUnsignedLong(Objects.hash(c.getArgument(0), c.getArgument(1), c.getArgument(2)))));
        when(payment.facts("tenant", "payment")).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return facts.get();
        });
        when(payment.outcome("tenant", "payment")).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new PaymentOutcomeDTO(facts.get().payment(), facts.get().executionFenced());
        });
        doAnswer(c -> {
            active();
            OrderPaymentChange changed = c.getArgument(1);
            order.set(context.getBean(PaymentProgressConverter.class).withOrder(order.get(), changed.order()));
            var old = allocation.get();
            allocation.set(new StoredPaymentAllocation(changed.allocation(), old.callerScopeHash(), old.intentPayload(), old.payloadHash(), old.rowVersion() + 1));
            outcome.set(c.getArgument(3));
            return null;
        }).when(allocations).apply(eq("tenant"), any(), any(), any(), anyLong());
        doAnswer(c -> {
            active();
            PaymentEventReceipt receipt = c.getArgument(1);
            accepted.put(receipt.deliveryId(), receipt);
            return null;
        }).when(events).insert(eq("tenant"), any());
        doAnswer(c -> {
            active();
            return null;
        }).when(fees).confirmPaid(anyString(), any(), any(), any());
    }

    void active() {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    @Test
    void eventCommitsOriginalAllocationAmountOrderFeeAndSemanticAckInOneTransaction() {
        var ack = service.accept(event("delivery"));
        assertTrue(ack.accepted());
        assertEquals("proof", ack.evidenceId());
        assertEquals(2, ack.paymentVersion());
        assertEquals(AcquireOrderState.PAID, order.get().order().state());
        assertEquals(10000, order.get().order().paidAmountMinor());
        assertEquals(0, order.get().order().paymentReservedMinor());
        assertEquals(AllocationState.CONSUMED, allocation.get().allocation().state());
        assertEquals(ack.orderVersion(), order.get().order().version());
        assertEquals(1, manager.commits);
        verify(fees).confirmPaid(eq("tenant"), any(), any(), any());
        verify(snapshots).publish("tenant", "order", order.get().order().version());
    }

    @Test
    void committedDeliveryReplaysItsOriginalAckWithoutDependingOnPaymentAvailability() {
        var e = event("delivery");
        var ack = service.accept(e);
        clearInvocations(payment, allocations, fees, events);
        doThrow(new CommonException(DEPENDENCY_UNAVAILABLE)).when(payment).facts(anyString(), anyString());
        assertEquals(ack, service.accept(e));
        verify(payment, never()).facts(anyString(), anyString());
        verify(allocations, never()).apply(anyString(), any(), any(), any(), anyLong());
        verifyNoInteractions(fees);
        verify(events, never()).insert(anyString(), any());
    }

    @Test
    void anotherDeliveryOfTheSameFinancialFactCannotCreditTheAllocationTwice() {
        service.accept(event("one"));
        service.accept(event("two"));
        assertEquals(2, accepted.size());
        assertEquals(10000, order.get().order().paidAmountMinor());
        verify(allocations, times(1)).apply(eq("tenant"), any(), any(), any(), anyLong());
        verify(fees, times(1)).confirmPaid(eq("tenant"), any(), any(), any());
        verify(snapshots, times(1)).publish(eq("tenant"), eq("order"), anyLong());
    }

    @Test
    void eventAfterMerchantRefreshReusesTheAlreadyCommittedOutcomeReceipt() {
        operations.refresh(new AcquireActor("tenant", OWNER, null, Set.of()),
                new io.github.loadup.pay.acquire.client.command.PaymentProgressRefreshCommand("order", "payment"));
        service.accept(event("delivery"));
        verify(allocations, times(1)).apply(eq("tenant"), any(), any(), any(), anyLong());
        verify(fees, times(1)).confirmPaid(eq("tenant"), any(), any(), any());
    }

    @Test
    void partialSuccessfulPaymentLeavesTheOrderPendingAndDoesNotConfirmTheFullOrderFee() {
        prepare(4000, PaymentState.SUCCEEDED);
        service.accept(event("delivery"));
        assertEquals(AcquireOrderState.PENDING, order.get().order().state());
        assertEquals(4000, order.get().order().paidAmountMinor());
        assertEquals(0, order.get().order().paymentReservedMinor());
        verifyNoInteractions(fees);
    }

    @Test
    void provenNoCollectionReleasesTheOriginalHoldWithoutIncreasingPaidAmount() {
        prepare(4000, PaymentState.FAILED);
        service.accept(event("delivery"));
        assertEquals(0, order.get().order().paidAmountMinor());
        assertEquals(0, order.get().order().paymentReservedMinor());
        assertEquals(AllocationState.RELEASED, allocation.get().allocation().state());
        verifyNoInteractions(fees);
    }

    @Test
    void sameDeliveryCannotChangeTheCanonicalPayloadAfterItWasAccepted() {
        var original = event("delivery");
        service.accept(original);
        var altered = new EventEnvelope<>(original.deliveryId(), original.businessEventId(), original.producer(), original.eventType(),
                1, original.aggregateId(), original.aggregateVersion(), original.occurredAt().plusSeconds(1), original.data());
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service.accept(altered)).getResultCode());
    }

    @Test
    void eventCannotSubstituteAnotherLimitOperationForTheAuthoritativePayment() {
        var original = event("delivery");
        var d = original.data();
        var altered = new EventEnvelope<>(original.deliveryId(), original.businessEventId(), original.producer(), original.eventType(),
                1, original.aggregateId(), original.aggregateVersion(), original.occurredAt(),
                new PaymentOutcomeEventDTO(d.paymentId(), d.orderId(), d.allocationId(), "other-operation", d.payeeMerchantId(),
                        d.amountMinor(), d.currency(), d.state(), d.paymentVersion(), d.evidenceId(), d.executionFenced()));
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> service.accept(altered)).getResultCode());
        verify(allocations, never()).apply(anyString(), any(), any(), any(), anyLong());
    }

    @Test
    void staleSourceVersionCannotBeAcknowledgedAsTheCurrentTerminalFact() {
        var original = event("delivery");
        var p = facts.get().payment();
        facts.set(new PaymentSettlementFactsDTO(new PaymentExecutionDTO(p.paymentId(), p.orderId(), p.allocationId(), p.amountMinor(), p.currency(),
                p.state(), p.activeAttemptId(), null, p.terminalEvidenceId(), 3, p.initiatedAt()), "operation", "merchant", false));
        assertThrows(CommonException.class, () -> service.accept(original));
        assertTrue(accepted.isEmpty());
    }

    @Test
    void wrongServiceOrMissingEventPermissionIsRejectedBeforeFinancialReads() {
        actor.set(new AcquireServiceActor("tenant", "other-service", Set.of("pay:acquire:payment:outcome")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.accept(event("delivery"))).getResultCode());
        actor.set(new AcquireServiceActor("tenant", "payment", Set.of()));
        assertThrows(CommonException.class, () -> service.accept(event("delivery")));
        verifyNoInteractions(orders, allocations, events);
    }

    @Test
    void snapshotFailureCannotCommitTheFinancialTransitionOrItsEventAck() {
        doThrow(new CommonException(DEPENDENCY_UNAVAILABLE)).when(snapshots).publish(anyString(), anyString(), anyLong());
        assertThrows(CommonException.class, () -> service.accept(event("delivery")));
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
        assertTrue(accepted.isEmpty());
        verify(events, never()).insert(anyString(), any());
        // Financial rollback still needs real MySQL acceptance; these mocks check the transaction boundary.
    }

    @Test
    void eventReceiptFailureRollsBackTheWholeLocalAcceptanceBoundary() {
        doThrow(new CommonException(DEPENDENCY_UNAVAILABLE)).when(events).insert(eq("tenant"), any());
        assertThrows(CommonException.class, () -> service.accept(event("delivery")));
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
        // Mock maps are not a database rollback proof. Real MySQL acceptance remains required.
    }

    @Test
    void callerCannotHoldAnOuterTransactionWhilePaymentAuthorityIsQueried() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> service.accept(event("delivery"))).getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(orders, payment, events);
    }
}
