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
package io.github.loadup.acquire.app.service;

import static io.github.loadup.acquire.app.service.CheckoutFixtures.NOW;
import static io.github.loadup.acquire.app.service.CheckoutFixtures.UnitTransactions;
import static io.github.loadup.acquire.app.service.CheckoutFixtures.clock;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.VERSION_CONFLICT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.loadup.acquire.app.converter.MerchantNotificationQueryConverter;
import io.github.loadup.acquire.app.security.MerchantNotificationAccess;
import io.github.loadup.acquire.app.security.MerchantNotificationActor;
import io.github.loadup.acquire.client.command.MerchantNotificationWakeCommand;
import io.github.loadup.acquire.client.query.MerchantNotificationWakePageQuery;
import io.github.loadup.acquire.domain.gateway.MerchantNotificationGateway;
import io.github.loadup.acquire.domain.gateway.MerchantNotificationWakeGateway;
import io.github.loadup.acquire.domain.model.MerchantNotificationDelivery;
import io.github.loadup.acquire.domain.model.MerchantNotificationKey;
import io.github.loadup.acquire.domain.model.MerchantNotificationPolicy;
import io.github.loadup.acquire.domain.model.MerchantNotificationWakeReceipt;
import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.task.TaskExecution;
import io.github.loadup.components.task.TaskResultCodeEnum;
import io.github.loadup.components.task.TaskStatus;
import io.github.loadup.components.task.TaskSubmission;
import io.github.loadup.components.task.TaskTemplate;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MerchantNotificationOperationsServiceTest {
    final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(MerchantNotificationQueryConverter.class.getPackageName());
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AtomicReference<MerchantNotificationActor> actor = new AtomicReference<>(operator("operator-a"));
    final MerchantNotificationGateway deliveries = mock(MerchantNotificationGateway.class);
    final MerchantNotificationWakeGateway receipts = mock(MerchantNotificationWakeGateway.class);
    final TaskTemplate tasks = mock(TaskTemplate.class);
    final AtomicReference<MerchantNotificationDelivery> current = new AtomicReference<>(delivery(NOW.minusMinutes(1)));
    final AtomicReference<TaskExecution> nativeTask = new AtomicReference<>();
    final Map<String, MerchantNotificationWakeReceipt> accepted = new HashMap<>();
    final UnitTransactions manager = new UnitTransactions();
    final AtomicInteger ids = new AtomicInteger();
    final MerchantNotificationOperationsService service = new MerchantNotificationOperationsService(
            new MerchantNotificationAccess(actor::get, "pay-ops"),
            deliveries,
            receipts,
            tasks,
            context.getBean(MerchantNotificationQueryConverter.class),
            new OrderCreationTransactions(manager),
            () -> "wake-" + ids.incrementAndGet(),
            validation.getValidator(),
            clock(NOW));

    static MerchantNotificationActor operator(String subject) {
        return new MerchantNotificationActor("OPERATOR", subject, null, Set.of("pay:acquire:notification:manage"));
    }

    static MerchantNotificationDelivery delivery(LocalDateTime at) {
        return MerchantNotificationDelivery.init(
                "delivery",
                "event",
                "order",
                "merchant",
                "PaymentSucceeded",
                "payment",
                7,
                "frozen-body",
                "a".repeat(64),
                new MerchantNotificationPolicy("original-target", "original-key", 1000, 5, BigDecimal.ONE, 60, 3, 3600),
                at);
    }

    MerchantNotificationWakeCommand command() {
        return new MerchantNotificationWakeCommand(
                "delivery", "operation", current.get().rowVersion(), "Recover the current task");
    }

    TaskExecution task(TaskStatus status) {
        var row = current.get();
        return new TaskExecution(
                "native-task",
                MerchantNotificationService.TYPE,
                new MerchantNotificationKey(row.id(), row.attemptCount()).bizId(),
                row.nextAttemptAt(),
                row.nextAttemptAt(),
                status,
                5,
                5,
                status == TaskStatus.RUNNING ? NOW.plusMinutes(1) : null,
                null);
    }

    @BeforeEach
    void setup() {
        when(deliveries.lock("delivery")).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return Optional.of(current.get());
        });
        when(deliveries.find("delivery")).thenAnswer(c -> Optional.of(current.get()));
        when(receipts.find(anyString(), anyString()))
                .thenAnswer(c ->
                        Optional.ofNullable(accepted.get(c.<String>getArgument(0) + ":" + c.<String>getArgument(1))));
        doAnswer(c -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    var receipt = c.<MerchantNotificationWakeReceipt>getArgument(0);
                    accepted.put(receipt.callerScopeHash() + ":" + receipt.requestKey(), receipt);
                    return null;
                })
                .when(receipts)
                .insert(any());
        when(tasks.find(eq(MerchantNotificationService.TYPE), anyString()))
                .thenAnswer(c -> Optional.ofNullable(nativeTask.get()));
        when(tasks.find(anyString()))
                .thenAnswer(c -> Optional.ofNullable(nativeTask.get())
                        .filter(task -> task.id().equals(c.getArgument(0))));
        when(tasks.submit(ArgumentMatchers.any(TaskSubmission.class))).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            TaskSubmission submission = c.getArgument(0);
            assertEquals(MerchantNotificationService.TYPE, submission.taskType());
            assertEquals("delivery:0", submission.bizId());
            assertEquals(current.get().nextAttemptAt(), submission.executeAt());
            nativeTask.set(task(TaskStatus.PENDING));
            return nativeTask.get().id();
        });
    }

    @AfterEach
    void close() {
        validation.close();
        context.close();
    }

    @Test
    void missingWakeIsRegisteredAndAuditedWithoutChangingDeliveryBudgetOrIdentity() {
        var original = current.get();
        var result = service.wake(command());
        assertEquals("REGISTERED", result.result());
        assertEquals("native-task", result.eventId());
        assertEquals(0, result.attemptCount());
        assertEquals(original, current.get());
        assertEquals(1, accepted.size());
        assertEquals(1, manager.commits);
        verify(tasks)
                .submit(ArgumentMatchers.eq(TaskSubmission.scheduled(
                        MerchantNotificationService.TYPE, "delivery:0", original.nextAttemptAt())));
        verify(deliveries, never()).update(any(), any());
    }

    @Test
    void sameRequestReplaysTheAuditWithoutDependingOnTheCurrentDelivery() {
        var command = command();
        var first = service.wake(command);
        when(deliveries.lock(anyString())).thenThrow(new CommonException(DEPENDENCY_UNAVAILABLE));
        assertEquals(first, service.wake(command));
        verify(tasks, times(1)).submit(ArgumentMatchers.any(TaskSubmission.class));
        verify(receipts, times(1)).insert(any());
        assertEquals(1, manager.commits);
    }

    @Test
    void changedReasonOrVersionOnTheSameKeyIsRejected() {
        var original = command();
        service.wake(original);
        assertSame(
                IDEMPOTENCY_CONFLICT,
                assertThrows(
                                CommonException.class,
                                () -> service.wake(new MerchantNotificationWakeCommand(
                                        original.id(),
                                        original.requestKey(),
                                        original.expectedVersion(),
                                        "Different reason")))
                        .getResultCode());
        assertSame(
                IDEMPOTENCY_CONFLICT,
                assertThrows(
                                CommonException.class,
                                () -> service.wake(new MerchantNotificationWakeCommand(
                                        original.id(),
                                        original.requestKey(),
                                        original.expectedVersion() + 1,
                                        original.reason())))
                        .getResultCode());
        verify(tasks, times(1)).submit(ArgumentMatchers.any(TaskSubmission.class));
    }

    @Test
    void failedNativeTaskRetriesAndCancelledTaskReschedulesOnlyTheOriginalDueTime() {
        nativeTask.set(task(TaskStatus.FAILED));
        assertEquals("RETRIED", service.wake(command()).result());
        verify(tasks).retryFailed("native-task", "operator-a", "Recover the current task");
        nativeTask.set(task(TaskStatus.CANCELLED));
        var other = new MerchantNotificationWakeCommand(
                "delivery", "operation-2", current.get().rowVersion(), "Resume cancelled wake");
        assertEquals("RESCHEDULED", service.wake(other).result());
        verify(tasks).reschedule("native-task", current.get().nextAttemptAt());
        verify(deliveries, never()).update(any(), any());
    }

    @Test
    void pendingAndRunningTasksAreNotResetOrStolen() {
        nativeTask.set(task(TaskStatus.PENDING));
        assertEquals("PENDING", service.wake(command()).result());
        nativeTask.set(task(TaskStatus.RUNNING));
        assertEquals(
                "RUNNING",
                service.wake(new MerchantNotificationWakeCommand("delivery", "operation-2", 0L, "Observe running task"))
                        .result());
        verify(tasks, never()).retryFailed(anyString(), anyString(), anyString());
        verify(tasks, never()).reschedule(anyString(), any());
        verify(tasks, never()).submit(ArgumentMatchers.any(TaskSubmission.class));
    }

    @Test
    void futureBusinessBackoffCannotBeAccelerated() {
        current.set(current.get().begin(NOW, "b".repeat(64)));
        assertEquals("WAITING", service.wake(command()).result());
        verifyNoInteractions(tasks);
    }

    @Test
    void ackedExpiredAndExhaustedDeliveriesNeverReceiveNewPermits() {
        var original = current.get();
        current.set(original.begin(original.nextAttemptAt(), "b".repeat(64)).acknowledge(NOW));
        assertEquals("ACKED", service.wake(command()).result());
        current.set(delivery(NOW.minusHours(2)));
        assertEquals(
                "EXPIRED",
                service.wake(new MerchantNotificationWakeCommand("delivery", "operation-2", 0L, "Observe expiration"))
                        .result());
        var spent = original;
        for (int i = 0; i < 3; i++) {
            spent = spent.begin(spent.nextAttemptAt(), "b".repeat(64));
        }
        current.set(spent);
        assertEquals(
                "EXHAUSTED",
                service.wake(new MerchantNotificationWakeCommand(
                                "delivery", "operation-3", spent.rowVersion(), "Observe exhausted budget"))
                        .result());
        verifyNoInteractions(tasks);
        verify(deliveries, never()).update(any(), any());
    }

    @Test
    void staleVersionsAndImpossibleSucceededCurrentTasksFailWithoutReceipts() {
        assertSame(
                VERSION_CONFLICT,
                assertThrows(
                                CommonException.class,
                                () -> service.wake(new MerchantNotificationWakeCommand(
                                        "delivery", "operation", 1L, "Stale observation")))
                        .getResultCode());
        nativeTask.set(task(TaskStatus.SUCCEEDED));
        assertSame(
                SNAPSHOT_INVALID,
                assertThrows(CommonException.class, () -> service.wake(command()))
                        .getResultCode());
        assertTrue(accepted.isEmpty());
    }

    @Test
    void onlyOperatorsOrTheConfiguredOperationsServiceCanWake() {
        actor.set(new MerchantNotificationActor(
                "MERCHANT", "merchant", "merchant", Set.of("pay:acquire:notification:manage")));
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> service.wake(command()))
                        .getResultCode());
        actor.set(new MerchantNotificationActor("SERVICE", "payment", null, Set.of("pay:acquire:notification:manage")));
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> service.wake(command()))
                        .getResultCode());
        actor.set(new MerchantNotificationActor("SERVICE", "pay-ops", null, Set.of("pay:acquire:notification:manage")));
        assertEquals("REGISTERED", service.wake(command()).result());
    }

    @Test
    void auditFailureAbortsTheWholeLocalTransactionBoundary() {
        doThrow(new CommonException(CONCURRENT_UPDATE)).when(receipts).insert(any());
        assertThrows(CommonException.class, () -> service.wake(command()));
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
        // Native task and audit rollback requires real PostgreSQL proof; the fake manager checks propagation only.
    }

    @Test
    void nativeStateRaceReturnsModuleConflictWithoutAnAuditSuccess() {
        nativeTask.set(task(TaskStatus.FAILED));
        doThrow(new CommonException(TaskResultCodeEnum.INVALID_STATE))
                .when(tasks)
                .retryFailed(anyString(), anyString(), anyString());
        assertSame(
                CONCURRENT_UPDATE,
                assertThrows(CommonException.class, () -> service.wake(command()))
                        .getResultCode());
        verify(receipts, never()).insert(any());
        assertEquals(1, manager.rollbacks);
    }

    @Test
    void historyIsOperatorOnlyAndKeepsPaginationMetadata() {
        service.wake(command());
        var record = accepted.values().iterator().next();
        when(receipts.page("delivery", 2, 10)).thenReturn(PageResult.of(List.of(record), 11, 2, 10));
        var page = service.wakes(new MerchantNotificationWakePageQuery("delivery", 2, 10));
        var wake = page.getData().iterator().next();
        assertEquals(record.id(), wake.id());
        assertEquals(record.reason(), wake.reason());
        actor.set(new MerchantNotificationActor(
                "MERCHANT_APP", "app", "merchant", Set.of("pay:acquire:notification:read")));
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(
                                CommonException.class,
                                () -> service.wakes(new MerchantNotificationWakePageQuery("delivery", 1, 20)))
                        .getResultCode());
    }
}
