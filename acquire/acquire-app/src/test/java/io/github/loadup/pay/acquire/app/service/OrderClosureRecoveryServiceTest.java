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
import io.github.loadup.components.delayedtask.*;
import io.github.loadup.pay.acquire.domain.gateway.*;
import io.github.loadup.pay.acquire.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Optional;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.*;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderClosureRecoveryServiceTest {
    final DelayedTaskTemplate tasks = mock(DelayedTaskTemplate.class);
    final OrderCreationGateway orders = mock(OrderCreationGateway.class);
    final OrderClosureGateway closes = mock(OrderClosureGateway.class);
    final OrderClosureOperations operations = mock(OrderClosureOperations.class);
    final UnitTransactions manager = new UnitTransactions();
    final OrderCreationTransactions transactions = new OrderCreationTransactions(manager);
    final OrderClosureRecoveryService service = new OrderClosureRecoveryService(tasks, orders, closes, operations, transactions, clock(NOW));
    final StoredAcquireOrder original = CheckoutFixtures.order(AcquireOrderState.INIT, UnpaidOrderAction.CLOSE, NOW);
    final StoredAcquireOrder order = new StoredAcquireOrder(original.order().requestClose(), original.merchantOrderNo(), original.subject(),
            original.admission(), original.admittedAt(), original.callerScopeHash(), original.snapshotId(), original.admissionPayload(), original.payloadHash());
    final OrderCloseIntent intent = new OrderCloseIntent("close-intent", "order", "merchant", OWNER, "original-key", "e".repeat(64),
            "MERCHANT_CLOSED", 1L, NOW.minusMinutes(1), "original-payment-cursor", 3L);
    final DelayedTask task = new DelayedTask("close-task", "tenant", OrderClosureTasks.TYPE, "order.3", NOW, NOW,
            DelayedTaskStatus.RUNNING, 1, 5, NOW.plusMinutes(5), null);
    final OrderPaymentScope scope = new OrderPaymentScope("tenant", OWNER);

    @BeforeEach
    void setup() {
        when(tasks.find(OrderClosureTasks.TYPE, "order.3")).thenReturn(Optional.of(task));
        when(orders.lockOrder("tenant", "order")).thenReturn(order);
        when(closes.findIntent("tenant", "order")).thenReturn(Optional.of(intent));
        when(operations.isCurrent(scope, order, intent, 3L)).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return true;
        });
    }
    void handle() { TenantUtil.runWithTenant("tenant", () -> service.handle(task)); }

    @Test
    void resumesOnlyTheOriginalIntentAfterCommittingItsLocalInspection() {
        when(operations.resume(scope, intent)).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(1, manager.commits);
            return null;
        });
        handle();
        verify(operations).resume(scope, intent);
        verify(operations, never()).close(any(), any());
        verify(operations, never()).startLocked(any(), any(), any());
    }

    @Test
    void oldGenerationOrTerminalOrderHasNoAnotherPass() {
        when(operations.isCurrent(scope, order, intent, 3L)).thenReturn(false);
        handle();
        verify(operations, never()).resume(any(), any());
        verify(tasks, never()).register(anyString(), anyString(), any());
    }

    @Test
    void missingOriginalIntentAndWrongTenantCannotManufactureAClose() {
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class,
                () -> TenantUtil.runWithTenant("another-tenant", () -> service.handle(task))).getResultCode());
        when(closes.findIntent("tenant", "order")).thenReturn(Optional.empty());
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, this::handle).getResultCode());
        verifyNoInteractions(operations);
    }
}
