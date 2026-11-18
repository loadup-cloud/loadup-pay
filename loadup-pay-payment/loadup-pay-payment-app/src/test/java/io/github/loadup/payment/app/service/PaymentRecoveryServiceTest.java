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

import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.components.task.TaskExecution;
import io.github.loadup.components.task.TaskStatus;
import io.github.loadup.components.task.TaskSubmission;
import io.github.loadup.components.task.TaskTemplate;
import io.github.loadup.payment.app.model.RecoveryHandlingOutcome;
import io.github.loadup.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.payment.domain.gateway.PaymentRecoveryGateway;
import io.github.loadup.payment.domain.model.NoCollectionEvidence;
import io.github.loadup.payment.domain.model.PaymentRecoveryCursor;
import io.github.loadup.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.payment.domain.policy.RecoveryPollingPolicy;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PaymentRecoveryServiceTest {
    final PaymentRecoveryGateway cursors = mock(PaymentRecoveryGateway.class);
    final TaskTemplate tasks = mock(TaskTemplate.class);
    final PaymentExecutionLocks locks = mock(PaymentExecutionLocks.class);
    final PaymentAcceptanceGateway payments = mock(PaymentAcceptanceGateway.class);
    final PaymentExecutionService execution = mock(PaymentExecutionService.class);
    final PaymentAcceptanceServiceTest.UnitTransactions manager = new PaymentAcceptanceServiceTest.UnitTransactions();
    final PaymentAcceptanceTransactions transactions = new PaymentAcceptanceTransactions(manager);
    final AtomicReference<StoredPaymentAcceptance> accepted = new AtomicReference<>(AttemptFixtures.accepted(false));
    final AtomicReference<PaymentRecoveryCursor> cursor =
            new AtomicReference<>(PaymentRecoveryCursor.init("payment", AttemptFixtures.NOW));
    final Clock clock =
            Clock.fixed(AttemptFixtures.NOW.atZone(BusinessTime.zone()).toInstant(), BusinessTime.zone());
    final PaymentRecoveryService recovery = new PaymentRecoveryService(
            cursors,
            tasks,
            locks,
            execution,
            payments,
            transactions,
            clock,
            new RecoveryPollingPolicy(30, 1800, 900, 0));
    final TaskExecution task = new TaskExecution(
            "task",
            PaymentRecoveryService.TYPE,
            "payment.0",
            AttemptFixtures.NOW,
            TaskStatus.RUNNING,
            1,
            5,
            AttemptFixtures.NOW.plusMinutes(5),
            null);

    @BeforeEach
    void setup() {
        when(tasks.find(PaymentRecoveryService.TYPE, "payment.0")).thenReturn(Optional.of(task));
        when(payments.find("payment")).thenAnswer(c -> Optional.of(accepted.get()));
        when(locks.lock("payment", "acquire")).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return PaymentExecutionBindings.locked(
                    accepted.get(), AttemptFixtures.gate(true), AttemptFixtures.gate(false));
        });
        when(cursors.lock("payment")).thenAnswer(c -> Optional.ofNullable(cursor.get()));
        doAnswer(c -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    assertNull(cursor.get());
                    cursor.set(c.getArgument(0));
                    return null;
                })
                .when(cursors)
                .insert(any());
        doAnswer(c -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    assertEquals(cursor.get(), c.getArgument(0));
                    cursor.set(c.getArgument(1));
                    return null;
                })
                .when(cursors)
                .update(any(), any());
        doAnswer(c -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    return null;
                })
                .when(tasks)
                .submit(ArgumentMatchers.any(TaskSubmission.class));
        doAnswer(c -> {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    assertTrue(manager.commits > 0);
                    return null;
                })
                .when(execution)
                .progress("payment");
    }

    RecoveryHandlingOutcome handle() {
        return recovery.handle(task);
    }

    @Test
    void registrationRequiresAndJoinsAcceptanceTransaction() {
        cursor.set(null);
        assertSame(
                INVALID_STATE,
                assertThrows(CommonException.class, () -> recovery.register(accepted.get()))
                        .getResultCode());

        transactions.writeLocal(() -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            recovery.register(accepted.get());
            return true;
        });

        assertEquals(PaymentRecoveryCursor.init("payment", AttemptFixtures.NOW), cursor.get());
        verify(tasks)
                .submit(ArgumentMatchers.eq(
                        TaskSubmission.scheduled(PaymentRecoveryService.TYPE, "payment.0", AttemptFixtures.NOW)));
        assertEquals(1, manager.commits);
        verifyNoInteractions(execution);
    }

    @Test
    void validPendingTaskRunsExecutionThenCommitsDistinctSuccessorCursor() {
        assertEquals(RecoveryHandlingOutcome.CONTINUED, handle());

        assertEquals(1, cursor.get().generation());
        assertEquals(AttemptFixtures.NOW.plusSeconds(30), cursor.get().dueAt());
        assertFalse(cursor.get().completed());
        verify(execution).progress("payment");
        verify(tasks)
                .submit(ArgumentMatchers.eq(TaskSubmission.scheduled(
                        PaymentRecoveryService.TYPE, "payment.1", AttemptFixtures.NOW.plusSeconds(30))));
        assertEquals(2, manager.commits);
    }

    @Test
    void staleGenerationAndTerminalPaymentDoNotStartExecution() {
        cursor.set(cursor.get().schedule(AttemptFixtures.NOW.plusSeconds(30), 0));
        assertEquals(RecoveryHandlingOutcome.SKIPPED, handle());
        verifyNoInteractions(execution);
        verify(tasks, never()).submit(ArgumentMatchers.any(TaskSubmission.class));

        var original = accepted.get();
        var terminal = original.payment().fail(new NoCollectionEvidence("evidence", "payment", true));
        accepted.set(new StoredPaymentAcceptance(
                terminal,
                original.admissionId(),
                original.requestDigest(),
                original.callerServiceId(),
                original.intentPayload(),
                original.payloadHash(),
                "evidence"));
        cursor.set(PaymentRecoveryCursor.init("payment", AttemptFixtures.NOW));
        clearInvocations(cursors, tasks, execution);
        assertEquals(RecoveryHandlingOutcome.SKIPPED, handle());
        assertTrue(cursor.get().completed());
        verifyNoInteractions(execution);
        verify(cursors).update(any(), argThat(PaymentRecoveryCursor::completed));
        verify(tasks, never()).submit(ArgumentMatchers.any(TaskSubmission.class));
    }

    @Test
    void taskClaimTenantAndLeaseAreValidatedBeforeBusinessWork() {
        when(tasks.find(PaymentRecoveryService.TYPE, "payment.0")).thenReturn(Optional.empty());
        assertSame(
                SNAPSHOT_INVALID,
                assertThrows(CommonException.class, this::handle).getResultCode());
        assertThrows(CommonException.class, () -> recovery.handle(task));

        var expired = new TaskExecution(
                task.id(),
                task.taskType(),
                task.bizId(),
                task.nextExecuteAt(),
                task.status(),
                task.attempts(),
                task.maxAttempts(),
                AttemptFixtures.NOW,
                null);
        when(tasks.find(PaymentRecoveryService.TYPE, "payment.0")).thenReturn(Optional.of(expired));
        assertSame(
                INVALID_STATE, assertThrows(CommonException.class, this::handle).getResultCode());
        verifyNoInteractions(execution, payments, locks);
    }

    @Test
    void executionFailureKeepsCursorGenerationAndPropagatesForNativeRetry() {
        doThrow(new CommonException(DEPENDENCY_UNAVAILABLE)).when(execution).progress("payment");
        var before = cursor.get();

        assertSame(
                DEPENDENCY_UNAVAILABLE,
                assertThrows(CommonException.class, this::handle).getResultCode());

        assertEquals(before, cursor.get());
        verify(cursors, never()).update(any(), any());
        verify(tasks, never()).submit(ArgumentMatchers.any(TaskSubmission.class));
    }

    @Test
    void oldUnresolvedPaymentBacksOffWithoutFinishingOrChangingItsRequest() {
        var late = AttemptFixtures.NOW.plusDays(1);
        var lateClock = Clock.fixed(late.atZone(clock.getZone()).toInstant(), clock.getZone());
        var delayed = new TaskExecution(
                task.id(),
                task.taskType(),
                task.bizId(),
                task.nextExecuteAt(),
                task.status(),
                task.attempts(),
                task.maxAttempts(),
                late.plusMinutes(5),
                null);
        when(tasks.find(PaymentRecoveryService.TYPE, task.bizId())).thenReturn(Optional.of(delayed));
        var handler = new PaymentRecoveryService(
                cursors,
                tasks,
                locks,
                execution,
                payments,
                transactions,
                lateClock,
                new RecoveryPollingPolicy(30, 1800, 900, 0));
        var original = accepted.get();
        handler.handle(delayed);
        assertSame(original, accepted.get());
        assertFalse(cursor.get().completed());
        assertEquals(1L, cursor.get().generation());
        assertEquals(late.plusMinutes(30), cursor.get().dueAt());
        verify(execution).progress("payment");
        verify(tasks)
                .submit(ArgumentMatchers.eq(
                        TaskSubmission.scheduled(PaymentRecoveryService.TYPE, "payment.1", late.plusMinutes(30))));
    }

    @Test
    void finalExecutionReportsCompletionOnlyAfterTheCursorTransactionCommits() {
        doAnswer(call -> {
                    var before = accepted.get();
                    var failed = before.payment().fail(new NoCollectionEvidence("proof", "payment", true));
                    accepted.set(new StoredPaymentAcceptance(
                            failed,
                            before.admissionId(),
                            before.requestDigest(),
                            before.callerServiceId(),
                            before.intentPayload(),
                            before.payloadHash(),
                            "proof"));
                    return null;
                })
                .when(execution)
                .progress("payment");
        assertEquals(RecoveryHandlingOutcome.COMPLETED, handle());
        assertTrue(cursor.get().completed());
        assertEquals(2, manager.commits);
        verify(tasks, never()).submit(ArgumentMatchers.any(TaskSubmission.class));
    }

    @Test
    void aSameGenerationDueTimeChangeDuringExecutionCannotCreateASuccessor() {
        doAnswer(call -> {
                    var before = cursor.get();
                    cursor.set(new PaymentRecoveryCursor(
                            before.paymentId(),
                            before.generation(),
                            before.dueAt().plusSeconds(1),
                            false));
                    return null;
                })
                .when(execution)
                .progress("payment");
        assertEquals(
                SNAPSHOT_INVALID,
                assertThrows(CommonException.class, this::handle).getResultCode());
        verify(tasks, never()).submit(ArgumentMatchers.any(TaskSubmission.class));
        verify(cursors, never()).update(any(), any());
        assertEquals(1, manager.commits);
        assertEquals(1, manager.rollbacks);
    }
}
