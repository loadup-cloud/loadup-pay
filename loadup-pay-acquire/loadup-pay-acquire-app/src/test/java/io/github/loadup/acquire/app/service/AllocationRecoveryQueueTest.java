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
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.loadup.acquire.domain.gateway.AllocationRecoveryGateway;
import io.github.loadup.acquire.domain.model.AllocationRecoveryCursor;
import io.github.loadup.acquire.domain.model.AllocationState;
import io.github.loadup.acquire.domain.model.OrderPaymentAllocation;
import io.github.loadup.acquire.domain.model.StoredPaymentAllocation;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.money.Money;
import io.github.loadup.components.task.TaskSubmission;
import io.github.loadup.components.task.TaskTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AllocationRecoveryQueueTest {
    final AllocationRecoveryGateway cursors = mock(AllocationRecoveryGateway.class);
    final TaskTemplate tasks = mock(TaskTemplate.class);
    final AllocationRecoveryQueue queue = new AllocationRecoveryQueue(cursors, tasks, 30);
    final UnitTransactions manager = new UnitTransactions();
    final OrderCreationTransactions transactions = new OrderCreationTransactions(manager);
    final StoredPaymentAllocation allocation = new StoredPaymentAllocation(
            new OrderPaymentAllocation(
                    "allocation",
                    "order",
                    "payment",
                    "merchant/" + OWNER,
                    "action",
                    "e".repeat(64),
                    Money.ofMinor(1000L, "CNY"),
                    AllocationState.HELD,
                    0L),
            OWNER,
            "original-command",
            "f".repeat(64),
            0L);

    @Test
    void firstWakeupAndCursorJoinTheOriginalA2TransactionAtMicrosecondPrecision() {
        var due = NOW.plusSeconds(30);
        var expected = AllocationRecoveryCursor.init("payment", "order", due);
        doAnswer(c -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    return null;
                })
                .when(tasks)
                .submit(ArgumentMatchers.eq(TaskSubmission.scheduled(AllocationRecoveryQueue.TYPE, "payment.0", due)));
        transactions.writeLocal(() -> {
            queue.register(allocation, NOW.plusNanos(123));
            return true;
        });
        var sequence = inOrder(cursors, tasks);
        sequence.verify(cursors).insert(expected);
        sequence.verify(tasks)
                .submit(ArgumentMatchers.eq(TaskSubmission.scheduled(AllocationRecoveryQueue.TYPE, "payment.0", due)));
        assertEquals(1, manager.commits);
    }

    @Test
    void enqueueFailureRollsBackTheTransactionInsteadOfLeavingAnUnscheduledIntent() {
        var failure = new CommonException(DEPENDENCY_UNAVAILABLE);
        doThrow(failure).when(tasks).submit(ArgumentMatchers.any(TaskSubmission.class));
        assertSame(
                failure,
                assertThrows(
                        CommonException.class,
                        () -> transactions.writeLocal(() -> {
                            queue.register(allocation, NOW);
                            return true;
                        })));
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
    }

    @Test
    void nativeRunningTasksAreNeverRescheduledAndEachSuccessorHasANewBusinessKey() {
        var before = AllocationRecoveryCursor.init("payment", "order", NOW);
        var after = before.schedule(NOW.plusSeconds(30), 0L);
        transactions.writeLocal(() -> {
            queue.schedule(before, NOW.minusSeconds(1));
            return true;
        });
        var sequence = inOrder(cursors, tasks);
        sequence.verify(cursors).update(before, after);
        sequence.verify(tasks)
                .submit(ArgumentMatchers.eq(
                        TaskSubmission.scheduled(AllocationRecoveryQueue.TYPE, "payment.1", after.dueAt())));
        sequence.verifyNoMoreInteractions();
    }

    @Test
    void registrationRejectsMissingTransactionAndNonFreshAllocations() {
        assertSame(
                INVALID_STATE,
                assertThrows(CommonException.class, () -> queue.register(allocation, NOW))
                        .getResultCode());
        var amended = new StoredPaymentAllocation(
                allocation.allocation(), OWNER, allocation.intentPayload(), allocation.payloadHash(), 1L);
        System.err.println("AMENDED rowVersion=" + amended.rowVersion() + " state="
                + amended.allocation().state() + " srcVersion="
                + amended.allocation().paymentSourceVersion());
        assertSame(
                INVALID_STATE,
                assertThrows(
                                CommonException.class,
                                () -> transactions.writeLocal(() -> {
                                    queue.register(amended, NOW);
                                    return true;
                                }))
                        .getResultCode());
        verifyNoInteractions(cursors, tasks);
    }
}
