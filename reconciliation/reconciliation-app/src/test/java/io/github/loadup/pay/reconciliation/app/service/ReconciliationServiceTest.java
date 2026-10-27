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
package io.github.loadup.pay.reconciliation.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.delayedtask.*;
import io.github.loadup.pay.reconciliation.app.converter.ReconciliationConverter;
import io.github.loadup.pay.reconciliation.app.port.*;
import io.github.loadup.pay.reconciliation.app.security.*;
import io.github.loadup.pay.reconciliation.client.command.*;
import io.github.loadup.pay.reconciliation.domain.gateway.*;
import io.github.loadup.pay.reconciliation.domain.model.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.*;

class ReconciliationServiceTest {
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 27, 12, 0);
    private final Clock clock = Clock.fixed(AT.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
    private final varValidation validation = new varValidation();
    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ReconciliationConverter.class.getPackageName());
    private final ReconciliationConverter converter = context.getBean(ReconciliationConverter.class);
    private final ReconciliationBatchGateway batches = mock(ReconciliationBatchGateway.class);
    private final ReconciliationInputGateway inputs = mock(ReconciliationInputGateway.class);
    private final ReconciliationResultGateway results = mock(ReconciliationResultGateway.class);
    private final ReconciliationAccountDirectory accounts = mock(ReconciliationAccountDirectory.class);
    private final ReconciliationDifferenceReader differences = mock(ReconciliationDifferenceReader.class);
    private final DelayedTaskTemplate tasks = mock(DelayedTaskTemplate.class);
    private final AtomicInteger ids = new AtomicInteger();
    private final AtomicReference<StoredReconciliationBatch> stored = new AtomicReference<>();
    private final Tx manager = new Tx();
    private final ReconciliationTransactions transactions = new ReconciliationTransactions(manager);
    private final ReconciliationScope scope = new ReconciliationScope(Channel.WECHAT, "a".repeat(64), LocalDate.of(2026, 10, 26), "wechat-trade-v1");
    private final ReconciliationActor actor = new ReconciliationActor("tenant", "ops", Set.of("pay:reconciliation:batch:create", "pay:reconciliation:batch:attach", "pay:reconciliation:batch:start", "pay:reconciliation:batch:read"));
    private final ReconciliationService service = new ReconciliationService(() -> actor, accounts, batches, inputs, differences,
            transactions, converter, tasks, () -> "id-" + ids.incrementAndGet(), validation.factory.getValidator(), clock);
    private final ReconciliationBatchCreateCommand create = new ReconciliationBatchCreateCommand("request", "account", scope.billDate());

    @BeforeEach void setup() {
        when(accounts.resolve("tenant", "ops", "account", scope.billDate())).thenReturn(scope);
        when(batches.findRequest("tenant", "ops", "request")).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(batches.find(eq("tenant"), anyString())).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(batches.lock(eq("tenant"), anyString())).thenAnswer(call -> { assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); return Optional.ofNullable(stored.get()); });
        when(batches.findRun(eq("tenant"), anyString())).thenAnswer(call -> Optional.ofNullable(stored.get()));
        doAnswer(call -> { stored.set(call.getArgument(1)); return null; }).when(batches).insert(eq("tenant"), any());
        doAnswer(call -> { stored.set(call.getArgument(1)); return null; }).when(batches).update(eq("tenant"), any(), anyLong());
    }
    @AfterEach void close() { context.close(); validation.factory.close(); TransactionSynchronizationManager.clear(); }

    @Test void replayUsesOriginalFrozenScopeWithoutResolvingANewProfile() {
        var first = service.create(create);
        clearInvocations(accounts, batches);
        assertEquals(first, service.create(create));
        verify(accounts, never()).resolve(anyString(), anyString(), anyString(), any());
        verify(batches, never()).insert(anyString(), any());
        assertEquals(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service.create(
                new ReconciliationBatchCreateCommand("request", "another-account", scope.billDate()))).getResultCode());
    }
    @Test void cannotStartUntilBothOriginalInputsExistAndTaskIsRegisteredOnlyOnce() {
        service.create(create);
        assertEquals(INPUT_NOT_SEALED, assertThrows(CommonException.class, () -> service.start(new IdQuery(stored.get().batch().id()))).getResultCode());
        ready();
        doAnswer(call -> { assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly()); assertEquals(ReconciliationState.RUNNING, stored.get().batch().state()); return null;
        }).when(tasks).register(eq(ReconciliationExecutionService.TYPE), anyString(), eq(AT));
        var running = service.start(new IdQuery(stored.get().batch().id()));
        assertEquals(running, service.start(new IdQuery(running.batchId())));
        verify(tasks, times(1)).register(eq(ReconciliationExecutionService.TYPE), eq(running.runId()), eq(AT));
    }
    @Test void durableTaskLoadsImmutableInputsThenComparesOutsideTransactionAndCommitsOriginalResultOnce() {
        service.create(create); ready(); var running = service.start(new IdQuery(stored.get().batch().id()));
        var task = task(running.runId(), 1);
        when(tasks.find(ReconciliationExecutionService.TYPE, running.runId())).thenReturn(Optional.of(task));
        var matcher = spy(new ReconciliationMatcher());
        doAnswer(call -> { assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return call.callRealMethod(); }).when(matcher).compare(any(), any());
        var execution = new ReconciliationExecutionService(tasks, batches, inputs, results, transactions, converter, matcher, clock);
        doAnswer(call -> { assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(ReconciliationState.RUNNING, stored.get().batch().state()); return null;
        }).when(results).insert(eq("tenant"), eq(running.batchId()), eq(running.runId()), any());
        TenantUtil.runWithTenant("tenant", () -> execution.handle(task));
        assertEquals(ReconciliationState.MATCHED, stored.get().batch().state());
        assertNotNull(stored.get().batch().result().resultHash());
        TenantUtil.runWithTenant("tenant", () -> execution.handle(task));
        verify(results, times(1)).insert(eq("tenant"), eq(running.batchId()), eq(running.runId()), any());
        verify(matcher, times(1)).compare(any(), any());
    }
    @Test void staleTaskSnapshotIsRejectedAndFailedResultPersistenceCannotMarkBatchCompleted() {
        service.create(create); ready(); var running = service.start(new IdQuery(stored.get().batch().id()));
        var task = task(running.runId(), 1);
        when(tasks.find(ReconciliationExecutionService.TYPE, running.runId())).thenReturn(Optional.of(task(running.runId(), 2)));
        var execution = new ReconciliationExecutionService(tasks, batches, inputs, results, transactions, converter, new ReconciliationMatcher(), clock);
        assertEquals(INVALID_STATE, assertThrows(CommonException.class, () -> TenantUtil.runWithTenant("tenant", () -> execution.handle(task))).getResultCode());
        verifyNoInteractions(results);
        when(tasks.find(ReconciliationExecutionService.TYPE, running.runId())).thenReturn(Optional.of(task));
        doThrow(new CommonException(DEPENDENCY_UNAVAILABLE)).when(results).insert(anyString(), anyString(), anyString(), any());
        int before = manager.rollbacks;
        assertThrows(CommonException.class, () -> TenantUtil.runWithTenant("tenant", () -> execution.handle(task)));
        assertEquals(before + 1, manager.rollbacks);
        assertEquals(ReconciliationState.RUNNING, stored.get().batch().state());
        // These mocks prove ordering and transaction propagation, never real SQL rollback of inserted differences.
    }
    private void ready() {
        for (var side : ReconciliationSide.values()) {
            var line = new ReconciliationLine(new ReconciliationKey(ReconciliationRecordKind.PAYMENT, "transaction", null), "payment-request", Money.ofMinor(1000, "CNY"), ReconciliationEntryStatus.SUCCEEDED, 1);
            var rows = List.of(line);
            var manifest = new ReconciliationManifest(side, scope, side.name(), "b".repeat(64), ReconciliationDataset.normalizedHash(scope, side, "parser-v1", rows), side.name() + "-capture", "parser-v1", ReconciliationTotals.count(rows));
            var dataset = new ReconciliationDataset(manifest, rows);
            when(inputs.sealedManifest("tenant", manifest.artifactId())).thenReturn(Optional.of(manifest));
            when(inputs.loadSealed("tenant", manifest.artifactId())).thenAnswer(call -> { assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly()); return dataset; });
            service.attach(new ReconciliationInputAttachCommand(stored.get().batch().id(),
                    io.github.loadup.pay.reconciliation.client.enums.ReconciliationSide.valueOf(side.name()), manifest.artifactId()));
        }
    }
    private DelayedTask task(String run, int attempt) { return new DelayedTask("task", "tenant", ReconciliationExecutionService.TYPE, run, AT, AT, DelayedTaskStatus.RUNNING, attempt, 5, AT.plusMinutes(1), null); }
    static final class Tx extends AbstractPlatformTransactionManager {
        int commits, rollbacks;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object value, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
    private static final class varValidation { final jakarta.validation.ValidatorFactory factory = Validation.buildDefaultValidatorFactory(); }
}
