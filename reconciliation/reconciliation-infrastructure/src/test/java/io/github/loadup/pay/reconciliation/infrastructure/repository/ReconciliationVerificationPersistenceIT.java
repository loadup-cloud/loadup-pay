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
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.dfs.DfsService;
import io.github.loadup.components.outbox.JdbcOutboxRepository;
import io.github.loadup.components.outbox.OutboxDispatcher;
import io.github.loadup.components.outbox.OutboxEvent;
import io.github.loadup.components.outbox.OutboxHandler;
import io.github.loadup.components.outbox.OutboxProperties;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.components.testcontainers.annotation.ContainerType;
import io.github.loadup.components.testcontainers.annotation.EnableTestContainers;
import io.github.loadup.pay.payment.client.dto.PaymentVerificationDTO;
import io.github.loadup.pay.payment.client.enums.PaymentVerificationDisposition;
import io.github.loadup.pay.payment.client.enums.PaymentVerificationOutcome;
import io.github.loadup.pay.payment.client.query.PaymentVerificationQuery;
import io.github.loadup.pay.reconciliation.app.converter.ReconciliationReviewConverter;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationAccountDirectory;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationPaymentVerification;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationSourceFiles;
import io.github.loadup.pay.reconciliation.app.security.ReconciliationActor;
import io.github.loadup.pay.reconciliation.app.security.ReconciliationActorProvider;
import io.github.loadup.pay.reconciliation.app.service.ReconciliationReviewService;
import io.github.loadup.pay.reconciliation.app.service.ReconciliationTransactions;
import io.github.loadup.pay.reconciliation.app.service.ReconciliationVerificationDelivery;
import io.github.loadup.pay.reconciliation.app.service.ReconciliationVerificationMessages;
import io.github.loadup.pay.reconciliation.app.service.ReconciliationVerificationService;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationReviewDecideCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationReviewProposeCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationVerificationRetryCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationVerificationSubmitCommand;
import io.github.loadup.pay.reconciliation.client.dto.ReconciliationVerificationDTO;
import io.github.loadup.pay.reconciliation.client.enums.ReconciliationReviewConclusion;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationBatchGateway;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationInputGateway;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationResultGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationBatch;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationDataset;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationEntryStatus;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationFileSnapshot;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationKey;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationLine;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationManifest;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationMatcher;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationRecordKind;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationSide;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementAccount;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationTotals;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationVerificationRequest;
import io.github.loadup.pay.reconciliation.domain.model.StoredReconciliationBatch;
import io.github.loadup.pay.reconciliation.infrastructure.client.FileResourceReconciliationSources;
import io.github.loadup.pay.reconciliation.infrastructure.converter.ReconciliationReviewStorageConverter;
import io.github.loadup.pay.reconciliation.infrastructure.converter.ReconciliationVerificationStorageConverter;
import io.github.loadup.pay.reconciliation.infrastructure.mapper.ReconciliationVerificationDOMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real SQL, native Outbox claim/ACK and application transactions; owner transport and object bytes are controlled.
 */
@SpringBootTest(classes = ReconciliationVerificationPersistenceIT.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"loadup.outbox.worker-enabled=false", "loadup.outbox.max-attempts=1", "loadup.outbox.batch-size=1"})
@EnableTestContainers(value = ContainerType.MYSQL, reuse = false)
@Execution(ExecutionMode.SAME_THREAD)
class ReconciliationVerificationPersistenceIT {
    final ReconciliationVerificationService service;
    final ReconciliationReviewService reviews;
    final ReconciliationBatchGateway batches;
    final ReconciliationInputGateway inputs;
    final ReconciliationResultGateway results;
    final ReconciliationTransactions transactions;
    final ReconciliationSourceFiles files;
    final FaultingVerifications requests;
    final JdbcTemplate jdbc;
    final Actors actors;
    final Payment payment;
    final OutboxTemplate outbox;
    final OutboxDispatcher dispatcher;
    final Handler handler;
    final DataSource dataSource;
    final Set<String> tenants = new HashSet<>();

