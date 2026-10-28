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
import io.github.loadup.components.dfs.DfsService;
import io.github.loadup.components.testcontainers.annotation.ContainerType;
import io.github.loadup.components.testcontainers.annotation.EnableTestContainers;
import io.github.loadup.pay.reconciliation.app.converter.ReconciliationReviewConverter;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationSourceFiles;
import io.github.loadup.pay.reconciliation.app.security.ReconciliationActor;
import io.github.loadup.pay.reconciliation.app.security.ReconciliationActorProvider;
import io.github.loadup.pay.reconciliation.app.service.ReconciliationReviewService;
import io.github.loadup.pay.reconciliation.app.service.ReconciliationTransactions;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationReviewDecideCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationReviewProposeCommand;
import io.github.loadup.pay.reconciliation.client.query.ReconciliationReviewQuery;
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
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReview;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewEvent;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewProposal;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationScope;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationSide;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationStatementAccount;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationTotals;
import io.github.loadup.pay.reconciliation.domain.model.StoredReconciliationBatch;
import io.github.loadup.pay.reconciliation.infrastructure.client.FileResourceReconciliationSources;
import io.github.loadup.pay.reconciliation.infrastructure.converter.ReconciliationReviewStorageConverter;
import io.github.loadup.pay.reconciliation.infrastructure.mapper.ReconciliationReviewDOMapper;
import io.github.loadup.pay.reconciliation.infrastructure.mapper.ReconciliationReviewEventDOMapper;
import io.github.loadup.pay.reconciliation.infrastructure.mapper.ReconciliationReviewProposalDOMapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Real PostgreSQL review/File metadata; actor identity and private object byte transport remain controlled test inputs.
 */
