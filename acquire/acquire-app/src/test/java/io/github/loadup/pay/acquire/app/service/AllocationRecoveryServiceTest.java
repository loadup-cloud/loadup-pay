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
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.delayedtask.*;
import io.github.loadup.pay.acquire.client.command.PaymentProgressRefreshCommand;
import io.github.loadup.pay.acquire.domain.gateway.*;
import io.github.loadup.pay.acquire.domain.model.*;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.pay.payment.client.dto.PaymentCloseDTO;
import io.github.loadup.pay.product.client.dto.AdmittedOrderTermsDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.*;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AllocationRecoveryServiceTest {
    final AllocationRecoveryGateway cursors = mock(AllocationRecoveryGateway.class);
    final DelayedTaskTemplate tasks = mock(DelayedTaskTemplate.class);
    final OrderCreationGateway orders = mock(OrderCreationGateway.class);
    final PaymentOutcomeGateway allocations = mock(PaymentOutcomeGateway.class);
    final PaymentInitiationOperations initiation = mock(PaymentInitiationOperations.class);
    final PaymentOutcomeOperations outcomes = mock(PaymentOutcomeOperations.class);
    final UnitTransactions manager = new UnitTransactions();
    final OrderCreationTransactions transactions = new OrderCreationTransactions(manager);
    final AllocationRecoveryQueue queue = new AllocationRecoveryQueue(cursors, tasks, 30);
    final AllocationRecoveryService service = new AllocationRecoveryService(cursors, queue, tasks, orders,
            allocations, initiation, outcomes, transactions, clock(NOW));
    final AtomicReference<AllocationRecoveryCursor> cursor = new AtomicReference<>(
            AllocationRecoveryCursor.init("payment", "order", NOW));
    final AtomicReference<StoredAcquireOrder> order = new AtomicReference<>();
    final AtomicReference<StoredPaymentAllocation> allocation = new AtomicReference<>();
    final AtomicReference<PaymentOutcomeReceipt> receipt = new AtomicReference<>();
    final PaymentChoiceDTO choice = new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5);
    final PaymentExecuteCommand command = new PaymentExecuteCommand("payment", "order", "allocation", "admission", "merchant",
            1000L, "CNY", "e".repeat(64), choice, "original-input",
            new AdmittedOrderTermsDTO("admission", "order", "merchant", 10000L, "CNY", "contract", 1, 1L,
                    "snapshot", "b".repeat(64), terms()), "Original subject", BusinessTime.external(NOW.minusSeconds(1)));
    final DelayedTask task = nativeTask(NOW.plusMinutes(5));

    DelayedTask nativeTask(java.time.LocalDateTime leaseUntil) {
        return new DelayedTask("task", "tenant", AllocationRecoveryQueue.TYPE, "payment.0", NOW, NOW,
                DelayedTaskStatus.RUNNING, 1, 5, leaseUntil, null);
    }

    @BeforeEach
    void setup() {
        var original = CheckoutFixtures.order(AcquireOrderState.INIT, UnpaidOrderAction.CLOSE, NOW.minusSeconds(1));
        var change = original.order().reservePayment(new PaymentIntent("allocation", "payment", "checkout/session", "action",
                "e".repeat(64), Money.ofMinor(1000L, "CNY")), NOW.minusMinutes(1));
        order.set(withOrder(original, change.order()));
        allocation.set(new StoredPaymentAllocation(change.allocation(), OWNER, "original-payload", "f".repeat(64), 0L));
        when(tasks.find(AllocationRecoveryQueue.TYPE, "payment.0")).thenReturn(Optional.of(task));
        when(cursors.find("tenant", "payment")).thenAnswer(c -> Optional.of(cursor.get()));
        when(cursors.lock("tenant", "payment")).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return Optional.of(cursor.get());
        });
        when(orders.findOrder("tenant", "order")).thenAnswer(c -> Optional.of(order.get()));
        when(orders.lockOrder("tenant", "order")).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return order.get();
        });
        when(allocations.lockPayment("tenant", "order", "payment")).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return Optional.of(allocation.get());
        });
        when(allocations.findReceipt("tenant", "allocation")).thenAnswer(c -> Optional.ofNullable(receipt.get()));
        when(outcomes.bound(any(), any(), any())).thenReturn(command);
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(cursor.get(), c.getArgument(1));
            cursor.set(c.getArgument(2));
            return null;
        }).when(cursors).update(eq("tenant"), any(), any());
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("tenant", TenantUtil.getTenantId());
            return null;
        }).when(tasks).register(anyString(), anyString(), any());
        when(initiation.recover(any(), eq("order"), eq("action"), eq(1000L), eq(choice))).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(manager.commits > 0);
            PaymentInitiationPrincipal principal = c.getArgument(0);
            assertEquals(OWNER, principal.scopeHash());
            assertEquals("checkout/session", principal.actionScope());
            principal.authorize(order.get(), false);
            assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class,
                    () -> principal.authorize(order.get(), true)).getResultCode());
            return new PaymentIntentRecovery(PaymentIntentRecovery.State.RESOLVED, "payment", null);
        });
    }

    void handle() { TenantUtil.runWithTenant("tenant", () -> service.handle(task)); }

    @Test
    void committedIntentContinuesPastTheOrderDeadlineWithoutRenewingSessionOrCreatingPayment() {
        when(outcomes.refresh(any(), any())).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        });
        handle();
        assertEquals(1L, cursor.get().generation());
        assertEquals(NOW.plusSeconds(30), cursor.get().dueAt());
        assertFalse(cursor.get().completed());
        verify(outcomes).refresh(new OrderPaymentScope("tenant", OWNER), new PaymentProgressRefreshCommand("order", "payment"));
        verify(tasks).register(AllocationRecoveryQueue.TYPE, "payment.1", NOW.plusSeconds(30));
        verify(initiation, never()).start(any(), any());
        assertEquals(2, manager.commits);
    }

    @Test
    void missingInputRetainsTheHoldAndSchedulesAnotherGenerationWithoutFinancialInference() {
        when(initiation.recover(any(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(new PaymentIntentRecovery(PaymentIntentRecovery.State.INPUT_REQUIRED, null, null));
        handle();
        assertEquals(AllocationState.HELD, allocation.get().allocation().state());
        assertEquals(1000L, order.get().order().paymentReservedMinor());
        assertNull(receipt.get());
        assertEquals(1L, cursor.get().generation());
        verify(outcomes, never()).refresh(any(), any());
        verify(outcomes, never()).applyInputExpiry(any(), anyString(), anyString(), any());
    }

    @Test
    void expiredInputProofUsesA3AndOnlyItsBoundReceiptCompletesRecovery() {
        var proof = new PaymentCloseDTO("payment", "order", 1L, true, null, "no-collection");
        when(initiation.recover(any(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(new PaymentIntentRecovery(PaymentIntentRecovery.State.RESOLVED, "payment", proof));
        when(outcomes.applyInputExpiry(any(), anyString(), anyString(), any())).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            settle(VerifiedPaymentOutcome.Kind.NOT_ACCEPTED);
            return null;
        });
        handle();
        assertTrue(cursor.get().completed());
        assertEquals(0L, cursor.get().generation());
        assertEquals(0L, order.get().order().paymentReservedMinor());
        verify(outcomes).applyInputExpiry(new OrderPaymentScope("tenant", OWNER), "order", "payment", proof);
        verify(outcomes, never()).refresh(any(), any());
        verify(tasks, never()).register(anyString(), anyString(), any());
    }

    @Test
    void existingSuccessReceiptCompletesWithoutAnotherRemoteOperation() {
        settle(VerifiedPaymentOutcome.Kind.SUCCEEDED);
        handle();
        assertTrue(cursor.get().completed());
        verifyNoInteractions(initiation);
        verify(outcomes, never()).refresh(any(), any());
        verify(tasks, never()).register(anyString(), anyString(), any());
    }

    @Test
    void anotherWorkerAdvancingDuringTheRpcPreventsADuplicateSuccessor() {
        when(outcomes.refresh(any(), any())).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            cursor.set(cursor.get().schedule(NOW.plusSeconds(30), 0L));
            return null;
        });
        handle();
        assertEquals(1L, cursor.get().generation());
        verify(cursors, never()).update(anyString(), any(), any());
        verify(tasks, never()).register(anyString(), anyString(), any());
    }

    @Test
    void lockOrderIsStableAcrossInspectionAndTheSuccessorTransaction() {
        handle();
        var sequence = inOrder(orders, allocations, cursors);
        sequence.verify(cursors).find("tenant", "payment");
        sequence.verify(orders).lockOrder("tenant", "order");
        sequence.verify(allocations).lockPayment("tenant", "order", "payment");
        sequence.verify(cursors).lock("tenant", "payment");
        sequence.verify(allocations).findReceipt("tenant", "allocation");
        sequence.verify(orders).findOrder("tenant", "order");
        sequence.verify(orders).lockOrder("tenant", "order");
        sequence.verify(allocations).lockPayment("tenant", "order", "payment");
        sequence.verify(cursors).lock("tenant", "payment");
    }

    @Test
    void settledAllocationWithoutReceiptCannotStopRecovery() {
        settle(VerifiedPaymentOutcome.Kind.NO_COLLECTION);
        receipt.set(null);
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, this::handle).getResultCode());
        assertFalse(cursor.get().completed());
        verifyNoInteractions(initiation);
        verify(cursors, never()).update(anyString(), any(), any());
    }

    @Test
    void obsoleteOrCompletedWakeupsNeverQueryOrScheduleAgain() {
        cursor.set(cursor.get().schedule(NOW.plusSeconds(30), 0L));
        handle();
        cursor.set(cursor.get().finish());
        handle();
        verifyNoInteractions(initiation, outcomes);
        verify(tasks, never()).register(anyString(), anyString(), any());
        verify(cursors, never()).update(anyString(), any(), any());
    }

    @Test
    void dependencyFailureRetriesTheSameOriginalIntentWithoutAdvancingTheCursor() {
        var failure = new CommonException(DEPENDENCY_UNAVAILABLE);
        when(initiation.recover(any(), anyString(), anyString(), anyLong(), any())).thenThrow(failure);
        assertSame(failure, assertThrows(CommonException.class, this::handle));
        assertEquals(0L, cursor.get().generation());
        assertFalse(cursor.get().completed());
        verify(tasks, never()).register(anyString(), anyString(), any());
        verify(outcomes, never()).refresh(any(), any());
        verify(cursors, never()).update(anyString(), any(), any());
    }

    @Test
    void anAbsentOriginalIntentOrDifferentPaymentCannotBecomeNewAcceptance() {
        when(initiation.recover(any(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(new PaymentIntentRecovery(PaymentIntentRecovery.State.NOT_OBSERVED, null, null));
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, this::handle).getResultCode());
        when(initiation.recover(any(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(new PaymentIntentRecovery(PaymentIntentRecovery.State.RESOLVED, "other-payment", null));
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, this::handle).getResultCode());
        verify(initiation, never()).start(any(), any());
        verify(outcomes, never()).refresh(any(), any());
        verify(tasks, never()).register(anyString(), anyString(), any());
    }

    @Test
    void nativeLeaseTenantAndOuterTransactionAreRequiredBeforeBusinessLocks() {
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class,
                () -> TenantUtil.runWithTenant("another-tenant", () -> service.handle(task))).getResultCode());
        when(tasks.find(AllocationRecoveryQueue.TYPE, "payment.0")).thenReturn(Optional.of(nativeTask(NOW)));
        assertSame(INVALID_STATE, assertThrows(CommonException.class, this::handle).getResultCode());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(INVALID_STATE, assertThrows(CommonException.class, this::handle).getResultCode());
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(orders, allocations, cursors, initiation, outcomes);
    }

    @Test
    void anIncorrectNativeDeadlineCannotRunTheOriginalPayment() {
        cursor.set(AllocationRecoveryCursor.init("payment", "order", NOW.plusSeconds(1)));
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, this::handle).getResultCode());
        verifyNoInteractions(initiation, outcomes);
    }

    void settle(VerifiedPaymentOutcome.Kind kind) {
        var before = allocation.get();
        var outcome = new VerifiedPaymentOutcome("order", "payment", before.allocation().amount(), kind,
                kind == VerifiedPaymentOutcome.Kind.NOT_ACCEPTED ? 0L : 3L, "evidence", true);
        var change = order.get().order().applyPaymentOutcome(before.allocation(), outcome);
        order.set(withOrder(order.get(), change.order()));
        allocation.set(new StoredPaymentAllocation(change.allocation(), OWNER, before.intentPayload(), before.payloadHash(), 1L));
        receipt.set(new PaymentOutcomeReceipt("receipt", "allocation", outcome, NOW));
    }

    static StoredAcquireOrder withOrder(StoredAcquireOrder original, AcquireOrder changed) {
        return new StoredAcquireOrder(changed, original.merchantOrderNo(), original.subject(), original.admission(),
                original.admittedAt(), original.callerScopeHash(), original.snapshotId(), original.admissionPayload(), original.payloadHash());
    }
}