    @Autowired
    ReconciliationVerificationPersistenceIT(ReconciliationVerificationService service, ReconciliationReviewService reviews,
                                            ReconciliationBatchGateway batches, ReconciliationInputGateway inputs, ReconciliationResultGateway results,
                                            ReconciliationTransactions transactions, ReconciliationSourceFiles files, FaultingVerifications requests,
                                            JdbcTemplate jdbc, Actors actors, Payment payment, OutboxTemplate outbox, OutboxDispatcher dispatcher,
                                            Handler handler, DataSource dataSource) {
        this.service = service;
        this.reviews = reviews;
        this.batches = batches;
        this.inputs = inputs;
        this.results = results;
        this.transactions = transactions;
        this.files = files;
        this.requests = requests;
        this.jdbc = jdbc;
        this.actors = actors;
        this.payment = payment;
        this.outbox = outbox;
        this.dispatcher = dispatcher;
        this.handler = handler;
        this.dataSource = dataSource;
    }

    private static void await(CountDownLatch gate) {
        try {
            if (!gate.await(15, TimeUnit.SECONDS))
                throw new AssertionError("Timed out waiting for controlled owner response");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    @BeforeEach
    void reset() {
        requests.failure.set(null);
        handler.failAfterCommit.set(false);
        payment.reset();
    }

    @AfterEach
    void cleanQueue() {
        // Test-owned tenants only; no worker is started by this context.
        for (String tenant : tenants) {
            jdbc.update("DELETE FROM loadup_outbox_replay WHERE tenant_id = ?", tenant);
            jdbc.update("DELETE FROM loadup_outbox_event WHERE tenant_id = ?", tenant);
        }
    }

    @Test
    void actualRequestInsertFailureRollsBackTheNativeEventAndRetryCreatesOneOriginalIntent() {
        var fixture = fixture();
        requests.failure.set("insert");
        assertThrows(IllegalStateException.class, () -> submit(fixture));
        assertEquals(0L, count("reconciliation_verification", fixture.tenant()));
        assertEquals(0L, count("loadup_outbox_event", fixture.tenant()));
        assertEquals(0, payment.calls.get());
        var first = submit(fixture);
        var replay = submit(fixture);
        assertEquals(first, replay);
        assertEquals("INIT", first.state().name());
        assertNull(first.receipt());
        assertEquals(1L, count("reconciliation_verification", fixture.tenant()));
        assertEquals(1L, count("loadup_outbox_event", fixture.tenant()));
        assertOriginal(fixture);
    }

    @Test
    void sqlReceiptFailureRollsBackReceiptVersionButNativeFailureCanReplayTheSameReadOnlyQuery() {
        var fixture = fixture();
        var initial = submit(fixture);
        requests.failure.set("update");
        assertEquals(1, dispatcher.dispatchBatch());
        var failed = detail(fixture, initial.requestId());
        assertEquals("INIT", failed.state().name());
        assertNull(failed.receipt());
        assertEquals(0L, failed.version());
        assertEquals("FAILED", failed.deliveryStatus());
        assertEquals(1, failed.attempts());
        assertEquals(1, payment.calls.get());
        var pending = retry(fixture, initial);
        assertEquals(initial.requestId(), pending.requestId());
        assertEquals(initial.deliveryId(), pending.deliveryId());
        assertEquals("PENDING", pending.deliveryStatus());
        assertNull(pending.receipt());
        assertEquals(1L, count("loadup_outbox_replay", fixture.tenant()));
        assertEquals("ops", jdbc.queryForObject("SELECT operator_id FROM loadup_outbox_replay WHERE tenant_id = ? AND event_id = ?",
                String.class, fixture.tenant(), initial.deliveryId()));
        assertEquals(1, dispatcher.dispatchBatch());
        var recorded = detail(fixture, initial.requestId());
        assertEquals("RECORDED", recorded.state().name());
        assertEquals(1L, recorded.version());
        assertEquals("UNKNOWN", recorded.receipt().outcome().name());
        assertEquals("SUCCEEDED", recorded.deliveryStatus());
        assertEquals(2, payment.calls.get());
        assertOriginal(fixture);
    }

    @Test
    void businessCommitThenLostAckReplaysNativeDeliveryWithoutQueryingOrReplacingTheReceipt() {
        var fixture = fixture();
        var initial = submit(fixture);
        handler.failAfterCommit.set(true);
        assertEquals(1, dispatcher.dispatchBatch());
        var committed = detail(fixture, initial.requestId());
        assertEquals("RECORDED", committed.state().name());
        assertEquals("FAILED", committed.deliveryStatus());
        assertNotNull(committed.receipt());
        assertEquals(1, payment.calls.get());
        retry(fixture, initial);
        assertEquals(1, dispatcher.dispatchBatch());
        var recovered = detail(fixture, initial.requestId());
        assertEquals("SUCCEEDED", recovered.deliveryStatus());
        assertEquals(committed.receipt(), recovered.receipt());
        assertEquals(committed.version(), recovered.version());
        assertEquals(1, payment.calls.get());
        assertEquals(1L, count("reconciliation_verification", fixture.tenant()));
        assertOriginal(fixture);
    }

    @Test
    void twoConcurrentSubmissionsReturnOneDurableRequestAndOneNativeEvent() throws Exception {
        var fixture = fixture();
        var ready = new CyclicBarrier(2);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<ReconciliationVerificationDTO> action = () -> {
                ready.await(10, TimeUnit.SECONDS);
                return submit(fixture);
            };
            var first = executor.submit(action);
            var second = executor.submit(action);
            assertEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1L, count("reconciliation_verification", fixture.tenant()));
        assertEquals(1L, count("loadup_outbox_event", fixture.tenant()));
        assertEquals(0, payment.calls.get());
    }

    @Test
    void reclaimedNativeClaimCommitsFirstAndTheOlderReadCannotReplaceItsReceiptOrAck() throws Exception {
        var fixture = fixture();
        var initial = submit(fixture);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var sequence = new AtomicInteger();
        payment.action.set((tenant, query) -> {
            if (sequence.incrementAndGet() == 1) {
                entered.countDown();
                await(release);
                return Payment.observed(query, "c".repeat(64));
            }
            return Payment.observed(query, "d".repeat(64));
        });
        var properties = new OutboxProperties();
        properties.setWorkerEnabled(false);
        properties.setBatchSize(1);
        properties.setMaxAttempts(3);
        var secondWorker = new OutboxDispatcher(new JdbcOutboxRepository(dataSource, properties), properties, List.of(handler), null);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var oldWorker = executor.submit(dispatcher::dispatchBatch);
            try {
                assertTrue(entered.await(10, TimeUnit.SECONDS));
                // Expire only this test claim instead of relying on wall-clock sleeps.
                assertEquals(1, jdbc.update("UPDATE loadup_outbox_event SET lease_until = TIMESTAMPADD(SECOND, -1, CURRENT_TIMESTAMP(6)) WHERE tenant_id = ? AND id = ? AND status = 'PROCESSING'",
                        fixture.tenant(), initial.deliveryId()));
                assertEquals(1, secondWorker.dispatchBatch());
                var winner = detail(fixture, initial.requestId());
                assertEquals("d".repeat(64), winner.receipt().sourceDigest());
                assertEquals("SUCCEEDED", winner.deliveryStatus());
                assertEquals(2, winner.attempts());
            } finally {
                release.countDown();
            }
            assertEquals(1, oldWorker.get(20, TimeUnit.SECONDS));
        }
        var originalReceipt = detail(fixture, initial.requestId());
        assertEquals("d".repeat(64), originalReceipt.receipt().sourceDigest());
        assertEquals(1L, originalReceipt.version());
        assertEquals("SUCCEEDED", originalReceipt.deliveryStatus());
        assertEquals(2, payment.calls.get());
        assertOriginal(fixture);
    }

