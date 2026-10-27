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
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.delayedtask.*;
import io.github.loadup.pay.acquire.app.converter.AcquireRecoveryConverter;
import io.github.loadup.pay.acquire.app.security.AcquireServiceActor;
import io.github.loadup.pay.acquire.client.command.AcquireRecoveryRetryCommand;
import io.github.loadup.pay.acquire.client.enums.AcquireRecoveryKind;
import io.github.loadup.pay.acquire.client.query.AcquireRecoveryTaskQuery;
import io.github.loadup.pay.acquire.domain.gateway.*;
import io.github.loadup.pay.acquire.domain.model.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.*;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AcquireRecoveryOperationsServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AcquireRecoveryConverter.class.getPackageName());
    final jakarta.validation.ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AtomicReference<AcquireServiceActor> actor = new AtomicReference<>(new AcquireServiceActor("tenant", "pay-ops",
            Set.of("pay:acquire:recovery:read", "pay:acquire:recovery:retry")));
    final AcquireRecoveryRetryGateway receipts = mock(AcquireRecoveryRetryGateway.class);
    final OrderCreationGateway orders = mock(OrderCreationGateway.class);
    final OrderExtensionGateway extensions = mock(OrderExtensionGateway.class);
    final OrderClosureGateway closes = mock(OrderClosureGateway.class);
    final PaymentOutcomeGateway allocations = mock(PaymentOutcomeGateway.class);
    final AllocationRecoveryGateway cursors = mock(AllocationRecoveryGateway.class);
    final AllocationRecoveryService paymentRecovery = mock(AllocationRecoveryService.class);
    final OrderClosureOperations closure = mock(OrderClosureOperations.class);
    final DelayedTaskTemplate tasks = mock(DelayedTaskTemplate.class);
    final UnitTransactions manager = new UnitTransactions();
    final OrderCreationTransactions transactions = new OrderCreationTransactions(manager);
    final Map<String, AcquireRecoveryRetryReceipt> ledger = new HashMap<>();
    final Map<String, DelayedTask> nativeTasks = new HashMap<>();
    final AtomicReference<StoredAcquireOrder> order = new AtomicReference<>();
    final AtomicReference<AllocationRecoveryCursor> cursor = new AtomicReference<>(AllocationRecoveryCursor.init("payment", "order", NOW));
    final StoredPaymentAllocation allocation = new StoredPaymentAllocation(new OrderPaymentAllocation("allocation", "order", "payment",
            "merchant/" + OWNER, "action", "e".repeat(64), Money.ofMinor(1000L, "CNY"), AllocationState.HELD, 0L), OWNER,
            "original-command", "f".repeat(64), 0L);
    final AcquireRecoveryOperationsService service = new AcquireRecoveryOperationsService(actor::get, receipts, orders, extensions, closes,
            allocations, cursors, paymentRecovery, closure, tasks, context.getBean(AcquireRecoveryConverter.class), transactions,
            () -> "retry-receipt", validation.getValidator(), clock(NOW), "pay-ops");

    @BeforeEach
    void setup() {
        var original = CheckoutFixtures.order(AcquireOrderState.INIT, UnpaidOrderAction.CLOSE, NOW);
        var changed = original.order().reservePayment(new PaymentIntent("allocation", "payment", "merchant/" + OWNER, "action",
                "e".repeat(64), allocation.allocation().amount()), NOW.minusMinutes(1));
        order.set(withOrder(original, changed.order()));
        when(orders.findOrder("tenant", "order")).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return Optional.of(order.get());
        });
        when(allocations.findPayment("tenant", "order", "payment")).thenReturn(Optional.of(allocation));
        when(orders.lockOrder("tenant", "order")).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("tenant", TenantUtil.getTenantId());
            return order.get();
        });
        when(cursors.find("tenant", "payment")).thenAnswer(c -> Optional.of(cursor.get()));
        when(cursors.lock("tenant", "payment")).thenAnswer(c -> Optional.of(cursor.get()));
        when(allocations.lockPayment("tenant", "order", "payment")).thenReturn(Optional.of(allocation));
        when(paymentRecovery.isCurrent(eq("tenant"), any(), eq(allocation), any(), eq(0L))).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return true;
        });
        when(extensions.deadlineVersion(eq("tenant"), any())).thenReturn(1L);
        when(tasks.find(anyString(), anyString())).thenAnswer(c -> Optional.ofNullable(nativeTasks.get(c.getArgument(0) + "/" + c.getArgument(1))));
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            var key = c.getArgument(0) + "/" + c.getArgument(1);
            var before = nativeTasks.get(key);
            assertEquals(DelayedTaskStatus.FAILED, before.status());
            nativeTasks.put(key, new DelayedTask(before.id(), "tenant", before.taskType(), before.bizId(), before.executeAt(), NOW,
                    DelayedTaskStatus.PENDING, 0, before.maxAttempts(), null, null));
            return null;
        }).when(tasks).retryFailed(anyString(), anyString());
        when(receipts.find(eq("tenant"), anyString(), anyString())).thenAnswer(c -> Optional.ofNullable(ledger.get(c.getArgument(1) + "/" + c.getArgument(2))));
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            AcquireRecoveryRetryReceipt receipt = c.getArgument(1);
            ledger.put(receipt.callerScopeHash() + "/" + receipt.requestKey(), receipt);
            return null;
        }).when(receipts).insert(eq("tenant"), any());
        put(AllocationRecoveryQueue.TYPE, "payment.0", NOW, NOW, DelayedTaskStatus.FAILED);
    }
    @AfterEach void close() { context.close(); validation.close(); }

    AcquireRecoveryRetryCommand command(AcquireRecoveryKind kind, String subject, long generation, String expectedId, int attempts,
            LocalDateTime next, String key, String reason) {
        return new AcquireRecoveryRetryCommand(kind, subject, generation, expectedId, attempts, BusinessTime.external(next), key, reason);
    }
    AcquireRecoveryRetryCommand command() { return command(AcquireRecoveryKind.PAYMENT_ALLOCATION, "payment", 0L, "native-task", 5, NOW, "operation", "Dependency restored"); }
    void put(String type, String biz, LocalDateTime due, LocalDateTime next, DelayedTaskStatus status) {
        nativeTasks.put(type + "/" + biz, new DelayedTask("native-task", "tenant", type, biz, due, next, status, 5, 5, null, "secret-channel-error"));
    }

    @Test
    void detailProvidesExactPreconditionsWithoutErrorsPayloadsOrScheduling() {
        var result = service.detail(new AcquireRecoveryTaskQuery(AcquireRecoveryKind.PAYMENT_ALLOCATION, "payment", 0L));
        verify(orders, never()).lockOrder(anyString(), anyString());
        verify(allocations, never()).lockPayment(anyString(), anyString(), anyString());
        verify(cursors, never()).lock(anyString(), anyString());
        assertEquals("native-task", result.nativeTaskId());
        assertEquals("FAILED", result.nativeStatus());
        assertEquals(5, result.attempts());
        assertTrue(result.retryable());
        assertTrue(result.businessCurrent());
        assertEquals(BusinessTime.external(NOW), result.nextExecuteAt());
        assertFalse(JsonUtil.toJson(result).contains("secret-channel-error"));
        verify(tasks, never()).retryFailed(anyString(), anyString());
        verify(receipts, never()).insert(anyString(), any());
    }

    @Test
    void retryAndAuditShareOneTransactionAndReplayNeverRetriesANewFailedCycle() {
        var first = service.retry(command());
        assertEquals("RETRIED", first.result());
        assertEquals("pay-ops", first.callerServiceId());
        assertEquals(1, ledger.size());
        var audited = ledger.values().iterator().next();
        assertEquals(5, audited.expectedAttempts());
        assertEquals(NOW, audited.expectedNextExecuteAt());
        assertEquals("order", audited.orderId());
        assertFalse(command().toString().contains(command().reason()));
        // A later cycle can fail with the same attempts count; the original request key still replays its first decision.
        put(AllocationRecoveryQueue.TYPE, "payment.0", NOW, NOW.plusSeconds(1), DelayedTaskStatus.FAILED);
        clearInvocations(orders, allocations, cursors, paymentRecovery, tasks);
        var replay = service.retry(command());
        assertEquals(first, replay);
        verifyNoInteractions(orders, allocations, cursors, paymentRecovery, tasks);
        assertEquals(1, manager.commits);
    }

    @Test
    void staleReadInspectionCannotAuthorizeAChangedNativeTask() {
        var view = service.detail(new AcquireRecoveryTaskQuery(AcquireRecoveryKind.PAYMENT_ALLOCATION, "payment", 0L));
        assertTrue(view.retryable());
        put(AllocationRecoveryQueue.TYPE, "payment.0", NOW, NOW.plusSeconds(1), DelayedTaskStatus.FAILED);
        assertSame(VERSION_CONFLICT, assertThrows(CommonException.class, () -> service.retry(command())).getResultCode());
        verify(orders).lockOrder("tenant", "order");
        verify(allocations).lockPayment("tenant", "order", "payment");
        verify(cursors).lock("tenant", "payment");
        verify(tasks, never()).retryFailed(anyString(), anyString());
    }

    @Test
    void changingTheOriginalOperationPayloadCannotResetAnotherRetryCycle() {
        service.retry(command());
        var changed = command(AcquireRecoveryKind.PAYMENT_ALLOCATION, "payment", 0L, "native-task", 5, NOW,
                "operation", "Different reason");
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service.retry(changed)).getResultCode());
        verify(tasks, times(1)).retryFailed(anyString(), anyString());
    }

    @Test
    void exactTaskIdAttemptsAndNativeNextTimeAreOptimisticPreconditions() {
        for (var command : List.of(
                command(AcquireRecoveryKind.PAYMENT_ALLOCATION, "payment", 0L, "replacement", 5, NOW, "operation", "Reason"),
                command(AcquireRecoveryKind.PAYMENT_ALLOCATION, "payment", 0L, "native-task", 4, NOW, "operation", "Reason"),
                command(AcquireRecoveryKind.PAYMENT_ALLOCATION, "payment", 0L, "native-task", 5, NOW.plusSeconds(1), "operation", "Reason"))) {
            assertSame(VERSION_CONFLICT, assertThrows(CommonException.class, () -> service.retry(command)).getResultCode());
        }
        verify(tasks, never()).retryFailed(anyString(), anyString());
        assertTrue(ledger.isEmpty());
    }

    @Test
    void onlyFailedTasksCanBeRetriedAndNativeRegistrationIsNeverInvented() {
        for (var status : List.of(DelayedTaskStatus.PENDING, DelayedTaskStatus.RUNNING, DelayedTaskStatus.SUCCEEDED, DelayedTaskStatus.CANCELLED)) {
            put(AllocationRecoveryQueue.TYPE, "payment.0", NOW, NOW, status);
            assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> service.retry(command())).getResultCode());
        }
        nativeTasks.clear();
        assertSame(NOT_FOUND, assertThrows(CommonException.class, () -> service.retry(command())).getResultCode());
        verify(tasks, never()).register(anyString(), anyString(), any());
        verify(tasks, never()).reschedule(anyString(), anyString(), any());
        verify(tasks, never()).retryFailed(anyString(), anyString());
    }

    @Test
    void obsoleteOrFinanciallySettledWorkOnlyRecordsAnImmutableNoLongerNeededDecision() {
        when(paymentRecovery.isCurrent(eq("tenant"), any(), any(), any(), anyLong())).thenReturn(false);
        var result = service.retry(command());
        assertEquals("NO_LONGER_NEEDED", result.result());
        verify(tasks, never()).retryFailed(anyString(), anyString());
        verify(paymentRecovery, never()).handle(any());
        assertEquals(1, ledger.size());
    }

    @Test
    void aNativeRetryOrAuditFailureRollsBackTheWholeOperation() {
        doThrow(new CommonException(DelayedTaskResultCodeEnum.INVALID_STATE)).when(tasks).retryFailed(anyString(), anyString());
        assertSame(CONCURRENT_UPDATE, assertThrows(CommonException.class, () -> service.retry(command())).getResultCode());
        assertEquals(1, manager.rollbacks);
        assertTrue(ledger.isEmpty());
        verify(receipts, never()).insert(anyString(), any());
    }

    @Test
    void auditInsertFailureCannotBeAcknowledgedAfterResettingTheNativeTask() {
        var failure = new CommonException(DEPENDENCY_UNAVAILABLE);
        doThrow(failure).when(receipts).insert(anyString(), any());
        assertSame(failure, assertThrows(CommonException.class, () -> service.retry(command())));
        assertEquals(1, manager.rollbacks);
        assertEquals(0, manager.commits);
        // UnitTransactions proves callback rollback intent; database atomicity remains an unexecuted integration check.
    }

    @Test
    void deadlineVersionAndOriginalClosingGenerationUseTheirOwnNativeNamespaces() {
        put(OrderDeadlineTasks.TYPE, "order.1", NOW, NOW, DelayedTaskStatus.FAILED);
        var deadline = service.detail(new AcquireRecoveryTaskQuery(AcquireRecoveryKind.ORDER_DEADLINE, "order", 1L));
        assertTrue(deadline.retryable());
        when(extensions.deadlineVersion(eq("tenant"), any())).thenReturn(2L);
        assertFalse(service.detail(new AcquireRecoveryTaskQuery(AcquireRecoveryKind.ORDER_DEADLINE, "order", 1L)).businessCurrent());
        order.set(withOrder(order.get(), order.get().order().requestClose()));
        var intent = new OrderCloseIntent("close", "order", "merchant", OWNER, "original-close", "d".repeat(64), "MERCHANT_CLOSED", 1L,
                NOW.minusSeconds(30), "", 0L);
        when(closes.findIntent("tenant", "order")).thenReturn(Optional.of(intent));
        when(closure.isCurrent(new OrderPaymentScope("tenant", OWNER), order.get(), intent, 0L)).thenReturn(true);
        put(OrderClosureTasks.TYPE, "order.0", NOW, NOW, DelayedTaskStatus.FAILED);
        var result = service.retry(command(AcquireRecoveryKind.ORDER_CLOSURE, "order", 0L, "native-task", 5, NOW, "close-retry", "Resume close"));
        assertEquals("RETRIED", result.result());
        verify(tasks).retryFailed(OrderClosureTasks.TYPE, "order.0");
        verify(closure, never()).resume(any(), any());
    }

    @Test
    void aFutureOriginalWakeupCannotBeAcceleratedByManualFailedRetry() {
        cursor.set(AllocationRecoveryCursor.init("payment", "order", NOW.plusSeconds(1)));
        put(AllocationRecoveryQueue.TYPE, "payment.0", NOW.plusSeconds(1), NOW.plusSeconds(1), DelayedTaskStatus.FAILED);
        assertFalse(service.detail(new AcquireRecoveryTaskQuery(AcquireRecoveryKind.PAYMENT_ALLOCATION, "payment", 0L)).retryable());
        assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> service.retry(command(AcquireRecoveryKind.PAYMENT_ALLOCATION,
                "payment", 0L, "native-task", 5, NOW.plusSeconds(1), "operation", "Reason"))).getResultCode());
        verify(tasks, never()).retryFailed(anyString(), anyString());
    }

    @Test
    void permissionsConfiguredServiceAndNoOuterTransactionAreRequired() {
        actor.set(new AcquireServiceActor("tenant", "payment", Set.of("pay:acquire:recovery:read", "pay:acquire:recovery:retry")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.retry(command())).getResultCode());
        actor.set(new AcquireServiceActor("tenant", "pay-ops", Set.of("pay:acquire:recovery:read")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.retry(command())).getResultCode());
        actor.set(new AcquireServiceActor("tenant", "pay-ops", Set.of("pay:acquire:recovery:retry")));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> service.retry(command())).getResultCode()); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(receipts, orders, tasks);
    }

    @Test
    void invalidDeadlineVersionAndSubMicrosecondPreconditionsAreRejectedBeforeStorage() {
        assertSame(INVALID_ARGUMENT, assertThrows(CommonException.class,
                () -> service.detail(new AcquireRecoveryTaskQuery(AcquireRecoveryKind.ORDER_DEADLINE, "order", 0L))).getResultCode());
        assertSame(INVALID_ARGUMENT, assertThrows(CommonException.class, () -> service.retry(command(AcquireRecoveryKind.PAYMENT_ALLOCATION,
                "payment", 0L, "native-task", 5, NOW.plusNanos(1), "operation", "Reason"))).getResultCode());
        verifyNoInteractions(receipts, orders, tasks);
    }

    static StoredAcquireOrder withOrder(StoredAcquireOrder original, AcquireOrder changed) {
        return new StoredAcquireOrder(changed, original.merchantOrderNo(), original.subject(), original.admission(), original.admittedAt(),
                original.callerScopeHash(), original.snapshotId(), original.admissionPayload(), original.payloadHash());
    }
}
