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
package io.github.loadup.pay.reconciliation.infrastructure.repository;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.delayedtask.DelayedTaskDispatcher;
import io.github.loadup.components.delayedtask.DelayedTaskProperties;
import io.github.loadup.components.testcontainers.annotation.ContainerType;
import io.github.loadup.components.testcontainers.annotation.EnableTestContainers;
import io.github.loadup.pay.reconciliation.app.service.ReconciliationExecutionService;
import io.github.loadup.pay.reconciliation.app.service.ReconciliationService;
import io.github.loadup.pay.reconciliation.app.service.ReconciliationTransactions;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationBatchCreateCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationBatchRetryCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationBatchStartCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationInputAttachCommand;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationInputGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationDataset;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationEntryStatus;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationKey;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationLine;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationManifest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationRecordKind;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationResult;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationSide;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationState;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationTotals;
import io.github.loadup.pay.reconciliation.infrastructure.converter.ReconciliationDifferenceStorageConverter;
import io.github.loadup.pay.reconciliation.infrastructure.converter.ReconciliationStorageConverter;
import io.github.loadup.pay.reconciliation.infrastructure.statement.WechatTradeStatementParser;
import io.github.loadup.pay.reconciliation.infrastructure.task.ReconciliationTaskHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PostgreSQL proof of rollback, exhausted-task recovery and concurrent retry fencing.
 */