    @Test
    void missingOwnerCoverageRemainsANullFinancialSnapshotAndOtherTenantCannotReadTheRequest() {
        var fixture = fixture();
        var initial = submit(fixture);
        payment.action.set((tenant, query) -> {
            var at = OffsetDateTime.ofInstant(Instant.parse("2026-10-28T06:00:00Z"), ZoneId.systemDefault());
            return new PaymentVerificationDTO(query, PaymentVerificationDisposition.NO_LOCAL_TARGET,
                    null, null, null, null, null, null, null, null, null, null, null, null, at, at, null);
        });
        assertEquals(1, dispatcher.dispatchBatch());
        var value = detail(fixture, initial.requestId());
        assertEquals("NO_LOCAL_TARGET", value.receipt().disposition().name());
        assertNull(value.receipt().amount());
        assertNull(value.receipt().sourceDigest());
        assertNull(value.receipt().sourceVersion());
        assertThrows(CommonException.class, () -> actors.call("other-" + UUID.randomUUID(), "ops", () -> service.detail(new IdQuery(initial.requestId()))));
        assertOriginal(fixture);
    }

    private ReconciliationVerificationDTO submit(Fixture fixture) {
        return actors.call(fixture.tenant(), "ops", () -> service.submit(new ReconciliationVerificationSubmitCommand(
                fixture.batchId(), fixture.differenceId(), fixture.proposalId(), fixture.approvalId(), 2L, "verification")));
    }

