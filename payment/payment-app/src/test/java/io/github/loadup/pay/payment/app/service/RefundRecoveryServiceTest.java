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

import io.github.loadup.pay.channel.api.model.ChannelRefundOutcome;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.delayedtask.*;
import io.github.loadup.pay.payment.domain.gateway.RefundRecoveryGateway;
import io.github.loadup.pay.payment.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.app.service.RefundResultFixtures.NOW;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefundRecoveryServiceTest {
    final RefundRecoveryGateway cursors = mock(RefundRecoveryGateway.class);
    final DelayedTaskTemplate tasks = mock(DelayedTaskTemplate.class);
    final RefundExecutionLocks locks = mock(RefundExecutionLocks.class);
    final RefundExecutionService execution = mock(RefundExecutionService.class);
    final PaymentAcceptanceServiceTest.UnitTransactions manager = new PaymentAcceptanceServiceTest.UnitTransactions();
    final PaymentAcceptanceTransactions transactions = new PaymentAcceptanceTransactions(manager);
    final AtomicReference<StoredRefundRequest> request = new AtomicReference<>(RefundResultFixtures.initial());
    final AtomicReference<RefundRecoveryCursor> cursor = new AtomicReference<>(RefundRecoveryCursor.init("request", request.get().plannedAt()));
    final Clock clock = Clock.fixed(NOW.atZone(BusinessTime.zone()).toInstant(), BusinessTime.zone());
    final RefundRecoveryService recovery = new RefundRecoveryService(cursors, tasks, locks, execution, transactions, clock, 30);
    final DelayedTask task = new DelayedTask("task", "tenant", RefundRecoveryService.TYPE, "request.0",
            request.get().plannedAt(), request.get().plannedAt(), DelayedTaskStatus.RUNNING, 1, 5, NOW.plusMinutes(5), null);

    @BeforeEach
    void setup() {
        when(tasks.find(RefundRecoveryService.TYPE, "request.0")).thenReturn(Optional.of(task));
        when(locks.request("tenant", "request")).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("tenant", TenantUtil.getTenantId());
            return request.get();
        });
        when(cursors.lock("tenant", "request")).thenAnswer(c -> Optional.ofNullable(cursor.get()));
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            cursor.set(c.getArgument(1)); return null;
        }).when(cursors).insert(eq("tenant"), any());
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(cursor.get(), c.getArgument(1));
            cursor.set(c.getArgument(2)); return null;
        }).when(cursors).update(eq("tenant"), any(), any());
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(tasks).register(anyString(), anyString(), any());
        doAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(manager.commits > 0);
            return null;
        }).when(execution).progress("tenant", "request");
    }

    void handle() { TenantUtil.runWithTenant("tenant", () -> recovery.handle(task)); }

    StoredRefundRequest terminal() {
        var value = request.get();
        var children = new ArrayList<StoredRefundChild>();
        var aggregate = value.request();
        for (var stored : value.allocations()) {
            var refund = stored.refund().beginSubmission().recordOutcome(new ChannelRefundOutcome(stored.refund().id(),
                    ChannelRefundOutcome.Kind.FAILED_WITH_NO_REFUND, stored.refund().amount(), "proof-" + stored.refund().id()));
            children.add(new StoredRefundChild(refund, stored.sequenceNo(), stored.original(), stored.channelTransactionNo(), stored.originalSucceededAt()));
            aggregate = aggregate.apply(refund);
        }
        return new StoredRefundRequest(aggregate, value.reasonCode(), value.requestDigest(), value.callerSubjectHash(),
                value.admissionId(), value.termsPayload(), value.termsHash(), value.plannedAt(), List.copyOf(children));
    }

    @Test
    void fixedPlanAndInitialWakeUpUseTheSameTransactionAndOriginalPlanningTime() {
        cursor.set(null);
        transactions.write("tenant", () -> { recovery.register("tenant", request.get()); return true; });
        assertEquals(0, cursor.get().generation());
        assertEquals(request.get().plannedAt(), cursor.get().dueAt());
        verify(tasks).register(RefundRecoveryService.TYPE, "request.0", request.get().plannedAt());
        assertEquals(1, manager.commits);
        verifyNoInteractions(execution);
    }

    @Test
    void ordinaryPendingCreatesAnAtomicSuccessorRatherThanExhaustingFailureRetries() {
        handle();
        assertEquals(1, cursor.get().generation());
        assertEquals(NOW.plusSeconds(30), cursor.get().dueAt());
        assertFalse(cursor.get().completed());
        verify(execution).progress("tenant", "request");
        verify(tasks).register(RefundRecoveryService.TYPE, "request.1", NOW.plusSeconds(30));
        assertEquals(2, manager.commits);
    }

    @Test
    void oldGenerationAndCompletedCursorCannotRepeatChannelWork() {
        cursor.set(cursor.get().schedule(NOW.plusSeconds(30), 0));
        handle();
        verifyNoInteractions(execution);
        verify(tasks, never()).register(anyString(), anyString(), any());
    }

    @Test
    void aTerminalRequestCompletesWithoutRequiringAnyChannelProvider() {
        request.set(terminal());
        handle();
        handle();
        assertTrue(cursor.get().completed());
        assertEquals(0, cursor.get().generation());
        verify(cursors, times(1)).update(eq("tenant"), any(), any());
        verifyNoInteractions(execution);
        verify(tasks, never()).register(anyString(), anyString(), any());
    }

    @Test
    void finalChannelResultStopsContinuationAndRetainsTheCompletedGeneration() {
        doAnswer(c -> { request.set(terminal()); return null; }).when(execution).progress("tenant", "request");
        handle();
        assertTrue(cursor.get().completed());
        assertEquals(0, cursor.get().generation());
        verify(tasks, never()).register(anyString(), anyString(), any());
    }

    @Test
    void channelFailureLeavesTheSameGenerationForNativeRetryAndLeaseRecovery() {
        var before = cursor.get();
        doThrow(new CommonException(DEPENDENCY_UNAVAILABLE)).when(execution).progress("tenant", "request");
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, this::handle).getResultCode());
        assertEquals(before, cursor.get());
        verify(cursors, never()).update(anyString(), any(), any());
        verify(tasks, never()).register(anyString(), anyString(), any());
    }

    @Test
    void failedSuccessorRegistrationPreventsAcknowledgingTheCurrentTaskAsSuccessful() {
        doThrow(new CommonException(DEPENDENCY_UNAVAILABLE)).when(tasks).register(anyString(), anyString(), any());
        assertThrows(CommonException.class, this::handle);
        assertEquals(1, manager.commits);
        assertEquals(1, manager.rollbacks);
        // The mock storage cannot prove database rollback. Both mutations join one real transaction in production.
    }

    @Test
    void aDuplicateThatLosesTheGenerationRaceDoesNotRegisterAnotherSuccessor() {
        doAnswer(c -> { cursor.set(cursor.get().schedule(NOW.plusSeconds(30), 0)); return null; })
                .when(execution).progress("tenant", "request");
        handle();
        verify(tasks, never()).register(anyString(), anyString(), any());
        verify(cursors, never()).update(anyString(), any(), any());
    }

    @Test
    void unregisteredClaimWrongTenantAndExpiredLeaseCannotStartBusinessWork() {
        when(tasks.find(RefundRecoveryService.TYPE, "request.0")).thenReturn(Optional.empty());
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, this::handle).getResultCode());
        assertThrows(CommonException.class, () -> TenantUtil.runWithTenant("other", () -> recovery.handle(task)));
        var expired = new DelayedTask(task.id(), task.tenantId(), task.taskType(), task.bizId(), task.executeAt(), task.nextExecuteAt(),
                task.status(), task.attempts(), task.maxAttempts(), NOW, null);
        when(tasks.find(RefundRecoveryService.TYPE, "request.0")).thenReturn(Optional.of(expired));
        assertSame(INVALID_STATE, assertThrows(CommonException.class, this::handle).getResultCode());
        verifyNoInteractions(execution, locks);
    }
}
