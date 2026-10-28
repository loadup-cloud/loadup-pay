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
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderCreationTransactionsTest {
    final Manager manager = new Manager();
    final OrderCreationTransactions transactions = new OrderCreationTransactions(manager);

    @Test
    void readSnapshotHasAnExplicitReadonlyRepeatableReadBoundaryAndRestoresTenant() {
        TenantUtil.callWithTenant("outer", () -> {
            assertTrue(transactions.readSnapshot("inner", () -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ, manager.isolation);
                assertEquals("inner", TenantUtil.getTenantId());
                assertThrows(CommonException.class, () -> transactions.writeLocal("inner", () -> true));
                return true;
            }));
            assertEquals("outer", TenantUtil.getTenantId());
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return true;
        });
        assertEquals(1, manager.commits);
    }

    @Test
    void withTenantContextDoesNotCreateATransactionAroundRemoteIo() {
        transactions.withTenantContext("tenant", () -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("tenant", TenantUtil.getTenantId());
            return true;
        });
        assertEquals(0, manager.commits);
    }

    @Test
    void snapshotFailureRestoresContextAndDoesNotRetryTheCallback() {
        var failure = new IllegalStateException("Synthetic snapshot failure");
        TenantUtil.callWithTenant("outer", () -> {
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> transactions.readSnapshot("inner", () -> {
                        throw failure;
                    })));
            assertEquals("outer", TenantUtil.getTenantId());
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return true;
        });
        assertEquals(1, manager.rollbacks);
    }

    @Test
    void localWriteRetriesOnlyAfterRollbackAndRetainsTenantAcrossFreshReadCommittedAttempts() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        TenantUtil.callWithTenant("outer", () -> {
            assertEquals("committed", transactions.writeLocal("inner", () -> {
                assertEquals("inner", TenantUtil.getTenantId());
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, manager.isolation);
                int attempt = calls.incrementAndGet();
                assertEquals(attempt - 1, manager.rollbacks);
                if (attempt < 3) throw new org.springframework.dao.DuplicateKeyException("Synthetic local race");
                return "committed";
            }));
            assertEquals("outer", TenantUtil.getTenantId());
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return true;
        });
        assertEquals(3, calls.get());
        assertEquals(3, manager.begins);
        assertEquals(2, manager.rollbacks);
        assertEquals(1, manager.commits);
    }

    @Test
    void transientFailureIsBoundedAndBusinessFailureIsNotRetried() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var exhausted = assertThrows(CommonException.class, () -> transactions.writeLocal("tenant", () -> {
            calls.incrementAndGet();
            throw new org.springframework.dao.ConcurrencyFailureException("Synthetic local contention");
        }));
        assertEquals(io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.CONCURRENT_UPDATE, exhausted.getResultCode());
        assertEquals(3, calls.get());
        assertEquals(3, manager.rollbacks);
        assertEquals(0, manager.commits);
        var original = new CommonException(io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT);
        assertSame(original, assertThrows(CommonException.class, () -> transactions.writeLocal("tenant", () -> {
            calls.incrementAndGet();
            throw original;
        })));
        assertEquals(4, calls.get());
        assertEquals(4, manager.rollbacks);
        assertNull(TenantUtil.getTenantId());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test
    void contextScopeCanInheritTheCallersTransactionButCannotCreateANestedLocalWriteOrSnapshot() {
        transactions.writeLocal("tenant", () -> {
            assertTrue(transactions.withTenantContext("inner", () -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                assertEquals("inner", TenantUtil.getTenantId());
                return true;
            }));
            assertEquals("tenant", TenantUtil.getTenantId());
            assertEquals(io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE, assertThrows(CommonException.class,
                    () -> transactions.writeLocal("tenant", () -> true)).getResultCode());
            assertEquals(io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE, assertThrows(CommonException.class,
                    () -> transactions.readSnapshot("tenant", () -> true)).getResultCode());
            return true;
        });
        assertEquals(1, manager.begins);
        assertEquals(1, manager.commits);
    }

    static final class Manager extends AbstractPlatformTransactionManager {
        int isolation, commits, rollbacks, begins;

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