    private ReconciliationVerificationDTO detail(Fixture fixture, String id) {
        return actors.call(fixture.tenant(), "ops", () -> service.detail(new IdQuery(id)));
    }

    private ReconciliationVerificationDTO retry(Fixture fixture, ReconciliationVerificationDTO initial) {
        return actors.call(fixture.tenant(), "ops", () -> service.retry(new ReconciliationVerificationRetryCommand(initial.requestId(), initial.deliveryId(), "retry original query delivery")));
    }

    private void assertOriginal(Fixture fixture) {
        var original = transactions.readSnapshot(fixture.tenant(), () -> batches.find(fixture.tenant(), fixture.batchId()).orElseThrow().batch());
        assertEquals("DIFFERENCES", original.state().name());
        assertEquals(fixture.resultHash(), original.result().resultHash());
        assertEquals(1L, original.result().differenceCount());
        assertEquals(1L, count("reconciliation_difference", fixture.tenant()));
        assertEquals(2L, count("reconciliation_review_event", fixture.tenant()));
    }

    private Fixture fixture() {
        String tenant = "tenant-" + UUID.randomUUID();
        tenants.add(tenant);
        String batchId = UUID.randomUUID().toString();
        String run = UUID.randomUUID().toString();
        var scope = new ReconciliationScope(Channel.WECHAT, "a".repeat(64), LocalDate.of(2026, 10, 27), "wechat-trade-all-20260116");
        var key = new ReconciliationKey(ReconciliationRecordKind.PAYMENT, "transaction", null);
        var channelLine = new ReconciliationLine(key, "statement-request", Money.ofMinor(100L, "CNY"), ReconciliationEntryStatus.SUCCEEDED, 1L);
        var ledgerLine = new ReconciliationLine(key, "ledger-request", Money.ofMinor(90L, "CNY"), ReconciliationEntryStatus.SUCCEEDED, 1L);
        var cFile = upload(tenant, "channel");
        var lFile = upload(tenant, "ledger");
        var channel = dataset(scope, cFile, channelLine, ReconciliationSide.CHANNEL);
        var ledger = dataset(scope, lFile, ledgerLine, ReconciliationSide.LEDGER);
        var result = new ReconciliationMatcher().compare(channel, ledger);
        transactions.writeLocal(tenant, () -> {
            var initial = ReconciliationBatch.init(batchId, scope);
            batches.insert(tenant, stored(initial));
            batches.lock(tenant, batchId).orElseThrow();
            files.attach(tenant, "batch-owner", cFile, cFile.fileId());
            files.attach(tenant, "batch-owner", lFile, lFile.fileId());
            inputs.insertSealed(tenant, cFile.fileId(), channel);
            inputs.insertSealed(tenant, lFile.fileId(), ledger);
            var first = initial.attach(channel.manifest());
            batches.update(tenant, stored(first), initial.version());
            var ready = first.attach(ledger.manifest());
            batches.update(tenant, stored(ready), first.version());
            var running = ready.begin(run);
            batches.update(tenant, stored(running), ready.version());
            results.insert(tenant, batchId, run, result);
            batches.update(tenant, stored(running.complete(run, result)), running.version());
            return true;
        });
        String differenceId = jdbc.queryForObject("SELECT id FROM reconciliation_difference WHERE tenant_id = ? AND batch_id = ?", String.class, tenant, batchId);
        var proposed = actors.call(tenant, "maker", () -> reviews.propose(new ReconciliationReviewProposeCommand(batchId, differenceId,
                "proposal", 0L, ReconciliationReviewConclusion.NEEDS_SOURCE_VERIFICATION, "independent source query required", null)));
        var approved = actors.call(tenant, "checker", () -> reviews.decide(new ReconciliationReviewDecideCommand(batchId, differenceId,
                proposed.proposalId(), "approval", 1L, true, "approved read-only source consultation")));
        return new Fixture(tenant, batchId, differenceId, result.summary().resultHash(), proposed.proposalId(), approved.eventId());
    }