@SpringBootTest(classes = {ReconciliationImportPersistenceIT.Application.class, ReconciliationComparisonRetryPersistenceIT.Config.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnableTestContainers(value = ContainerType.POSTGRESQL, reuse = false)
class ReconciliationComparisonRetryPersistenceIT {
    private final ReconciliationService batches;
    private final ReconciliationImportPersistenceIT.Actors actors;
    private final ReconciliationInputGateway inputs;
    private final ReconciliationTransactions transactions;
    private final DelayedTaskDispatcher dispatcher;
    private final DelayedTaskProperties properties;
    private final FaultingResults results;
    private final JdbcTemplate jdbc;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Autowired
    ReconciliationComparisonRetryPersistenceIT(ReconciliationService batches, ReconciliationImportPersistenceIT.Actors actors,
                                               ReconciliationInputGateway inputs, ReconciliationTransactions transactions,
                                               DelayedTaskDispatcher dispatcher, DelayedTaskProperties properties,
                                               FaultingResults results, JdbcTemplate jdbc,
                                               org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.batches = batches;
        this.actors = actors;
        this.inputs = inputs;
        this.transactions = transactions;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.results = results;
        this.jdbc = jdbc;
        this.transactionManager = transactionManager;
    }

    @Test
    void exhaustedOriginalRunCanBeRetriedOnceAndCommitsOneResult() throws Exception {
        assertInstanceOf(org.springframework.jdbc.datasource.DataSourceTransactionManager.class, transactionManager);
        int previousMaxAttempts = properties.getMaxAttempts();
        properties.setMaxAttempts(1);
        try {
            String tenant = "tenant-" + UUID.randomUUID();
            LocalDate date = LocalDate.of(2026, 10, 26);
            var batch = actors.call(tenant, () -> batches.create(new ReconciliationBatchCreateCommand("batch", "account", date)));
            var scope = new ReconciliationScope(Channel.WECHAT, "a".repeat(64), date, WechatTradeStatementParser.PROFILE);
            var channel = dataset(scope, ReconciliationSide.CHANNEL, 100L);
            var ledger = dataset(scope, ReconciliationSide.LEDGER, 90L);
            transactions.writeLocal(tenant, () -> {
                inputs.insertSealed(tenant, channel.manifest().artifactId(), channel);
                inputs.insertSealed(tenant, ledger.manifest().artifactId(), ledger);
                return true;
            });
            actors.call(tenant, () -> batches.attach(new ReconciliationInputAttachCommand(batch.batchId(), ReconciliationSide.CHANNEL, channel.manifest().artifactId())));
            actors.call(tenant, () -> batches.attach(new ReconciliationInputAttachCommand(batch.batchId(), ReconciliationSide.LEDGER, ledger.manifest().artifactId())));
            var started = actors.call(tenant, () -> batches.start(new ReconciliationBatchStartCommand(batch.batchId())));
            results.failOnceFor(batch.batchId());
            dispatcher.execute();
            var failed = actors.call(tenant, () -> batches.detail(new IdQuery(batch.batchId())));
            assertEquals("FAILED", failed.nativeStatus());
            assertEquals(started.runId(), failed.runId());
            assertEquals(0L, differenceCount(tenant, batch.batchId()));
            var command = new ReconciliationBatchRetryCommand(batch.batchId(), failed.runId(), failed.taskId());
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            try (var pool = Executors.newFixedThreadPool(2)) {
                var first = pool.submit(() -> retry(tenant, command, ready, go));
                var second = pool.submit(() -> retry(tenant, command, ready, go));
                assertTrue(ready.await(10, TimeUnit.SECONDS));
                go.countDown();
                assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            }
            var queued = actors.call(tenant, () -> batches.detail(new IdQuery(batch.batchId())));
            assertEquals(failed.taskId(), queued.taskId());
            assertEquals("PENDING", queued.nativeStatus());
            dispatcher.execute();
            var completed = actors.call(tenant, () -> batches.detail(new IdQuery(batch.batchId())));
            assertEquals("SUCCEEDED", completed.nativeStatus());
            assertEquals(ReconciliationState.DIFFERENCES, completed.state());
            assertEquals(started.runId(), completed.runId());
            assertEquals(Long.valueOf(1L), completed.differenceCount());
            assertEquals(1L, differenceCount(tenant, batch.batchId()));
            assertThrows(CommonException.class, () -> actors.call(tenant, () -> batches.retry(command)));
        } finally {
            properties.setMaxAttempts(previousMaxAttempts);
        }
    }

    private boolean retry(String tenant, ReconciliationBatchRetryCommand command, CountDownLatch ready, CountDownLatch go) throws Exception {
        ready.countDown();
        if (!go.await(10, TimeUnit.SECONDS)) throw new AssertionError("Retry barrier timed out");
        try {
            actors.call(tenant, () -> batches.retry(command));
            return true;
        } catch (CommonException rejected) {
            assertEquals(io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.INVALID_STATE, rejected.getResultCode());
            return false;
        }
    }

    private long differenceCount(String tenant, String batchId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM reconciliation_difference WHERE tenant_id = ? AND batch_id = ?",
                Long.class, tenant, batchId);
    }

    private ReconciliationDataset dataset(ReconciliationScope scope, ReconciliationSide side, long amount) {
        String id = UUID.randomUUID().toString();
        var line = new ReconciliationLine(new ReconciliationKey(ReconciliationRecordKind.PAYMENT, "transaction", null),
                "request", Money.ofMinor(amount, "CNY"), ReconciliationEntryStatus.SUCCEEDED, 1);
        var records = List.of(line);
        var manifest = new ReconciliationManifest(side, scope, id, "b".repeat(64),
                ReconciliationDataset.normalizedHash(scope, side, "parser", records), id, "parser", ReconciliationTotals.count(records));
        return new ReconciliationDataset(manifest, records);
    }

    static class FaultingResults extends ReconciliationResultGatewayImpl {
        final AtomicReference<String> failBatch = new AtomicReference<>();

        FaultingResults(IdGenerator ids,
                        org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc, ReconciliationStorageConverter values,
                        ReconciliationDifferenceStorageConverter converter, Clock clock) {
            super(ids, jdbc, values, converter, clock);
        }

        public void failOnceFor(String batchId) {
            failBatch.set(batchId);
        }

        @Override
        public void insert(String tenant, String batchId, String runId, ReconciliationResult result) {
            super.insert(tenant, batchId, runId, result);
            var target = failBatch.get();
            if (batchId.equals(target) && failBatch.compareAndSet(target, null))
                throw new IllegalStateException("Injected after result insertion");
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import({ReconciliationExecutionService.class, ReconciliationTaskHandler.class})
    static class Config {
        @Bean
        @Primary
        FaultingResults faultingResults(IdGenerator ids,
                                        org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc, ReconciliationStorageConverter values,
                                        ReconciliationDifferenceStorageConverter converter, @Qualifier("reconciliationClock") Clock clock) {
            return new FaultingResults(ids, jdbc, values, converter, clock);
        }

        @Bean("delayedTaskClock")
        Clock delayedTaskClock(@Qualifier("reconciliationClock") Clock clock) {
            return clock;
        }
    }
}
