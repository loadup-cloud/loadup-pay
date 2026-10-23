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
import io.github.loadup.components.delayedtask.DelayedTask;
import io.github.loadup.components.delayedtask.DelayedTaskStatus;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import io.github.loadup.pay.payment.app.model.LockedPayment;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentRecoveryGateway;
import io.github.loadup.pay.payment.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentRecoveryServiceTest {
    final PaymentRecoveryGateway cursors = mock(PaymentRecoveryGateway.class);
    final DelayedTaskTemplate tasks = mock(DelayedTaskTemplate.class);
    final PaymentExecutionLocks locks = mock(PaymentExecutionLocks.class);
    final PaymentAcceptanceGateway payments = mock(PaymentAcceptanceGateway.class);
    final PaymentExecutionService execution = mock(PaymentExecutionService.class);
    final PaymentAcceptanceServiceTest.UnitTransactions manager = new PaymentAcceptanceServiceTest.UnitTransactions();
    final PaymentAcceptanceTransactions transactions = new PaymentAcceptanceTransactions(manager);
    final AtomicReference<StoredPaymentAcceptance> accepted = new AtomicReference<>(AttemptFixtures.accepted(false));
    final AtomicReference<PaymentRecoveryCursor> cursor = new AtomicReference<>(
            PaymentRecoveryCursor.init("payment", AttemptFixtures.NOW));
    final Clock clock = Clock.fixed(AttemptFixtures.NOW.atZone(BusinessTime.zone()).toInstant(), BusinessTime.zone());
    final PaymentRecoveryService recovery = new PaymentRecoveryService(cursors, tasks, locks, execution, payments,
            transactions, clock, 30);
    final DelayedTask task = new DelayedTask("task", "tenant", PaymentRecoveryService.TYPE, "payment.0",
            AttemptFixtures.NOW, AttemptFixtures.NOW, DelayedTaskStatus.RUNNING, 1, 5,
            AttemptFixtures.NOW.plusMinutes(5), null);

    @BeforeEach
    void setup() {
        when(tasks.find(PaymentRecoveryService.TYPE, "payment.0")).thenReturn(Optional.of(task));
        when(payments.find("tenant", "payment")).thenAnswer(c -> Optional.of(accepted.get()));
        when(locks.lock("tenant", "payment", "acquire")).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("tenant", TenantUtil.getTenantId());
            return new LockedPayment(accepted.get(), AttemptFixtures.gate(true), AttemptFixtures.gate(false));
        });
        when(cursors.lock("tenant", "payment")).thenAnswer(c -> Optional.ofNullable(cursor.get()));
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertNull(cursor.get());
            cursor.set(c.getArgument(1));
            return null;
        }).when(cursors).insert(eq("tenant"), any());
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(cursor.get(), c.getArgument(1));
            cursor.set(c.getArgument(2));
            return null;
        }).when(cursors).update(eq("tenant"), any(), any());
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(tasks).register(anyString(), anyString(), any());
        doAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(manager.commits > 0);
            return null;
        }).when(execution).progress("tenant", "payment");
    }

    void handle() { TenantUtil.runWithTenant("tenant", () -> recovery.handle(task)); }

    @Test
    void registrationRequiresAndJoinsAcceptanceTransaction() {
        cursor.set(null);
        assertSame(INVALID_STATE, assertThrows(CommonException.class,
                () -> TenantUtil.runWithTenant("tenant", () -> recovery.register("tenant", accepted.get()))).getResultCode());

        TenantUtil.runWithTenant("tenant", () -> transactions.write("tenant", () -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            recovery.register("tenant", accepted.get());
            return true;
        }));

        assertEquals(PaymentRecoveryCursor.init("payment", AttemptFixtures.NOW), cursor.get());
        verify(tasks).register(PaymentRecoveryService.TYPE, "payment.0", AttemptFixtures.NOW);
        assertEquals(1, manager.commits);
        verifyNoInteractions(execution);
    }

    @Test
    void validPendingTaskRunsExecutionThenCommitsDistinctSuccessorCursor() {
        handle();

        assertEquals(1, cursor.get().generation());
        assertEquals(AttemptFixtures.NOW.plusSeconds(30), cursor.get().dueAt());
        assertFalse(cursor.get().completed());
        verify(execution).progress("tenant", "payment");
        verify(tasks).register(PaymentRecoveryService.TYPE, "payment.1", AttemptFixtures.NOW.plusSeconds(30));
        assertEquals(2, manager.commits);
    }

    @Test
    void staleGenerationAndTerminalPaymentDoNotStartExecution() {
        cursor.set(cursor.get().schedule(AttemptFixtures.NOW.plusSeconds(30), 0));
        handle();
        verifyNoInteractions(execution);
        verify(tasks, never()).register(anyString(), anyString(), any());

        var original = accepted.get();
        var terminal = original.payment().fail(new NoCollectionEvidence("evidence", "payment", true));
        accepted.set(new StoredPaymentAcceptance(terminal, original.admissionId(), original.requestDigest(),
                original.callerServiceId(), original.intentPayload(), original.payloadHash(), "evidence"));
        cursor.set(PaymentRecoveryCursor.init("payment", AttemptFixtures.NOW));
        clearInvocations(cursors, tasks, execution);
        handle();
        assertTrue(cursor.get().completed());
        verifyNoInteractions(execution);
        verify(cursors).update(eq("tenant"), any(), argThat(PaymentRecoveryCursor::completed));
        verify(tasks, never()).register(anyString(), anyString(), any());
    }

    @Test
    void taskClaimTenantAndLeaseAreValidatedBeforeBusinessWork() {
        when(tasks.find(PaymentRecoveryService.TYPE, "payment.0")).thenReturn(Optional.empty());
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, this::handle).getResultCode());
        assertThrows(CommonException.class, () -> TenantUtil.runWithTenant("other", () -> recovery.handle(task)));

        var expired = new DelayedTask(task.id(), task.tenantId(), task.taskType(), task.bizId(), task.executeAt(),
                task.nextExecuteAt(), task.status(), task.attempts(), task.maxAttempts(), AttemptFixtures.NOW, null);
        when(tasks.find(PaymentRecoveryService.TYPE, "payment.0")).thenReturn(Optional.of(expired));
        assertSame(INVALID_STATE, assertThrows(CommonException.class, this::handle).getResultCode());
        verifyNoInteractions(execution, payments, locks);
    }

    @Test
    void executionFailureKeepsCursorGenerationAndPropagatesForNativeRetry() {
        doThrow(new CommonException(DEPENDENCY_UNAVAILABLE)).when(execution).progress("tenant", "payment");
        var before = cursor.get();

        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, this::handle).getResultCode());

        assertEquals(before, cursor.get());
        verify(cursors, never()).update(anyString(), any(), any());
        verify(tasks, never()).register(anyString(), anyString(), any());
    }
}