    private ReconciliationFileSnapshot upload(String tenant, String name) {
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        return files.upload(tenant, "batch-owner", name + ".bin", "application/octet-stream", bytes.length, new ByteArrayInputStream(bytes));
    }

    private StoredReconciliationBatch stored(ReconciliationBatch batch) {
        return new StoredReconciliationBatch(batch, "batch-owner", "request", "b".repeat(64), "account");
    }

    private ReconciliationDataset dataset(ReconciliationScope scope, ReconciliationFileSnapshot file, ReconciliationLine line, ReconciliationSide side) {
        return new ReconciliationDataset(new ReconciliationManifest(side, scope, file.fileId(), file.sha256(),
                ReconciliationDataset.normalizedHash(scope, side, "parser", List.of(line)), file.fileId(), "parser", ReconciliationTotals.count(List.of(line))), List.of(line));
    }

    private long count(String table, String tenant) {
        if (!Set.of("reconciliation_verification", "loadup_outbox_event", "loadup_outbox_replay", "reconciliation_difference", "reconciliation_review_event").contains(table))
            throw new AssertionError("Unexpected table");
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?", Long.class, tenant);
    }

    record Fixture(String tenant, String batchId, String differenceId, String resultHash, String proposalId,
                   String approvalId) {
    }

    static final class Actors implements ReconciliationActorProvider {
        final ThreadLocal<ReconciliationActor> actor = new ThreadLocal<>();

        @Override
        public ReconciliationActor current() {
            return actor.get();
        }

        <T> T call(String tenant, String caller, Supplier<T> action) {
            actor.set(new ReconciliationActor(tenant, caller, Set.of("pay:reconciliation:review:propose", "pay:reconciliation:review:decide",
                    "pay:reconciliation:verification:submit", "pay:reconciliation:verification:read", "pay:reconciliation:verification:retry")));
            try {
                return action.get();
            } finally {
                actor.remove();
            }
        }
    }

    static final class Payment implements ReconciliationPaymentVerification {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<BiFunction<String, PaymentVerificationQuery, PaymentVerificationDTO>> action = new AtomicReference<>();

        static PaymentVerificationDTO observed(PaymentVerificationQuery query, String digest) {
            var at = OffsetDateTime.ofInstant(Instant.parse("2026-10-28T06:00:00Z"), ZoneId.systemDefault());
            return new PaymentVerificationDTO(query, PaymentVerificationDisposition.OBSERVED, "revision", "payment", "attempt", null,
                    3L, "UNKNOWN", PaymentVerificationOutcome.UNKNOWN, Money.ofMinor(90L, "CNY"), null, null, null, digest, at, at, null);
        }

        void reset() {
            calls.set(0);
            action.set((tenant, query) -> observed(query, "c".repeat(64)));
        }

