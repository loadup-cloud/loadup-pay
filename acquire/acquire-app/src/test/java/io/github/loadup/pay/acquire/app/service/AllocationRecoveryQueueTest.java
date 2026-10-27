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
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import io.github.loadup.pay.acquire.domain.gateway.AllocationRecoveryGateway;
import io.github.loadup.pay.acquire.domain.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.*;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AllocationRecoveryQueueTest {
    final AllocationRecoveryGateway cursors = mock(AllocationRecoveryGateway.class);
    final DelayedTaskTemplate tasks = mock(DelayedTaskTemplate.class);
    final AllocationRecoveryQueue queue = new AllocationRecoveryQueue(cursors, tasks, 30);
    final UnitTransactions manager = new UnitTransactions();
    final OrderCreationTransactions transactions = new OrderCreationTransactions(manager);
    final StoredPaymentAllocation allocation = new StoredPaymentAllocation(new OrderPaymentAllocation("allocation", "order", "payment",
            "merchant/" + OWNER, "action", "e".repeat(64), Money.ofMinor(1000L, "CNY"), AllocationState.HELD, 0L),
            OWNER, "original-command", "f".repeat(64), 0L);

    @Test
    void firstWakeupAndCursorJoinTheOriginalA2TransactionAtMicrosecondPrecision() {
        var due = NOW.plusSeconds(30);
        var expected = AllocationRecoveryCursor.init("payment", "order", due);
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("tenant", TenantUtil.getTenantId());
            return null;
        }).when(tasks).register(AllocationRecoveryQueue.TYPE, "payment.0", due);
        transactions.writeLocal("tenant", () -> {
            queue.register("tenant", allocation, NOW.plusNanos(123));
            return true;
        });
        var sequence = inOrder(cursors, tasks);
        sequence.verify(cursors).insert("tenant", expected);
        sequence.verify(tasks).register(AllocationRecoveryQueue.TYPE, "payment.0", due);
        assertEquals(1, manager.commits);
    }

    @Test
    void enqueueFailureRollsBackTheTransactionInsteadOfLeavingAnUnscheduledIntent() {
        var failure = new CommonException(DEPENDENCY_UNAVAILABLE);
        doThrow(failure).when(tasks).register(anyString(), anyString(), any());
        assertSame(failure, assertThrows(CommonException.class, () -> transactions.writeLocal("tenant", () -> {
            queue.register("tenant", allocation, NOW);
            return true;
        })));
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
    }

    @Test
    void nativeRunningTasksAreNeverRescheduledAndEachSuccessorHasANewBusinessKey() {
        var before = AllocationRecoveryCursor.init("payment", "order", NOW);
        var after = before.schedule(NOW.plusSeconds(30), 0L);
        transactions.writeLocal("tenant", () -> {
            queue.schedule("tenant", before, NOW.minusSeconds(1));
            return true;
        });
        var sequence = inOrder(cursors, tasks);
        sequence.verify(cursors).update("tenant", before, after);
        sequence.verify(tasks).register(AllocationRecoveryQueue.TYPE, "payment.1", after.dueAt());
        sequence.verifyNoMoreInteractions();
    }

    @Test
    void registrationRejectsMissingTransactionWrongTenantAndNonFreshAllocations() {
        assertSame(INVALID_STATE, assertThrows(CommonException.class,
                () -> TenantUtil.runWithTenant("tenant", () -> queue.register("tenant", allocation, NOW))).getResultCode());
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class,
                () -> transactions.writeLocal("other-tenant", () -> { queue.register("tenant", allocation, NOW); return true; })).getResultCode());
        var amended = new StoredPaymentAllocation(allocation.allocation(), OWNER, allocation.intentPayload(), allocation.payloadHash(), 1L);
        assertSame(INVALID_STATE, assertThrows(CommonException.class,
                () -> transactions.writeLocal("tenant", () -> { queue.register("tenant", amended, NOW); return true; })).getResultCode());
        verifyNoInteractions(cursors, tasks);
    }
}
