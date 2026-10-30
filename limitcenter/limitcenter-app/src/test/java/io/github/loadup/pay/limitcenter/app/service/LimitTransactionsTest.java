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
package io.github.loadup.pay.limitcenter.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.limitcenter.app.security.LimitActor;
import io.github.loadup.pay.limitcenter.domain.gateway.LimitWriteRaceException;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.EXCEEDED;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.limitcenter.client.error.LimitCenterResultCodeEnum.INVALID_ARGUMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimitTransactionsTest {
    final Manager manager = new Manager();
    final jakarta.validation.ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final LimitActor actor = new LimitActor("tenant", "payment", Set.of("pay:limit:reserve"));
    final LimitTransactions transactions = new LimitTransactions(() -> actor, validation.getValidator(), manager);

    @AfterEach
    void close() {
        validation.close();
    }

    @Test
    void contextScopeDoesNotStartATransactionAndRestoresTenantOnFailure() {
        var original = new IllegalStateException("Synthetic scope failure");
        TenantUtil.callWithTenant("outer", () -> {
            assertSame(original, assertThrows(IllegalStateException.class, () -> transactions.withTenantContext(actor, () -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                assertEquals("tenant", TenantUtil.getTenantId());
                throw original;
            })));
            assertEquals("outer", TenantUtil.getTenantId());
            return true;
        });
        assertEquals(0, manager.begins);
    }

    @Test
    void localReservationRetriesAfterRollbackWithTheOriginalActorAndConflictPolicy() {
        var calls = new AtomicInteger();
        assertEquals("reserved", transactions.writeLocal(actor, IDEMPOTENCY_CONFLICT, () -> {
            assertEquals("tenant", TenantUtil.getTenantId());
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, manager.isolation);
            int attempt = calls.incrementAndGet();
            assertEquals(attempt - 1, manager.rollbacks);
            if (attempt < 3) {
                throw new LimitWriteRaceException("Synthetic reservation race");
            }
            return "reserved";
        }));
        assertEquals(3, calls.get());
        assertEquals(2, manager.rollbacks);
        assertEquals(1, manager.commits);
        var exhausted = assertThrows(CommonException.class, () -> transactions.writeLocal(actor, IDEMPOTENCY_CONFLICT, () -> {
            throw new org.springframework.dao.ConcurrencyFailureException("Synthetic repeated reservation contention");
        }));
        assertEquals(CONCURRENT_UPDATE, exhausted.getResultCode());
        assertEquals(6, manager.begins);
        assertEquals(5, manager.rollbacks);
    }

    @Test
    void domainFailuresKeepTheirModuleMappingAndNeverTriggerDatabaseRetry() {
        var conflict = assertThrows(CommonException.class, () -> transactions.writeLocal(actor, INVALID_ARGUMENT, () -> {
            throw new DomainViolation(DomainViolation.Reason.CONFLICT);
        }));
        assertEquals(INVALID_ARGUMENT, conflict.getResultCode());
        var exceeded = assertThrows(CommonException.class, () -> transactions.writeLocal(actor, IDEMPOTENCY_CONFLICT, () -> {
            throw new DomainViolation(DomainViolation.Reason.AMOUNT_EXCEEDED);
        }));
        assertEquals(EXCEEDED, exceeded.getResultCode());
        assertEquals(2, manager.begins);
        assertEquals(2, manager.rollbacks);
        transactions.writeLocal(actor, IDEMPOTENCY_CONFLICT, () -> {
            assertTrue(transactions.withTenantContext(actor, () -> TransactionSynchronizationManager.isActualTransactionActive()));
            assertEquals(CONCURRENT_UPDATE, assertThrows(CommonException.class,
                    () -> transactions.writeLocal(actor, IDEMPOTENCY_CONFLICT, () -> true)).getResultCode());
            return true;
        });
        assertEquals(3, manager.begins);
        assertEquals(1, manager.commits);
        assertNull(TenantUtil.getTenantId());
    }

    @Test
    void unexpectedUniqueConstraintFailureIsNotRetried() {
        var failure = new org.springframework.dao.DuplicateKeyException("unexpected constraint");
        var calls = new AtomicInteger();
        assertSame(failure, assertThrows(org.springframework.dao.DuplicateKeyException.class,
                () -> transactions.writeLocal(actor, IDEMPOTENCY_CONFLICT, () -> {
                    calls.incrementAndGet();
                    throw failure;
                })));
        assertEquals(1, calls.get());
        assertEquals(1, manager.rollbacks);
    }

    static final class Manager extends AbstractPlatformTransactionManager {
        int isolation, begins, commits, rollbacks;

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
