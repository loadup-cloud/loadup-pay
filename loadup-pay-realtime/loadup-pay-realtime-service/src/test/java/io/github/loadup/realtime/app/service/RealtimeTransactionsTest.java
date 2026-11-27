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
package io.github.loadup.realtime.app.service;

import static io.github.loadup.realtime.client.error.RealtimeResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.realtime.client.error.RealtimeResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.realtime.client.error.RealtimeResultCodeEnum.INVALID_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.realtime.domain.gateway.ProjectionWriteRaceException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RealtimeTransactionsTest {
    final Manager manager = new Manager();
    final RealtimeTransactions transactions = new RealtimeTransactions(manager);

    @Test
    void readSnapshotCreatesAReadonlyRepeatableReadViewAndRestoresTheTenant() {
        assertEquals("snapshot", transactions.readSnapshot(() -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ, manager.isolation);
            assertEquals(
                    INVALID_STATE,
                    assertThrows(CommonException.class, () -> transactions.writeLocal(() -> true))
                            .getResultCode());
            assertEquals(
                    INVALID_STATE,
                    assertThrows(CommonException.class, () -> transactions.readSnapshot(() -> true))
                            .getResultCode());
            return "snapshot";
        }));
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(1, manager.begins);
        assertEquals(1, manager.commits);
    }

    @Test
    void readFailureIsNotRetriedAndNullResultRetainsTheExistingRealtimeContract() {
        var original = new DuplicateKeyException("Synthetic snapshot failure");
        assertSame(
                original,
                assertThrows(
                        DuplicateKeyException.class,
                        () -> transactions.readSnapshot(() -> {
                            throw original;
                        })));
        assertEquals(1, manager.begins);
        assertEquals(1, manager.rollbacks);
        assertNull(transactions.readSnapshot(() -> null));
        assertNull(transactions.writeLocal(() -> null));
        assertEquals(2, manager.commits);
    }

    @Test
    void localWritesRetryAfterRollbackButNeverRetryBusinessFailure() {
        var calls = new AtomicInteger();
        assertEquals("committed", transactions.writeLocal(() -> {
            assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, manager.isolation);
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            int attempt = calls.incrementAndGet();
            assertEquals(attempt - 1, manager.rollbacks);
            if (attempt < 3) {
                throw new ConcurrencyFailureException("Synthetic local contention");
            }
            return "committed";
        }));
        assertEquals(3, manager.begins);
        assertEquals(2, manager.rollbacks);
        assertEquals(1, manager.commits);
        var original = new CommonException(INVALID_ARGUMENT);
        assertSame(
                original,
                assertThrows(
                        CommonException.class,
                        () -> transactions.writeLocal(() -> {
                            throw original;
                        })));
        assertEquals(4, manager.begins);
        assertEquals(3, manager.rollbacks);
        assertEquals(1, manager.commits);
    }

    @Test
    void exhaustedLocalContentionHasTheExistingRealtimeErrorCodeAndThreeAttempts() {
        var exhausted = assertThrows(
                CommonException.class,
                () -> transactions.writeLocal(() -> {
                    throw new ProjectionWriteRaceException("Synthetic repeated local race");
                }));
        assertEquals(CONCURRENT_UPDATE, exhausted.getResultCode());
        assertEquals(3, manager.begins);
        assertEquals(3, manager.rollbacks);
        assertEquals(0, manager.commits);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test
    void unexpectedUniqueConstraintFailureIsNotRetried() {
        var original = new DuplicateKeyException("unexpected unique constraint");
        var calls = new AtomicInteger();
        assertSame(
                original,
                assertThrows(
                        DuplicateKeyException.class,
                        () -> transactions.writeLocal(() -> {
                            calls.incrementAndGet();
                            throw original;
                        })));
        assertEquals(1, calls.get());
        assertEquals(1, manager.rollbacks);
    }

    static final class Manager extends AbstractPlatformTransactionManager {
        int isolation;
        int begins;
        int commits;
        int rollbacks;

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
