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
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.delayedtask.DelayedTask;
import io.github.loadup.components.delayedtask.DelayedTaskResultCodeEnum;
import io.github.loadup.components.delayedtask.DelayedTaskStatus;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import io.github.loadup.pay.payment.app.converter.PaymentRecoveryOperationsConverter;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.client.command.PaymentRecoveryRetryCommand;
import io.github.loadup.pay.payment.client.enums.PaymentRecoveryKind;
import io.github.loadup.pay.payment.client.query.PaymentRecoveryTaskQuery;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentRecoveryGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentRecoveryRetryGateway;
import io.github.loadup.pay.payment.domain.gateway.RefundGateway;
import io.github.loadup.pay.payment.domain.gateway.RefundRecoveryGateway;
import io.github.loadup.pay.payment.domain.model.NoCollectionEvidence;
import io.github.loadup.pay.payment.domain.model.PaymentRecoveryCursor;
import io.github.loadup.pay.payment.domain.model.PaymentRecoveryRetryReceipt;
import io.github.loadup.pay.payment.domain.model.RefundRecoveryCursor;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredRefundRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.app.service.AttemptFixtures.NOW;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.VERSION_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentRecoveryOperationsServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(PaymentRecoveryOperationsConverter.class.getPackageName());
    final jakarta.validation.ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AtomicReference<PaymentActor> actor = new AtomicReference<>(new PaymentActor("tenant", "pay-ops",
            Set.of("pay:payment:recovery:read", "pay:payment:recovery:retry")));
    final PaymentRecoveryRetryGateway receipts = mock(PaymentRecoveryRetryGateway.class);
    final PaymentAcceptanceGateway payments = mock(PaymentAcceptanceGateway.class);
    final PaymentExecutionLocks paymentLocks = mock(PaymentExecutionLocks.class);
    final PaymentRecoveryGateway paymentCursors = mock(PaymentRecoveryGateway.class);
    final RefundExecutionLocks refundLocks = mock(RefundExecutionLocks.class);
    final RefundGateway refunds = mock(RefundGateway.class);
    final RefundRecoveryGateway refundCursors = mock(RefundRecoveryGateway.class);
    final DelayedTaskTemplate tasks = mock(DelayedTaskTemplate.class);
    final PaymentAcceptanceServiceTest.UnitTransactions manager = new PaymentAcceptanceServiceTest.UnitTransactions();
    final AtomicReference<StoredPaymentAcceptance> accepted = new AtomicReference<>(AttemptFixtures.accepted(false));
    final AtomicReference<PaymentRecoveryCursor> cursor = new AtomicReference<>(PaymentRecoveryCursor.init("payment", NOW));
    final AtomicReference<StoredRefundRequest> refund = new AtomicReference<>(RefundResultFixtures.initial());
    final AtomicReference<RefundRecoveryCursor> refundCursor = new AtomicReference<>(RefundRecoveryCursor.init("request", refund.get().plannedAt()));
    final java.util.concurrent.atomic.AtomicInteger receiptIds = new java.util.concurrent.atomic.AtomicInteger();
    final Map<String, PaymentRecoveryRetryReceipt> ledger = new HashMap<>();
    final Map<String, DelayedTask> nativeTasks = new HashMap<>();
    final PaymentRecoveryOperationsService service = new PaymentRecoveryOperationsService(actor::get, receipts, payments, paymentLocks,
            paymentCursors, refundLocks, refunds, refundCursors, tasks, context.getBean(PaymentRecoveryOperationsConverter.class),
            new PaymentAcceptanceTransactions(manager), () -> "retry-receipt-" + receiptIds.incrementAndGet(), validation.getValidator(),
            Clock.fixed(NOW.atZone(BusinessTime.zone()).toInstant(), BusinessTime.zone()), "pay-ops");

    @BeforeEach
    void setup() {
        when(payments.find("tenant", "payment")).thenAnswer(c -> Optional.of(accepted.get()));
        when(paymentLocks.lock("tenant", "payment", "acquire")).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("tenant", TenantUtil.getTenantId());
            return PaymentExecutionBindings.locked(accepted.get(), AttemptFixtures.gate(true), AttemptFixtures.gate(false));
        });
        when(paymentCursors.find("tenant", "payment")).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return Optional.of(cursor.get());
        });
        when(refundCursors.find("tenant", "request")).thenAnswer(c -> Optional.of(refundCursor.get()));
        when(refunds.load("tenant", "request")).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return refund.get();
        });
        when(paymentCursors.lock("tenant", "payment")).thenAnswer(c -> Optional.of(cursor.get()));
        when(refundLocks.request("tenant", "request")).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return refund.get();
        });
        when(refundCursors.lock("tenant", "request")).thenAnswer(c -> Optional.of(refundCursor.get()));
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
            PaymentRecoveryRetryReceipt value = c.getArgument(1);
            ledger.put(value.callerScopeHash() + "/" + value.requestKey(), value);
            return null;
        }).when(receipts).insert(eq("tenant"), any());
        put(PaymentRecoveryService.TYPE, "payment.0", NOW, NOW, DelayedTaskStatus.FAILED);
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    void put(String type, String biz, LocalDateTime due, LocalDateTime next, DelayedTaskStatus status) {
        nativeTasks.put(type + "/" + biz, new DelayedTask(type.equals(RefundRecoveryService.TYPE) ? "native-refund-task" : "native-task", "tenant", type, biz, due, next, status, 5, 5, null, "private-wire-error"));
    }

    PaymentRecoveryRetryCommand command(PaymentRecoveryKind kind, String subject, String key, LocalDateTime next) {
        return new PaymentRecoveryRetryCommand(kind, subject, 0L, kind == PaymentRecoveryKind.REFUND_REQUEST ? "native-refund-task" : "native-task", 5, BusinessTime.external(next), key, "Dependency restored");
    }

    PaymentRecoveryRetryCommand command() {
        return command(PaymentRecoveryKind.COLLECTION, "payment", "operation", NOW);
    }

    @Test
    void collectionInspectionUsesReadSnapshotAndHidesNativeErrors() {
        var dto = service.detail(new PaymentRecoveryTaskQuery(PaymentRecoveryKind.COLLECTION, "payment", 0L));
        assertTrue(dto.retryable());
        assertEquals("native-task", dto.nativeTaskId());
        assertEquals("FAILED", dto.nativeStatus());
        assertFalse(JsonUtil.toJson(dto).contains("private-wire-error"));
        verifyNoInteractions(paymentLocks, refundLocks);
        verify(paymentCursors, never()).lock(anyString(), anyString());
        verify(tasks, never()).retryFailed(anyString(), anyString());
        verifyNoInteractions(refundLocks, refundCursors, receipts);
    }

    @Test
    void retryAndReceiptAreAtomicAndOriginalKeyCannotResetAnotherFailedCycle() {
        var first = service.retry(command());
        assertEquals("RETRIED", first.result());
        verify(paymentLocks).lock("tenant", "payment", "acquire");
        verify(paymentLocks, never()).lock(anyString(), anyString(), eq("pay-ops"));
        assertEquals("order", ledger.values().iterator().next().orderId());
        assertEquals(1, manager.commits);
        put(PaymentRecoveryService.TYPE, "payment.0", NOW, NOW.plusSeconds(1), DelayedTaskStatus.FAILED);
        clearInvocations(payments, paymentLocks, paymentCursors, tasks);
        assertEquals(first, service.retry(command()));
        verifyNoInteractions(payments, paymentLocks, paymentCursors, tasks);
        assertEquals(1, manager.commits);
    }

    @Test
    void refundInspectionNeverAcquiresParentRequestOrCursorWriteLocks() {
        put(RefundRecoveryService.TYPE, "request.0", refund.get().plannedAt(), NOW, DelayedTaskStatus.FAILED);
        assertTrue(service.detail(new PaymentRecoveryTaskQuery(PaymentRecoveryKind.REFUND_REQUEST, "request", 0L)).retryable());
        verify(refunds).load("tenant", "request");
        verify(refundCursors).find("tenant", "request");
        verifyNoInteractions(refundLocks, paymentLocks);
        verify(refundCursors, never()).lock(anyString(), anyString());
    }

    @Test
    void staleInspectionStillNeedsTheLockedOriginalRetryPreconditions() {
        assertTrue(service.detail(new PaymentRecoveryTaskQuery(PaymentRecoveryKind.COLLECTION, "payment", 0L)).retryable());
        put(PaymentRecoveryService.TYPE, "payment.0", NOW, NOW.plusSeconds(1), DelayedTaskStatus.FAILED);
        assertSame(VERSION_CONFLICT, assertThrows(CommonException.class, () -> service.retry(command())).getResultCode());
        verify(paymentLocks).lock("tenant", "payment", "acquire");
        verify(paymentCursors).lock("tenant", "payment");
        verify(tasks, never()).retryFailed(anyString(), anyString());
    }

    @Test
    void sameKeyWithChangedPreconditionsConflictsBeforeReadingFinancialState() {
        service.retry(command());
        var changed = command(PaymentRecoveryKind.COLLECTION, "payment", "operation", NOW.plusSeconds(1));
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service.retry(changed)).getResultCode());
        verify(tasks, times(1)).retryFailed(PaymentRecoveryService.TYPE, "payment.0");
    }

    @Test
    void refundRetryOnlyWakesItsOriginalFixedRequestAndDoesNotEnterCollectionLocks() {
        put(RefundRecoveryService.TYPE, "request.0", refund.get().plannedAt(), NOW, DelayedTaskStatus.FAILED);
        var result = service.retry(command(PaymentRecoveryKind.REFUND_REQUEST, "request", "refund-operation", NOW));
        assertEquals("RETRIED", result.result());
        assertEquals("request", result.subjectId());
        verify(tasks).retryFailed(RefundRecoveryService.TYPE, "request.0");
        verifyNoInteractions(payments, paymentLocks, paymentCursors);
        var sequence = inOrder(refundLocks, refundCursors, tasks);
        sequence.verify(refundLocks).request("tenant", "request");
        sequence.verify(refundCursors).lock("tenant", "request");
        sequence.verify(tasks).find(RefundRecoveryService.TYPE, "request.0");
    }

    @Test
    void obsoleteAndTerminalCollectionGenerationsNeverGetAnotherWakeup() {
        cursor.set(cursor.get().schedule(NOW.plusSeconds(30), 0L));
        assertEquals("NO_LONGER_NEEDED", service.retry(command()).result());
        var original = accepted.get();
        accepted.set(new StoredPaymentAcceptance(original.payment().fail(new NoCollectionEvidence("proof", "payment", true)),
                original.admissionId(), original.requestDigest(), original.callerServiceId(), original.intentPayload(), original.payloadHash(), "proof"));
        cursor.set(PaymentRecoveryCursor.init("payment", NOW));
        assertEquals("NO_LONGER_NEEDED", service.retry(command(PaymentRecoveryKind.COLLECTION, "payment", "terminal-operation", NOW)).result());
        verify(tasks, never()).retryFailed(anyString(), anyString());
    }

    @Test
    void aCompletedCursorCannotHideAnUnfinishedFinancialAggregate() {
        cursor.set(cursor.get().finish());
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> service.retry(command())).getResultCode());
        assertTrue(ledger.isEmpty());
        verify(tasks, never()).retryFailed(anyString(), anyString());
    }

    @Test
    void missingTasksAndNonFailedStatesCannotBeCreatedOrRescheduledThroughOperations() {
        for (var status : List.of(DelayedTaskStatus.PENDING, DelayedTaskStatus.RUNNING, DelayedTaskStatus.SUCCEEDED, DelayedTaskStatus.CANCELLED)) {
            put(PaymentRecoveryService.TYPE, "payment.0", NOW, NOW, status);
            assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> service.retry(command())).getResultCode());
        }
        nativeTasks.clear();
        assertSame(NOT_FOUND, assertThrows(CommonException.class, () -> service.retry(command())).getResultCode());
        verify(tasks, never()).register(anyString(), anyString(), any());
        verify(tasks, never()).reschedule(anyString(), anyString(), any());
        verify(tasks, never()).retryFailed(anyString(), anyString());
    }

    @Test
    void nativeIdentityCountAndNextExecutionTimeMustMatchTheObservedFailedEpisode() {
        var original = command();
        for (var wrong : List.of(
                new PaymentRecoveryRetryCommand(original.kind(), "payment", 0L, "other-task", 5, original.expectedNextExecuteAt(), "operation", original.reason()),
                new PaymentRecoveryRetryCommand(original.kind(), "payment", 0L, "native-task", 4, original.expectedNextExecuteAt(), "operation", original.reason()),
                command(PaymentRecoveryKind.COLLECTION, "payment", "operation", NOW.plusSeconds(1)))) {
            assertSame(VERSION_CONFLICT, assertThrows(CommonException.class, () -> service.retry(wrong)).getResultCode());
        }
        assertTrue(ledger.isEmpty());
        verify(tasks, never()).retryFailed(anyString(), anyString());
    }

    @Test
    void futureWakeupAndSubMicrosecondExpectationsAreRejected() {
        cursor.set(PaymentRecoveryCursor.init("payment", NOW.plusSeconds(1)));
        put(PaymentRecoveryService.TYPE, "payment.0", NOW.plusSeconds(1), NOW.plusSeconds(1), DelayedTaskStatus.FAILED);
        assertSame(INVALID_STATE, assertThrows(CommonException.class,
                () -> service.retry(command(PaymentRecoveryKind.COLLECTION, "payment", "future", NOW.plusSeconds(1)))).getResultCode());
        assertSame(INVALID_ARGUMENT, assertThrows(CommonException.class,
                () -> service.retry(command(PaymentRecoveryKind.COLLECTION, "payment", "fraction", NOW.plusNanos(1)))).getResultCode());
        verify(tasks, never()).retryFailed(anyString(), anyString());
    }

    @Test
    void nativeStateCompetitionAndAuditFailureCannotReturnAnAcknowledgement() {
        doThrow(new CommonException(DelayedTaskResultCodeEnum.INVALID_STATE)).when(tasks).retryFailed(anyString(), anyString());
        assertSame(CONCURRENT_UPDATE, assertThrows(CommonException.class, () -> service.retry(command())).getResultCode());
        verify(receipts, never()).insert(anyString(), any());
        assertEquals(1, manager.rollbacks);
    }

    @Test
    void auditInsertFailureRollsBackTheNativeRetryTransaction() {
        var failure = new CommonException(DEPENDENCY_UNAVAILABLE);
        doThrow(failure).when(receipts).insert(anyString(), any());
        assertSame(failure, assertThrows(CommonException.class, () -> service.retry(command())));
        assertEquals(1, manager.rollbacks);
        assertEquals(0, manager.commits);
    }

    @Test
    void recoveryPermissionDoesNotBelongToTheOrdinaryAcquireCaller() {
        actor.set(new PaymentActor("tenant", "acquire", Set.of("pay:payment:recovery:read", "pay:payment:recovery:retry")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.retry(command())).getResultCode());
        actor.set(new PaymentActor("tenant", "pay-ops", Set.of("pay:payment:recovery:read")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.retry(command())).getResultCode());
        actor.set(new PaymentActor("tenant", "pay-ops", Set.of("pay:payment:recovery:retry")));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> service.retry(command())).getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(receipts, payments, tasks);
    }
}