        @Override
        public PaymentVerificationDTO verify(String tenant, PaymentVerificationQuery query) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(tenant, TenantUtil.getTenantId());
            assertEquals("ledger-request", query.merchantPaymentNo());
            calls.incrementAndGet();
            return action.get().apply(tenant, query);
        }
    }

    static class FaultingVerifications extends ReconciliationVerificationGatewayImpl {
        final AtomicReference<String> failure = new AtomicReference<>();

        FaultingVerifications(ReconciliationVerificationDOMapper mapper, ReconciliationVerificationStorageConverter converter) {
            super(mapper, converter);
        }

        @Override
        public void insert(String tenant, ReconciliationVerificationRequest value) {
            super.insert(tenant, value);
            if (failure.compareAndSet("insert", null))
                throw new IllegalStateException("Injected after actual request INSERT");
        }

        @Override
        public void update(String tenant, ReconciliationVerificationRequest value, long expectedVersion) {
            super.update(tenant, value, expectedVersion);
            if (failure.compareAndSet("update", null))
                throw new IllegalStateException("Injected after actual receipt UPDATE");
        }
    }

    static final class Handler implements OutboxHandler {
        final ReconciliationVerificationDelivery delivery;
        final AtomicBoolean failAfterCommit = new AtomicBoolean();

        Handler(ReconciliationVerificationDelivery delivery) {
            this.delivery = delivery;
        }

        @Override
        public String eventType() {
            return ReconciliationVerificationMessages.TYPE;
        }

        @Override
        public void handle(OutboxEvent event) {
            delivery.deliver(event);
            if (failAfterCommit.compareAndSet(true, false))
                throw new IllegalStateException("Injected after receipt commit before native ACK");
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = ReconciliationVerificationDOMapper.class)
    @ComponentScan(basePackageClasses = {ReconciliationReviewStorageConverter.class, ReconciliationReviewConverter.class})
    @Import({ReconciliationVerificationService.class, ReconciliationVerificationDelivery.class, ReconciliationReviewService.class,
            ReconciliationTransactions.class, ReconciliationReviewGatewayImpl.class, ReconciliationBatchGatewayImpl.class,
            ReconciliationInputGatewayImpl.class, ReconciliationResultGatewayImpl.class, FileResourceReconciliationSources.class})
    static class Application {
        @Bean
        Actors actors() {
            return new Actors();
        }

        @Bean
        Payment payment() {
            return new Payment();
        }

        @Bean
        Handler handler(ReconciliationVerificationDelivery delivery) {
            return new Handler(delivery);
        }

        @Bean
        FaultingVerifications verifications(ReconciliationVerificationDOMapper mapper, ReconciliationVerificationStorageConverter converter) {
            return new FaultingVerifications(mapper, converter);
        }

        @Bean("reconciliationClock")
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-28T06:00:00Z"), ZoneId.systemDefault());
        }

        @Bean
        ReconciliationAccountDirectory accounts() {
            return new ReconciliationAccountDirectory() {
                @Override
                public ReconciliationScope resolve(String tenant, String caller, String code, LocalDate date) {
                    return new ReconciliationScope(Channel.WECHAT, "a".repeat(64), date, "wechat-trade-all-20260116");
                }

                @Override
                public void authorize(String tenant, String caller, String code, ReconciliationScope scope) {
                    if (!Set.of("maker", "checker", "ops").contains(caller) || !"account".equals(code))
                        throw new CommonException(io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.ACTION_FORBIDDEN);
                }

                @Override
                public ReconciliationStatementAccount statementAccount(String tenant, String caller, String code, ReconciliationScope scope) {
                    throw new AssertionError("Not a source operation");
                }

                @Override
                public String statementBindingRevision(String tenant, String caller, String code, ReconciliationScope scope) {
                    throw new AssertionError("Not a source operation");
                }
            };
        }

        @Bean
        DfsService dfs() throws IOException {
            return new ReconciliationImportPersistenceIT.Application().dfs();
        }
    }
}
