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

import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.delayedtask.DelayedTask;
import io.github.loadup.components.delayedtask.DelayedTaskResultCodeEnum;
import io.github.loadup.components.delayedtask.DelayedTaskStatus;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import io.github.loadup.pay.acquire.app.converter.MerchantNotificationQueryConverter;
import io.github.loadup.pay.acquire.app.security.MerchantNotificationAccess;
import io.github.loadup.pay.acquire.app.security.MerchantNotificationActor;
import io.github.loadup.pay.acquire.client.command.MerchantNotificationWakeCommand;
import io.github.loadup.pay.acquire.client.query.MerchantNotificationWakePageQuery;
import io.github.loadup.pay.acquire.domain.gateway.MerchantNotificationGateway;
import io.github.loadup.pay.acquire.domain.gateway.MerchantNotificationWakeGateway;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationDelivery;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationKey;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationPolicy;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationWakeReceipt;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.NOW;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.UnitTransactions;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.clock;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.VERSION_CONFLICT;
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

class MerchantNotificationOperationsServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MerchantNotificationQueryConverter.class.getPackageName());
    final jakarta.validation.ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AtomicReference<MerchantNotificationActor> actor = new AtomicReference<>(operator("operator-a"));
    final MerchantNotificationGateway deliveries = mock(MerchantNotificationGateway.class);
    final MerchantNotificationWakeGateway receipts = mock(MerchantNotificationWakeGateway.class);
    final DelayedTaskTemplate tasks = mock(DelayedTaskTemplate.class);
    final AtomicReference<MerchantNotificationDelivery> current = new AtomicReference<>(delivery(NOW.minusMinutes(1)));
    final AtomicReference<DelayedTask> nativeTask = new AtomicReference<>();
    final Map<String, MerchantNotificationWakeReceipt> accepted = new HashMap<>();
    final UnitTransactions manager = new UnitTransactions();
    final AtomicInteger ids = new AtomicInteger();
    final MerchantNotificationOperationsService service = new MerchantNotificationOperationsService(new MerchantNotificationAccess(actor::get, "pay-ops"),
            deliveries, receipts, tasks, context.getBean(MerchantNotificationQueryConverter.class), new OrderCreationTransactions(manager),
            () -> "wake-" + ids.incrementAndGet(), validation.getValidator(), clock(NOW));

    static MerchantNotificationActor operator(String subject) {
        return new MerchantNotificationActor("tenant", "OPERATOR", subject, null, Set.of("pay:acquire:notification:manage"));
    }

    static MerchantNotificationDelivery delivery(LocalDateTime at) {
        return MerchantNotificationDelivery.init("delivery", "event", "order", "merchant", "PaymentSucceeded", "payment", 7,
                "frozen-body", "a".repeat(64), new MerchantNotificationPolicy("original-target", "original-key", 1000, 5, BigDecimal.ONE, 60, 3, 3600), at);
    }

    MerchantNotificationWakeCommand command() {
        return new MerchantNotificationWakeCommand("delivery", "operation", current.get().rowVersion(), "Recover the current task");
    }

    DelayedTask task(DelayedTaskStatus status) {
        var row = current.get();
        return new DelayedTask("native-task", "tenant", MerchantNotificationService.TYPE, new MerchantNotificationKey(row.id(), row.attemptCount()).bizId(),
                row.nextAttemptAt(), row.nextAttemptAt(), status, 5, 5, status == DelayedTaskStatus.RUNNING ? NOW.plusMinutes(1) : null, null);
    }

    @BeforeEach
    void setup() {
        when(deliveries.lock("tenant", "delivery")).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("tenant", TenantUtil.getTenantId());
            return Optional.of(current.get());
        });
        when(deliveries.find("tenant", "delivery")).thenAnswer(c -> Optional.of(current.get()));
        when(receipts.find(eq("tenant"), anyString(), anyString())).thenAnswer(c -> Optional.ofNullable(accepted.get(c.<String>getArgument(1) + ":" + c.<String>getArgument(2))));
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            var receipt = c.<MerchantNotificationWakeReceipt>getArgument(1);
            accepted.put(receipt.callerScopeHash() + ":" + receipt.requestKey(), receipt);
            return null;
        }).when(receipts).insert(eq("tenant"), any());
        when(tasks.find(eq(MerchantNotificationService.TYPE), anyString())).thenAnswer(c -> Optional.ofNullable(nativeTask.get()));
        when(tasks.register(eq(MerchantNotificationService.TYPE), anyString(), any())).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(current.get().nextAttemptAt(), c.getArgument(2));
            return task(DelayedTaskStatus.PENDING);
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
        assertEquals("event", result.eventId());
        assertEquals(0, result.attemptCount());
        assertEquals(original, current.get());
        assertEquals(1, accepted.size());
        assertEquals(1, manager.commits);
        verify(tasks).register(MerchantNotificationService.TYPE, "delivery:0", original.nextAttemptAt());
        verify(deliveries, never()).update(anyString(), any(), any());
    }

    @Test
    void sameRequestReplaysTheAuditWithoutDependingOnTheCurrentDelivery() {
        var command = command();
        var first = service.wake(command);
        when(deliveries.lock(anyString(), anyString())).thenThrow(new CommonException(DEPENDENCY_UNAVAILABLE));
        assertEquals(first, service.wake(command));
        verify(tasks, times(1)).register(anyString(), anyString(), any());
        verify(receipts, times(1)).insert(anyString(), any());
        assertEquals(1, manager.commits);
    }

    @Test
    void changedReasonOrVersionOnTheSameKeyIsRejected() {
        var original = command();
        service.wake(original);
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service.wake(new MerchantNotificationWakeCommand(
                original.id(), original.requestKey(), original.expectedVersion(), "Different reason"))).getResultCode());
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service.wake(new MerchantNotificationWakeCommand(
                original.id(), original.requestKey(), original.expectedVersion() + 1, original.reason()))).getResultCode());
        verify(tasks, times(1)).register(anyString(), anyString(), any());
    }

    @Test
    void failedNativeTaskRetriesAndCancelledTaskReschedulesOnlyTheOriginalDueTime() {
        nativeTask.set(task(DelayedTaskStatus.FAILED));
        assertEquals("RETRIED", service.wake(command()).result());
        verify(tasks).retryFailed(MerchantNotificationService.TYPE, "delivery:0");
        nativeTask.set(task(DelayedTaskStatus.CANCELLED));
        var other = new MerchantNotificationWakeCommand("delivery", "operation-2", current.get().rowVersion(), "Resume cancelled wake");
        assertEquals("RESCHEDULED", service.wake(other).result());
        verify(tasks).reschedule(MerchantNotificationService.TYPE, "delivery:0", current.get().nextAttemptAt());
        verify(deliveries, never()).update(anyString(), any(), any());
    }

    @Test
    void pendingAndRunningTasksAreNotResetOrStolen() {
        nativeTask.set(task(DelayedTaskStatus.PENDING));
        assertEquals("PENDING", service.wake(command()).result());
        nativeTask.set(task(DelayedTaskStatus.RUNNING));
        assertEquals("RUNNING", service.wake(new MerchantNotificationWakeCommand("delivery", "operation-2", 0L, "Observe running task")).result());
        verify(tasks, never()).retryFailed(anyString(), anyString());
        verify(tasks, never()).reschedule(anyString(), anyString(), any());
        verify(tasks, never()).register(anyString(), anyString(), any());
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
        assertEquals("EXPIRED", service.wake(new MerchantNotificationWakeCommand("delivery", "operation-2", 0L, "Observe expiration")).result());
        var spent = original;
        for (int i = 0; i < 3; i++) spent = spent.begin(spent.nextAttemptAt(), "b".repeat(64));
        current.set(spent);
        assertEquals("EXHAUSTED", service.wake(new MerchantNotificationWakeCommand("delivery", "operation-3", spent.rowVersion(), "Observe exhausted budget")).result());
        verifyNoInteractions(tasks);
        verify(deliveries, never()).update(anyString(), any(), any());
    }

    @Test
    void staleVersionsAndImpossibleSucceededCurrentTasksFailWithoutReceipts() {
        assertSame(VERSION_CONFLICT, assertThrows(CommonException.class, () -> service.wake(new MerchantNotificationWakeCommand(
                "delivery", "operation", 1L, "Stale observation"))).getResultCode());
        nativeTask.set(task(DelayedTaskStatus.SUCCEEDED));
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> service.wake(command())).getResultCode());
        assertTrue(accepted.isEmpty());
    }

    @Test
    void onlyOperatorsOrTheConfiguredOperationsServiceCanWake() {
        actor.set(new MerchantNotificationActor("tenant", "MERCHANT", "merchant", "merchant", Set.of("pay:acquire:notification:manage")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.wake(command())).getResultCode());
        actor.set(new MerchantNotificationActor("tenant", "SERVICE", "payment", null, Set.of("pay:acquire:notification:manage")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.wake(command())).getResultCode());
        actor.set(new MerchantNotificationActor("tenant", "SERVICE", "pay-ops", null, Set.of("pay:acquire:notification:manage")));
        assertEquals("REGISTERED", service.wake(command()).result());
    }

    @Test
    void auditFailureAbortsTheWholeLocalTransactionBoundary() {
        doThrow(new CommonException(CONCURRENT_UPDATE)).when(receipts).insert(anyString(), any());
        assertThrows(CommonException.class, () -> service.wake(command()));
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
        // Native task and audit rollback requires real PostgreSQL proof; the fake manager checks propagation only.
    }

    @Test
    void nativeStateRaceReturnsModuleConflictWithoutAnAuditSuccess() {
        nativeTask.set(task(DelayedTaskStatus.FAILED));
        doThrow(new CommonException(DelayedTaskResultCodeEnum.INVALID_STATE)).when(tasks).retryFailed(anyString(), anyString());
        assertSame(CONCURRENT_UPDATE, assertThrows(CommonException.class, () -> service.wake(command())).getResultCode());
        verify(receipts, never()).insert(anyString(), any());
        assertEquals(1, manager.rollbacks);
    }

    @Test
    void historyIsOperatorOnlyAndKeepsPaginationMetadata() {
        service.wake(command());
        var record = accepted.values().iterator().next();
        when(receipts.page("tenant", "delivery", 2, 10)).thenReturn(PageResult.of(List.of(record), 11, 2, 10));
        var page = service.wakes(new MerchantNotificationWakePageQuery("delivery", 2, 10));
        var wake = page.getData().iterator().next();
        assertEquals(record.id(), wake.id());
        assertEquals(record.reason(), wake.reason());
        actor.set(new MerchantNotificationActor("tenant", "MERCHANT_APP", "app", "merchant", Set.of("pay:acquire:notification:read")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.wakes(new MerchantNotificationWakePageQuery("delivery", 1, 20))).getResultCode());
    }
}
