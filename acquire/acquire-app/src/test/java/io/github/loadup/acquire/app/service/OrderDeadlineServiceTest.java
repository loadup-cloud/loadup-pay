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
import static io.github.loadup.acquire.app.service.CheckoutFixtures.OWNER;
import static io.github.loadup.acquire.app.service.CheckoutFixtures.UnitTransactions;
import static io.github.loadup.acquire.app.service.CheckoutFixtures.clock;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.loadup.acquire.client.command.AcquireOrderCloseCommand;
import io.github.loadup.acquire.client.enums.UnpaidOrderAction;
import io.github.loadup.acquire.domain.gateway.OrderClosureGateway;
import io.github.loadup.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.acquire.domain.gateway.OrderExtensionGateway;
import io.github.loadup.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.delayedtask.DelayedTask;
import io.github.loadup.components.delayedtask.DelayedTaskStatus;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import io.github.loadup.contracts.event.acquire.AcquireOrderState;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class OrderDeadlineServiceTest {
    final DelayedTaskTemplate tasks = mock(DelayedTaskTemplate.class);
    final OrderCreationGateway orders = mock(OrderCreationGateway.class);
    final OrderExtensionGateway extensions = mock(OrderExtensionGateway.class);
    final OrderClosureGateway closes = mock(OrderClosureGateway.class);
    final OrderClosureOperations operations = mock(OrderClosureOperations.class);
    final OrderSnapshotEvents snapshots = mock(OrderSnapshotEvents.class);
    final UnitTransactions manager = new UnitTransactions();
    final OrderCreationTransactions transactions = new OrderCreationTransactions(manager);
    final OrderDeadlineService service = new OrderDeadlineService(
            tasks, orders, extensions, closes, operations, transactions, snapshots, clock(NOW));
    final AtomicReference<StoredAcquireOrder> order =
            new AtomicReference<>(CheckoutFixtures.order(AcquireOrderState.INIT, UnpaidOrderAction.CLOSE, NOW));
    final DelayedTask task = new DelayedTask(
            "due-task",
            OrderDeadlineTasks.TYPE,
            "order.1",
            NOW,
            NOW,
            DelayedTaskStatus.RUNNING,
            1,
            5,
            NOW.plusMinutes(5),
            null);

    @BeforeEach
    void setup() {
        when(tasks.find(OrderDeadlineTasks.TYPE, "order.1")).thenReturn(Optional.of(task));
        when(orders.lockOrder("order")).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("tenant", TenantUtil.getTenantId());
            return order.get();
        });
        when(extensions.deadlineVersion(any())).thenReturn(1L);
    }

    void handle() {
        TenantUtil.runWithTenant("tenant", () -> service.handle(task));
    }

    @Test
    void dueCloseStartsC0WithThePersistedOwnerInsideTheDueTransaction() {
        doAnswer(c -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    assertEquals(new OrderPaymentScope("tenant", OWNER), c.getArgument(0));
                    assertEquals(order.get(), c.getArgument(1));
                    return null;
                })
                .when(operations)
                .startLocked(any(), any(), any());
        handle();
        verify(operations)
                .startLocked(
                        new OrderPaymentScope("tenant", OWNER),
                        order.get(),
                        new AcquireOrderCloseCommand("order", "order-deadline/1", "ORDER_EXPIRED"));
        verify(operations, never()).resume(any(), any());
        verify(closes, never()).keepPending(any(), any());
        assertEquals(1, manager.commits);
    }

    @Test
    void keepPendingChangesOnlyTheDomainDueStateAndPublishesItsVersion() {
        order.set(CheckoutFixtures.order(AcquireOrderState.INIT, UnpaidOrderAction.KEEP_PENDING, NOW));
        var expected = order.get().order().checkDue(NOW);
        doAnswer(c -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    return null;
                })
                .when(closes)
                .keepPending(any(), any());
        handle();
        verify(closes).keepPending(order.get().order(), expected);
        verify(snapshots).publish("tenant", "order", expected.version());
        assertTrue(expected.canAcceptNewPayment(NOW.plusDays(1)));
        verifyNoInteractions(operations);
    }

    @Test
    void anAlreadyPendingKeepPendingOrderHasNoRepeatedStateWrite() {
        order.set(CheckoutFixtures.order(AcquireOrderState.PENDING, UnpaidOrderAction.KEEP_PENDING, NOW));
        handle();
        verifyNoInteractions(operations, closes, snapshots);
    }

    @Test
    void extensionVersionMakesTheOldNativeDeadlineHarmless() {
        order.set(CheckoutFixtures.order(AcquireOrderState.INIT, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10)));
        when(extensions.deadlineVersion(any())).thenReturn(2L);
        handle();
        verifyNoInteractions(operations, closes, snapshots);
    }

    @Test
    void completedOrdersNeverStartAnotherClose() {
        order.set(CheckoutFixtures.order(AcquireOrderState.PAID, UnpaidOrderAction.CLOSE, NOW));
        handle();
        verifyNoInteractions(operations, closes, snapshots);
    }

    @Test
    void sameVersionWithAnotherDeadlineOrAFutureVersionIsCorruption() {
        order.set(CheckoutFixtures.order(AcquireOrderState.INIT, UnpaidOrderAction.CLOSE, NOW.plusSeconds(1)));
        assertSame(
                SNAPSHOT_INVALID,
                assertThrows(CommonException.class, this::handle).getResultCode());
        when(extensions.deadlineVersion(any())).thenReturn(0L);
        assertSame(
                SNAPSHOT_INVALID,
                assertThrows(CommonException.class, this::handle).getResultCode());
        verifyNoInteractions(operations, closes, snapshots);
    }

    @Test
    void aTaskWithoutItsLiveNativeClaimCannotAcquireBusinessLocks() {
        when(tasks.find(OrderDeadlineTasks.TYPE, "order.1")).thenReturn(Optional.empty());
        assertSame(
                SNAPSHOT_INVALID,
                assertThrows(CommonException.class, this::handle).getResultCode());
        verifyNoInteractions(orders, extensions, closes, operations);
    }
}
