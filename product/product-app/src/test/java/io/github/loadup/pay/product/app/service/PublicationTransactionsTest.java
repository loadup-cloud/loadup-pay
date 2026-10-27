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
package io.github.loadup.pay.product.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.util.TenantUtil;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.concurrent.atomic.AtomicInteger;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;

class PublicationTransactionsTest {
    final Manager manager = new Manager();
    final PublicationTransactions transactions = new PublicationTransactions(manager);

    @Test void contextScopeDoesNotOpenATransactionAndRestoresOuterTenantAfterFailure() {
        var original = new IllegalStateException("Synthetic context failure");
        TenantUtil.callWithTenant("outer", () -> {
            assertSame(original, assertThrows(IllegalStateException.class, () -> transactions.withTenantContext("inner", () -> {
                assertEquals("inner", TenantUtil.getTenantId());
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                throw original;
            })));
            assertEquals("outer", TenantUtil.getTenantId());
            return true;
        });
        assertEquals(0, manager.begins);
    }

    @Test void localPublicationWritesRetryAfterRollbackUsingReadCommittedAndKeepTheConflictMapping() {
        var calls = new AtomicInteger();
        assertEquals("published", transactions.writeLocal("tenant", () -> {
            assertEquals("tenant", TenantUtil.getTenantId());
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, manager.isolation);
            int attempt = calls.incrementAndGet();
            assertEquals(attempt - 1, manager.rollbacks);
            if (attempt < 3) throw new org.springframework.dao.DuplicateKeyException("Synthetic publication race");
            return "published";
        }));
        assertEquals(3, calls.get()); assertEquals(2, manager.rollbacks); assertEquals(1, manager.commits);
        var exhausted = assertThrows(CommonException.class, () -> transactions.writeLocal("tenant", () -> {
            throw new org.springframework.dao.ConcurrencyFailureException("Synthetic repeated publication contention");
        }));
        assertEquals(POLICY_PUBLICATION_CONFLICT, exhausted.getResultCode());
        assertEquals(6, manager.begins); assertEquals(5, manager.rollbacks);
    }

    @Test void businessConflictsAreNotRetriedAndNestedLocalWritesAreRejected() {
        var conflict = assertThrows(CommonException.class, () -> transactions.writeLocal("tenant", () -> {
            throw new DomainViolation(DomainViolation.Reason.CONFLICT);
        }));
        assertEquals(IDEMPOTENCY_CONFLICT, conflict.getResultCode());
        assertEquals(1, manager.begins); assertEquals(1, manager.rollbacks);
        transactions.writeLocal("tenant", () -> {
            assertTrue(transactions.withTenantContext("inner", () -> TransactionSynchronizationManager.isActualTransactionActive()));
            assertEquals("tenant", TenantUtil.getTenantId());
            assertEquals(INVALID_STATE, assertThrows(CommonException.class,
                    () -> transactions.writeLocal("tenant", () -> true)).getResultCode());
            return true;
        });
        assertEquals(2, manager.begins); assertEquals(1, manager.commits);
        assertNull(TenantUtil.getTenantId());
    }
    static final class Manager extends AbstractPlatformTransactionManager {
        int isolation, begins, commits, rollbacks;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { isolation = definition.getIsolationLevel(); begins++; }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
