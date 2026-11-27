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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.loadup.acquire.app.port.AcquireWriteConflictException;
import io.github.loadup.acquire.client.error.AcquireResultCodeEnum;
import io.github.loadup.commons.error.CommonException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class OrderCreationTransactionsTest {
    final Manager manager = new Manager();
    final OrderCreationTransactions transactions = new OrderCreationTransactions(manager);

    @Test
    void readSnapshotHasAnExplicitReadonlyRepeatableReadBoundaryAndRestoresTenant() {
        assertTrue(transactions.readSnapshot(() -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ, manager.isolation);
            assertThrows(CommonException.class, () -> transactions.writeLocal(() -> true));
            return true;
        }));
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(1, manager.commits);
    }

    @Test
    void guardedDoesNotCreateATransactionAroundRemoteIo() {
        transactions.guarded(() -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return true;
        });
        assertEquals(0, manager.commits);
    }

    @Test
    void snapshotFailureRestoresContextAndDoesNotRetryTheCallback() {
        var failure = new IllegalStateException("Synthetic snapshot failure");
        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () -> transactions.readSnapshot(() -> {
                            throw failure;
                        })));
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(1, manager.rollbacks);
    }

    @Test
    void localWriteRetriesOnlyAfterRollbackAndRetainsTenantAcrossFreshReadCommittedAttempts() {
        var calls = new AtomicInteger();
        assertEquals("committed", transactions.writeLocal(() -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, manager.isolation);
            int attempt = calls.incrementAndGet();
            assertEquals(attempt - 1, manager.rollbacks);
            if (attempt < 3) {
                throw new AcquireWriteConflictException("Synthetic local race");
            }
            return "committed";
        }));
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(3, calls.get());
        assertEquals(3, manager.begins);
        assertEquals(2, manager.rollbacks);
        assertEquals(1, manager.commits);
    }

    @Test
    void unexpectedUniqueViolationIsNotRetried() {
        var calls = new AtomicInteger();
        var failure = new DuplicateKeyException("Unexpected primary-key collision");
        assertSame(
                failure,
                assertThrows(
                        DuplicateKeyException.class,
                        () -> transactions.writeLocal(() -> {
                            calls.incrementAndGet();
                            throw failure;
                        })));
        assertEquals(1, calls.get());
        assertEquals(1, manager.rollbacks);
    }

    @Test
    void transientFailureIsBoundedAndBusinessFailureIsNotRetried() {
        var calls = new AtomicInteger();
        var exhausted = assertThrows(
                CommonException.class,
                () -> transactions.writeLocal(() -> {
                    calls.incrementAndGet();
                    throw new ConcurrencyFailureException("Synthetic local contention");
                }));
        assertEquals(AcquireResultCodeEnum.CONCURRENT_UPDATE, exhausted.getResultCode());
        assertEquals(3, calls.get());
        assertEquals(3, manager.rollbacks);
        assertEquals(0, manager.commits);
        var original = new CommonException(AcquireResultCodeEnum.INVALID_ARGUMENT);
        assertSame(
                original,
                assertThrows(
                        CommonException.class,
                        () -> transactions.writeLocal(() -> {
                            calls.incrementAndGet();
                            throw original;
                        })));
        assertEquals(4, calls.get());
        assertEquals(4, manager.rollbacks);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test
    void contextScopeCanInheritTheCallersTransactionButCannotCreateANestedLocalWriteOrSnapshot() {
        transactions.writeLocal(() -> {
            assertTrue(transactions.guarded(() -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                return true;
            }));
            assertEquals(
                    AcquireResultCodeEnum.INVALID_STATE,
                    assertThrows(CommonException.class, () -> transactions.writeLocal(() -> true))
                            .getResultCode());
            assertEquals(
                    AcquireResultCodeEnum.INVALID_STATE,
                    assertThrows(CommonException.class, () -> transactions.readSnapshot(() -> true))
                            .getResultCode());
            return true;
        });
        assertEquals(1, manager.begins);
        assertEquals(1, manager.commits);
    }

    static final class Manager extends AbstractPlatformTransactionManager {
        int isolation;
        int commits;
        int rollbacks;
        int begins;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            isolation = definition.getIsolationLevel();
            begins++;
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }
}