@SpringBootTest(classes = ReconciliationReviewPersistenceIT.Application.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnableTestContainers(value = ContainerType.POSTGRESQL, reuse = false)
class ReconciliationReviewPersistenceIT {
    final ReconciliationReviewService service;
    final ReconciliationBatchGateway batches;
    final ReconciliationInputGateway inputs;
    final ReconciliationResultGateway results;
    final ReconciliationTransactions transactions;
    final ReconciliationSourceFiles files;
    final FaultingReviews persistence;
    final JdbcTemplate jdbc;
    final Actors actors;

    @Autowired
    ReconciliationReviewPersistenceIT(ReconciliationReviewService service, ReconciliationBatchGateway batches, ReconciliationInputGateway inputs,
                                      ReconciliationResultGateway results, ReconciliationTransactions transactions, ReconciliationSourceFiles files, FaultingReviews persistence,
                                      JdbcTemplate jdbc, Actors actors) {
        this.service = service;
        this.batches = batches;
        this.inputs = inputs;
        this.results = results;
        this.transactions = transactions;
        this.files = files;
        this.persistence = persistence;
        this.jdbc = jdbc;
        this.actors = actors;
    }

    @Test
    void proposalFailureRollsBackHeadProposalReceiptAndPermanentEvidenceReference() {
        var fixture = fixture();
        long references = count("file_resource_reference", fixture.tenant());
        var uploaded = actors.call(fixture.tenant(), "maker", () -> service.uploadEvidence("evidence.txt", "text/plain", 8, new ByteArrayInputStream("evidence".getBytes(StandardCharsets.UTF_8))));
        var command = propose(fixture, "proposal", uploaded.fileId());
        persistence.failRequest.set("proposal");
        assertThrows(IllegalStateException.class, () -> actors.call(fixture.tenant(), "maker", () -> service.propose(command)));
        assertEquals(0L, count("reconciliation_review", fixture.tenant()));
        assertEquals(0L, count("reconciliation_review_proposal", fixture.tenant()));
        assertEquals(0L, count("reconciliation_review_event", fixture.tenant()));
        assertEquals(references, count("file_resource_reference", fixture.tenant()));
        var first = actors.call(fixture.tenant(), "maker", () -> service.propose(command));
        var replay = actors.call(fixture.tenant(), "maker", () -> service.propose(command));
        assertEquals(first, replay);
        assertEquals(1L, count("reconciliation_review", fixture.tenant()));
        assertEquals(1L, count("reconciliation_review_event", fixture.tenant()));
        assertEquals(references + 1, count("file_resource_reference", fixture.tenant()));
    }

    @Test
    void checkerFailureRollsBackDecisionAndApprovalDoesNotChangeBatchDifferenceOrOriginalResult() {
        var fixture = fixture();
        var proposed = actors.call(fixture.tenant(), "maker", () -> service.propose(propose(fixture, "proposal", null)));
        var command = new ReconciliationReviewDecideCommand(fixture.batchId(), fixture.differenceId(), proposed.proposalId(), "decision", 1L, true, "verified independent conclusion");
        assertThrows(CommonException.class, () -> actors.call(fixture.tenant(), "maker", () -> service.decide(command)));
        persistence.failRequest.set("decision");
        assertThrows(IllegalStateException.class, () -> actors.call(fixture.tenant(), "checker", () -> service.decide(command)));
        assertEquals("PROPOSED", actors.call(fixture.tenant(), "checker", () -> service.detail(new ReconciliationReviewQuery(fixture.batchId(), fixture.differenceId()))).state().name());
        assertEquals(1L, count("reconciliation_review_event", fixture.tenant()));
        var approved = actors.call(fixture.tenant(), "checker", () -> service.decide(command));
        assertEquals("APPROVED", approved.state().name());
        assertEquals(2L, approved.version());
        assertEquals(proposed, actors.call(fixture.tenant(), "maker", () -> service.propose(propose(fixture, "proposal", null))));
        var original = transactions.readSnapshot(fixture.tenant(), () -> batches.find(fixture.tenant(), fixture.batchId()).orElseThrow().batch());
        assertEquals("DIFFERENCES", original.state().name());
        assertEquals(fixture.resultHash(), original.result().resultHash());
        assertEquals(1L, original.result().differenceCount());
        assertEquals(1L, count("reconciliation_difference", fixture.tenant()));
        assertEquals(2L, count("reconciliation_review_event", fixture.tenant()));
    }

    @Test
    void concurrentSameProposalReturnsOneOriginalReceiptAndOtherTenantCannotReadIt() throws Exception {
        var fixture = fixture();
        var command = propose(fixture, "proposal", null);
        var ready = new CyclicBarrier(2);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<io.github.loadup.pay.reconciliation.client.dto.ReconciliationReviewEventDTO> action = () -> {
                ready.await(10, TimeUnit.SECONDS);
                return actors.call(fixture.tenant(), "maker", () -> service.propose(command));
            };
            var a = executor.submit(action);
            var b = executor.submit(action);
            assertEquals(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1L, count("reconciliation_review", fixture.tenant()));
        assertEquals(1L, count("reconciliation_review_proposal", fixture.tenant()));
        assertEquals(1L, count("reconciliation_review_event", fixture.tenant()));
        assertThrows(CommonException.class, () -> actors.call("other-" + UUID.randomUUID(), "checker", () -> service.detail(new ReconciliationReviewQuery(fixture.batchId(), fixture.differenceId()))));
    }

    Fixture fixture() {
        var tenant = "tenant-" + UUID.randomUUID();
        var batchId = UUID.randomUUID().toString();
        var run = UUID.randomUUID().toString();
        var scope = new ReconciliationScope(Channel.WECHAT, "a".repeat(64), LocalDate.of(2026, 10, 27), "wechat-trade-all-20260116");
        var key = new ReconciliationKey(ReconciliationRecordKind.PAYMENT, "transaction", null);
        var channelLine = new ReconciliationLine(key, "payment", Money.ofMinor(100L, "CNY"), ReconciliationEntryStatus.SUCCEEDED, 1L);
        var ledgerLine = new ReconciliationLine(key, "payment", Money.ofMinor(90L, "CNY"), ReconciliationEntryStatus.SUCCEEDED, 1L);
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
        var differenceId = jdbc.queryForObject("SELECT id FROM reconciliation_difference WHERE tenant_id = ? AND batch_id = ?", String.class, tenant, batchId);
        return new Fixture(tenant, batchId, differenceId, result.summary().resultHash());
    }

    ReconciliationFileSnapshot upload(String tenant, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return files.upload(tenant, "batch-owner", value + ".bin", "application/octet-stream", bytes.length, new ByteArrayInputStream(bytes));
    }

    StoredReconciliationBatch stored(ReconciliationBatch value) {
        return new StoredReconciliationBatch(value, "batch-owner", "request", "b".repeat(64), "account");
    }

    ReconciliationDataset dataset(ReconciliationScope scope, ReconciliationFileSnapshot file, ReconciliationLine line, ReconciliationSide side) {
        return new ReconciliationDataset(new ReconciliationManifest(side, scope, file.fileId(), file.sha256(), ReconciliationDataset.normalizedHash(scope, side, "parser", List.of(line)), file.fileId(), "parser", ReconciliationTotals.count(List.of(line))), List.of(line));
    }

    ReconciliationReviewProposeCommand propose(Fixture fixture, String request, String file) {
        return new ReconciliationReviewProposeCommand(fixture.batchId(), fixture.differenceId(), request, 0L, io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewConclusion.TIMING_DIFFERENCE, "review rationale", file);
    }

    long count(String table, String tenant) {
        if (!Set.of("reconciliation_review", "reconciliation_review_proposal", "reconciliation_review_event", "file_resource_reference", "reconciliation_difference").contains(table))
            throw new AssertionError("Unexpected table");
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?", Long.class, tenant);
    }

    record Fixture(String tenant, String batchId, String differenceId, String resultHash) {
    }

    static final class Actors implements ReconciliationActorProvider {
        final ThreadLocal<ReconciliationActor> actor = new ThreadLocal<>();

        @Override
        public ReconciliationActor current() {
            return actor.get();
        }

        <T> T call(String tenant, String service, Supplier<T> action) {
            actor.set(new ReconciliationActor(tenant, service, Set.of("pay:reconciliation:review:upload", "pay:reconciliation:review:read", "pay:reconciliation:review:propose", "pay:reconciliation:review:decide")));
            try {
                return action.get();
            } finally {
                actor.remove();
            }
        }
    }

    static class FaultingReviews extends ReconciliationReviewGatewayImpl {
        final AtomicReference<String> failRequest = new AtomicReference<>();

        FaultingReviews(ReconciliationReviewDOMapper heads, ReconciliationReviewProposalDOMapper proposals, ReconciliationReviewEventDOMapper events, ReconciliationReviewStorageConverter converter) {
            super(heads, proposals, events, converter);
        }

        @Override
        public void commit(String tenant, ReconciliationReview before, ReconciliationReview after, ReconciliationReviewProposal proposal, ReconciliationReviewEvent event) {
            super.commit(tenant, before, after, proposal, event);
            String target = failRequest.get();
            if (event.requestKey().equals(target) && failRequest.compareAndSet(target, null))
                throw new IllegalStateException("Injected after actual review commit");
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = ReconciliationReviewDOMapper.class)
    @ComponentScan(basePackageClasses = {ReconciliationReviewStorageConverter.class, ReconciliationReviewConverter.class})
    @Import({ReconciliationReviewService.class, ReconciliationTransactions.class, ReconciliationBatchGatewayImpl.class,
            ReconciliationInputGatewayImpl.class, ReconciliationResultGatewayImpl.class, FileResourceReconciliationSources.class})
    static class Application {
        @Bean
        Actors actors() {
            return new Actors();
        }

        @Bean
        FaultingReviews reviews(ReconciliationReviewDOMapper heads, ReconciliationReviewProposalDOMapper proposals, ReconciliationReviewEventDOMapper events, ReconciliationReviewStorageConverter converter) {
            return new FaultingReviews(heads, proposals, events, converter);
        }

        @Bean("reconciliationClock")
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-28T06:00:00Z"), ZoneId.systemDefault());
        }

        @Bean
        io.github.loadup.pay.reconciliation.app.port.ReconciliationAccountDirectory accounts() {
            return new io.github.loadup.pay.reconciliation.app.port.ReconciliationAccountDirectory() {
                @Override
                public ReconciliationScope resolve(String tenant, String caller, String code, LocalDate date) {
                    return new ReconciliationScope(Channel.WECHAT, "a".repeat(64), date, "wechat-trade-all-20260116");
                }

                @Override
                public void authorize(String tenant, String caller, String code, ReconciliationScope scope) {
                    if (!Set.of("maker", "checker").contains(caller) || !"account".equals(code))
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
