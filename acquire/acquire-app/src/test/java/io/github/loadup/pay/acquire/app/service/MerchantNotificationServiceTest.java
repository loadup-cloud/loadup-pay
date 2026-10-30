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
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.delayedtask.DelayedTask;
import io.github.loadup.components.delayedtask.DelayedTaskStatus;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import io.github.loadup.pay.acquire.app.port.MerchantNotificationTransport;
import io.github.loadup.pay.acquire.domain.gateway.MerchantNotificationGateway;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationDelivery;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationPolicy;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MerchantNotificationServiceTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 12, 0);
    private static final String TENANT = "tenant";
    private static final String BODY = "frozen merchant event body";
    private static final String DIGEST = "a".repeat(64);
    private static final String ROUTE = "b".repeat(64);
    private static final String CHANGED_ROUTE = "c".repeat(64);

    private final MerchantNotificationGateway deliveries = mock(MerchantNotificationGateway.class);
    private final MerchantNotificationTransport transport = mock(MerchantNotificationTransport.class);
    private final DelayedTaskTemplate tasks = mock(DelayedTaskTemplate.class);
    private final CheckoutFixtures.UnitTransactions manager = new CheckoutFixtures.UnitTransactions();
    private final OrderCreationTransactions transactions = new OrderCreationTransactions(manager);
    private final AtomicReference<MerchantNotificationDelivery> current = new AtomicReference<>();
    private final Map<String, DelayedTask> nativeTasks = new HashMap<>();
    private final ArrayList<String> operations = new ArrayList<>();

    private static DelayedTask runningTask(String bizId, LocalDateTime executeAt, LocalDateTime nextExecuteAt,
                                           String id, int attempts, LocalDateTime leaseUntil) {
        return new DelayedTask(id, MerchantNotificationService.TYPE, bizId, executeAt, nextExecuteAt,
                DelayedTaskStatus.RUNNING, attempts, 5, leaseUntil, null);
    }

    private static MerchantNotificationDelivery delivery(MerchantNotificationPolicy policy) {
        return MerchantNotificationDelivery.init("delivery", "event", "order", "merchant", "PaymentSucceeded",
                "payment", 7, BODY, DIGEST, policy, NOW);
    }

    private static MerchantNotificationPolicy policy(long initialSeconds, int maxAttempts, long windowSeconds) {
        return new MerchantNotificationPolicy("target", "signing-key", 1000, initialSeconds,
                BigDecimal.ONE, 60, maxAttempts, windowSeconds);
    }

    @BeforeEach
    void setup() {
        current.set(delivery(policy(5, 5, 3600)));
        when(deliveries.lock(TENANT, "delivery")).thenAnswer(call -> Optional.of(current.get()));
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(TENANT, TenantUtil.getTenantId());
            assertEquals(current.get(), call.getArgument(1));
            current.set(call.getArgument(2));
            operations.add("update:" + current.get().state());
            return null;
        }).when(deliveries).update(eq(TENANT), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        when(tasks.find(eq(MerchantNotificationService.TYPE), anyString()))
                .thenAnswer(call -> Optional.ofNullable(nativeTasks.get(call.getArgument(1))));
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(TENANT, TenantUtil.getTenantId());
            operations.add("register:" + call.getArgument(1));
            return null;
        }).when(tasks).register(eq(MerchantNotificationService.TYPE), anyString(), org.mockito.ArgumentMatchers.any());
        when(transport.fingerprint(eq(TENANT), org.mockito.ArgumentMatchers.any())).thenReturn(ROUTE);
        when(transport.send(eq(TENANT), org.mockito.ArgumentMatchers.any())).thenReturn(false);
    }

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    @Test
    void registersSuccessorBeforeSendingAndFalseAcknowledgementLeavesDeliveryPending() {
        var task = runningTask("delivery:0", NOW, NOW, "native-0", 1, NOW.plusMinutes(1));
        nativeTasks.put(task.bizId(), task);
        when(transport.send(eq(TENANT), org.mockito.ArgumentMatchers.any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(MerchantNotificationState.PENDING, current.get().state());
            assertTrue(operations.stream().anyMatch(value -> value.startsWith("register:delivery:1")));
            operations.add("send");
            return false;
        });

        invoke(task, service(NOW));

        assertEquals(MerchantNotificationState.PENDING, current.get().state());
        assertEquals(1, current.get().attemptCount());
        assertEquals(BODY, current.get().body());
        assertEquals(ROUTE, current.get().routeFingerprint());
        assertTrue(operations.indexOf("register:delivery:1") < operations.indexOf("send"));
        verify(tasks).register(eq(MerchantNotificationService.TYPE), eq("delivery:1"), eq(current.get().nextAttemptAt()));
    }

    @Test
    void acknowledgesOnlyAfterTheTransportReturnsOutsideTheTransaction() {
        var task = runningTask("delivery:0", NOW, NOW, "native-0", 1, NOW.plusMinutes(1));
        nativeTasks.put(task.bizId(), task);
        when(transport.send(eq(TENANT), org.mockito.ArgumentMatchers.any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(MerchantNotificationState.PENDING, current.get().state());
            operations.add("send");
            return true;
        });

        invoke(task, service(NOW));

        assertEquals(MerchantNotificationState.ACKED, current.get().state());
        assertEquals(1, current.get().attemptCount());
        assertEquals(NOW, current.get().acknowledgedAt());
        assertTrue(operations.indexOf("send") < operations.indexOf("update:ACKED"));
    }

    @Test
    void duplicateNativeGenerationCannotSendAfterItsCursorWasAdvanced() {
        var first = runningTask("delivery:0", NOW, NOW, "native-0", 1, NOW.plusMinutes(1));
        nativeTasks.put(first.bizId(), first);
        invoke(first, service(NOW));
        var duplicate = runningTask("delivery:0", NOW, NOW, "native-0-reclaimed", 2, NOW.plusMinutes(1));
        nativeTasks.put(duplicate.bizId(), duplicate);

        invoke(duplicate, service(NOW.plusSeconds(1)));

        assertEquals(1, current.get().attemptCount());
        verify(transport, times(1)).send(eq(TENANT), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mismatchedRegisteredTaskAndExpiredLeaseAreRejectedBeforeDeliveryReads() {
        var presented = runningTask("delivery:0", NOW, NOW, "presented", 1, NOW.plusMinutes(1));
        nativeTasks.put(presented.bizId(), runningTask("delivery:0", NOW, NOW, "registered", 1, NOW.plusMinutes(1)));

        assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> invoke(presented, service(NOW)))
                .getResultCode());

        var expired = runningTask("delivery:0", NOW, NOW, "expired", 1, NOW);
        nativeTasks.put(expired.bizId(), expired);
        assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> invoke(expired, service(NOW)))
                .getResultCode());
        verify(deliveries, never()).lock(eq(TENANT), eq("delivery"));
        verify(transport, never()).send(eq(TENANT), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void exhaustedAttemptLimitAndExpiredWindowDoNotSend() {
        var oneAttempt = policy(5, 1, 3600);
        current.set(delivery(oneAttempt).begin(NOW, ROUTE));
        var atLimit = runningTask("delivery:1", current.get().nextAttemptAt(), current.get().nextAttemptAt(),
                "native-limit", 1, NOW.plusMinutes(1));
        nativeTasks.put(atLimit.bizId(), atLimit);

        invoke(atLimit, service(atLimit.executeAt()));
        assertEquals(MerchantNotificationState.EXHAUSTED, current.get().state());
        verify(transport, never()).send(eq(TENANT), org.mockito.ArgumentMatchers.any());

        current.set(delivery(policy(5, 5, 1)));
        var expired = runningTask("delivery:0", NOW, NOW, "native-expired-window", 1, NOW.plusMinutes(1));
        nativeTasks.put(expired.bizId(), expired);
        invoke(expired, service(NOW.plusSeconds(1)));
        assertEquals(MerchantNotificationState.EXHAUSTED, current.get().state());
        verify(transport, never()).send(eq(TENANT), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void routeFingerprintStaysFrozenAcrossLaterConfigurationChanges() {
        var first = runningTask("delivery:0", NOW, NOW, "native-0", 1, NOW.plusMinutes(1));
        nativeTasks.put(first.bizId(), first);
        invoke(first, service(NOW));
        when(transport.fingerprint(eq(TENANT), org.mockito.ArgumentMatchers.any())).thenReturn(CHANGED_ROUTE);
        var due = current.get().nextAttemptAt();
        var second = runningTask("delivery:1", due, due, "native-1", 1, NOW.plusMinutes(1));
        nativeTasks.put(second.bizId(), second);

        invoke(second, service(due.plusSeconds(1)));

        assertEquals(ROUTE, current.get().routeFingerprint());
        assertEquals(2, current.get().attemptCount());
        verify(transport, times(2)).send(eq(TENANT), org.mockito.ArgumentMatchers.argThat(value ->
                ((MerchantNotificationDelivery) value).routeFingerprint().equals(ROUTE)));
    }

    private MerchantNotificationService service(LocalDateTime now) {
        var clock = Clock.fixed(now.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        return new MerchantNotificationService(deliveries, transport, tasks, transactions, clock);
    }

    private void invoke(DelayedTask task, MerchantNotificationService service) {
        TenantUtil.runWithTenant(TENANT, () -> service.handle(task));
    }
}
