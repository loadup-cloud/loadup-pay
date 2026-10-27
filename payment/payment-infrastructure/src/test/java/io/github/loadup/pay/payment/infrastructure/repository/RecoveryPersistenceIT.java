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
package io.github.loadup.pay.payment.infrastructure.repository;

import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.delayedtask.*;
import io.github.loadup.components.testcontainers.annotation.ContainerType;
import io.github.loadup.components.testcontainers.annotation.EnableTestContainers;
import io.github.loadup.pay.payment.app.service.PaymentAcceptanceTransactions;
import io.github.loadup.pay.payment.app.service.PaymentRecoveryService;
import io.github.loadup.pay.payment.domain.gateway.PaymentRecoveryGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentRecoveryRetryGateway;
import io.github.loadup.pay.payment.domain.model.PaymentRecoveryCursor;
import io.github.loadup.pay.payment.domain.model.PaymentRecoveryKey;
import io.github.loadup.pay.payment.domain.model.PaymentRecoveryRetryReceipt;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentRecoveryStorageConverter;
import io.github.loadup.pay.payment.infrastructure.mapper.PaymentRecoveryCursorDOMapper;
import io.github.loadup.testify.starter.scenario.TestScenario;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/** Real migration, generated Mapper, gateway and native-task boundaries; does not exercise financial submission. */
@SpringBootTest(classes = RecoveryPersistenceIT.Application.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnableTestContainers(value = ContainerType.MYSQL, reuse = false)
class RecoveryPersistenceIT {
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 26, 12, 0);
    private static final String AUDIT_TYPE = "pay.review.native-retry";
    private final PaymentRecoveryGateway cursors;
    private final PaymentRecoveryRetryGateway receipts;
    private final DelayedTaskTemplate tasks;
    private final DelayedTaskDispatcher dispatcher;
    private final PaymentAcceptanceTransactions transactions;
    private final TestScenario scenario;

    @Autowired
    RecoveryPersistenceIT(PaymentRecoveryGateway cursors, PaymentRecoveryRetryGateway receipts, DelayedTaskTemplate tasks,
            DelayedTaskDispatcher dispatcher, PaymentAcceptanceTransactions transactions, TestScenario scenario) {
        this.cursors = cursors; this.receipts = receipts; this.tasks = tasks; this.dispatcher = dispatcher;
        this.transactions = transactions; this.scenario = scenario;
    }

    @Test
    void initialCursorAndNativeRegistrationRollBackTogether() {
        String tenant = id("tenant"), payment = id("payment");
        assertThrows(IllegalStateException.class, () -> transactions.writeLocal(tenant, () -> {
            cursors.insert(tenant, PaymentRecoveryCursor.init(payment, AT));
            tasks.register(PaymentRecoveryService.TYPE, key(payment, 0), AT);
            throw new IllegalStateException("Injected after native registration");
        }));
        transactions.readSnapshot(tenant, () -> {
            assertTrue(cursors.find(tenant, payment).isEmpty());
            assertTrue(tasks.find(PaymentRecoveryService.TYPE, key(payment, 0)).isEmpty());
            return true;
        });
    }

    @Test
    void successorFailureCannotCommitOnlyTheCursorOrOnlyTheNewTask() {
        String tenant = id("tenant"), payment = id("payment");
        seed(tenant, payment);
        assertThrows(IllegalStateException.class, () -> transactions.writeLocal(tenant, () -> {
            var before = cursors.lock(tenant, payment).orElseThrow();
            var after = before.schedule(AT.plusSeconds(30), 0L);
            cursors.update(tenant, before, after);
            tasks.register(PaymentRecoveryService.TYPE, key(payment, 1), after.dueAt());
            throw new IllegalStateException("Injected before successor commit");
        }));
        transactions.readSnapshot(tenant, () -> {
            assertEquals(0L, cursors.find(tenant, payment).orElseThrow().generation());
            assertTrue(tasks.find(PaymentRecoveryService.TYPE, key(payment, 0)).isPresent());
            assertTrue(tasks.find(PaymentRecoveryService.TYPE, key(payment, 1)).isEmpty());
            return true;
        });
    }

    @Test
    void concurrentWorkersCanCommitOnlyOneSuccessorForTheOriginalGeneration() throws Exception {
        String tenant = id("tenant"), payment = id("payment");
        seed(tenant, payment);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            java.util.concurrent.Callable<Boolean> advance = () -> {
                ready.countDown(); await(start);
                return transactions.writeLocal(tenant, () -> {
                    var before = cursors.lock(tenant, payment).orElseThrow();
                    if (before.generation() != 0L) return false;
                    var after = before.schedule(AT.plusSeconds(30), 0L);
                    cursors.update(tenant, before, after);
                    tasks.register(PaymentRecoveryService.TYPE, key(payment, 1), after.dueAt());
                    return true;
                });
            };
            var first = workers.submit(advance);
            var second = workers.submit(advance);
            await(ready); start.countDown();
            assertEquals(1L, Stream.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
                    .filter(Boolean::booleanValue).count());
        } finally { start.countDown(); }
        transactions.readSnapshot(tenant, () -> {
            assertEquals(1L, cursors.find(tenant, payment).orElseThrow().generation());
            assertTrue(tasks.find(PaymentRecoveryService.TYPE, key(payment, 1)).isPresent());
            return true;
        });
        scenario.assertDb("payment_recovery_cursor").where("tenant_id", tenant).where("id", payment).exists();
    }

    @Test
    void readSnapshotDoesNotHoldTheCursorWriteLockAndKeepsItsOriginalMultiTableView() throws Exception {
        String tenant = id("tenant"), payment = id("payment");
        seed(tenant, payment);
        var observed = new CountDownLatch(1);
        var written = new CountDownLatch(1);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var reader = workers.submit(() -> transactions.readSnapshot(tenant, () -> {
                assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                assertEquals(0L, cursors.find(tenant, payment).orElseThrow().generation());
                assertTrue(tasks.find(PaymentRecoveryService.TYPE, key(payment, 0)).isPresent());
                observed.countDown(); await(written);
                assertEquals(0L, cursors.find(tenant, payment).orElseThrow().generation());
                assertTrue(tasks.find(PaymentRecoveryService.TYPE, key(payment, 1)).isEmpty());
                return true;
            }));
            await(observed);
            var writer = workers.submit(() -> {
                try {
                    return transactions.writeLocal(tenant, () -> {
                        var before = cursors.lock(tenant, payment).orElseThrow();
                        var after = before.schedule(AT.plusSeconds(30), 0L);
                        cursors.update(tenant, before, after);
                        tasks.register(PaymentRecoveryService.TYPE, key(payment, 1), after.dueAt());
                        return true;
                    });
                } finally { written.countDown(); }
            });
            try {
                assertTrue(writer.get(5, TimeUnit.SECONDS));
                assertTrue(reader.get(5, TimeUnit.SECONDS));
            } finally { written.countDown(); }
        } finally { written.countDown(); }
        transactions.readSnapshot(tenant, () -> {
            assertEquals(1L, cursors.find(tenant, payment).orElseThrow().generation());
            assertTrue(tasks.find(PaymentRecoveryService.TYPE, key(payment, 1)).isPresent());
            return true;
        });
    }

    @Test
    void failedNativeRetryAndActualAuditGatewayHaveTheSameRollbackBoundary() {
        String tenant = id("tenant"), subject = id("payment"), request = id("operation");
        transactions.writeLocal(tenant, () -> tasks.register(AUDIT_TYPE, key(subject, 0), AT));
        dispatcher.execute();
        var failed = transactions.readSnapshot(tenant, () -> tasks.find(AUDIT_TYPE, key(subject, 0)).orElseThrow());
        assertEquals(DelayedTaskStatus.FAILED, failed.status());
        var receipt = new PaymentRecoveryRetryReceipt(id("receipt"), request, "a".repeat(64), "pay-ops", "b".repeat(64),
                "COLLECTION", subject, 0L, id("order"), failed.id(), failed.attempts(), failed.nextExecuteAt(),
                "Injected rollback proof", "RETRIED", AT);
        assertThrows(IllegalStateException.class, () -> transactions.writeLocal(tenant, () -> {
            tasks.retryFailed(AUDIT_TYPE, key(subject, 0));
            receipts.insert(tenant, receipt);
            throw new IllegalStateException("Injected after actual audit insert");
        }));
        transactions.readSnapshot(tenant, () -> {
            var unchanged = tasks.find(AUDIT_TYPE, key(subject, 0)).orElseThrow();
            assertEquals(DelayedTaskStatus.FAILED, unchanged.status());
            assertEquals(failed.attempts(), unchanged.attempts());
            assertTrue(receipts.find(tenant, receipt.callerScopeHash(), request).isEmpty());
            return true;
        });
        transactions.writeLocal(tenant, () -> {
            tasks.retryFailed(AUDIT_TYPE, key(subject, 0));
            receipts.insert(tenant, receipt);
            return true;
        });
        transactions.readSnapshot(tenant, () -> {
            assertEquals(DelayedTaskStatus.PENDING, tasks.find(AUDIT_TYPE, key(subject, 0)).orElseThrow().status());
            assertEquals(receipt, receipts.find(tenant, receipt.callerScopeHash(), request).orElseThrow());
            return true;
        });
    }

    private void seed(String tenant, String payment) {
        transactions.writeLocal(tenant, () -> {
            cursors.insert(tenant, PaymentRecoveryCursor.init(payment, AT));
            tasks.register(PaymentRecoveryService.TYPE, key(payment, 0), AT);
            return true;
        });
    }
    private static String id(String prefix) { return prefix + "-" + UUID.randomUUID(); }
    private static String key(String subject, long generation) { return new PaymentRecoveryKey(subject, generation).bizId(); }
    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS), "Concurrent database step did not arrive"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = PaymentRecoveryCursorDOMapper.class)
    @ComponentScan(basePackageClasses = PaymentRecoveryStorageConverter.class)
    @Import({PaymentRecoveryGatewayImpl.class, PaymentRecoveryRetryGatewayImpl.class, PaymentAcceptanceTransactions.class})
    static class Application {
        @Bean("delayedTaskClock")
        Clock delayedTaskClock() { return Clock.fixed(AT.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()); }
        @Bean
        DelayedTaskHandler syntheticFailure() {
            return new DelayedTaskHandler() {
                @Override public String taskType() { return AUDIT_TYPE; }
                @Override public void handle(DelayedTask task) { throw new IllegalStateException("Injected native failure"); }
            };
        }
    }
}
