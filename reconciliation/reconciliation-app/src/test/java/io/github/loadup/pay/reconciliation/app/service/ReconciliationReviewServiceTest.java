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

import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.pay.reconciliation.app.converter.ReconciliationConverter;
import io.github.loadup.pay.reconciliation.app.converter.ReconciliationReviewConverter;
import io.github.loadup.pay.reconciliation.app.model.StoredReconciliationDifference;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationAccountDirectory;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationDifferenceReader;
import io.github.loadup.pay.reconciliation.app.port.ReconciliationSourceFiles;
import io.github.loadup.pay.reconciliation.app.security.ReconciliationActor;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationReviewDecideCommand;
import io.github.loadup.pay.reconciliation.client.command.ReconciliationReviewProposeCommand;
import io.github.loadup.pay.reconciliation.client.query.ReconciliationReviewHistoryQuery;
import io.github.loadup.pay.reconciliation.client.query.ReconciliationReviewQuery;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationBatchGateway;
import io.github.loadup.pay.reconciliation.domain.gateway.ReconciliationReviewGateway;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationBatch;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationDataset;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationDifference;
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationDifferenceReason;
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
import io.github.loadup.pay.reconciliation.domain.model.ReconciliationTotals;
import io.github.loadup.pay.reconciliation.domain.model.StoredReconciliationBatch;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.REVIEW_CHECKER_REQUIRED;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.REVIEW_VERSION_CONFLICT;
import static io.github.loadup.pay.reconciliation.client.error.ReconciliationResultCodeEnum.SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Focused application semantics only; fake transaction manager does not establish database rollback proof.
 */
class ReconciliationReviewServiceTest {
    final AnnotationConfigApplicationContext spring = new AnnotationConfigApplicationContext(ReconciliationReviewConverter.class.getPackageName());
    final jakarta.validation.ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final ReconciliationBatchGateway batches = mock(ReconciliationBatchGateway.class);
    final ReconciliationAccountDirectory accounts = mock(ReconciliationAccountDirectory.class);
    final ReconciliationDifferenceReader differences = mock(ReconciliationDifferenceReader.class);
    final ReconciliationReviewGateway reviews = mock(ReconciliationReviewGateway.class);
    final ReconciliationSourceFiles files = mock(ReconciliationSourceFiles.class);
    final AtomicReference<ReconciliationReview> head = new AtomicReference<>();
    final Map<String, ReconciliationReviewProposal> proposals = new HashMap<>();
    final Map<String, ReconciliationReviewEvent> events = new HashMap<>();
    final AtomicReference<ReconciliationActor> actor = new AtomicReference<>(actor("maker"));
    final AtomicInteger sequence = new AtomicInteger();
    final Clock clock = Clock.fixed(Instant.parse("2026-10-28T06:00:00.123456Z"), ZoneId.systemDefault());
    final ReconciliationScope scope = new ReconciliationScope(Channel.WECHAT, "a".repeat(64), LocalDate.of(2026, 10, 27), "wechat-trade-all-20260116");
    final ReconciliationKey key = new ReconciliationKey(ReconciliationRecordKind.PAYMENT, "transaction", null);
    final ReconciliationLine channel = new ReconciliationLine(key, "payment", Money.ofMinor(100L, "CNY"), ReconciliationEntryStatus.SUCCEEDED, 1L);
    final ReconciliationLine ledger = new ReconciliationLine(key, "payment", Money.ofMinor(90L, "CNY"), ReconciliationEntryStatus.SUCCEEDED, 1L);
    final ReconciliationDifference difference = new ReconciliationDifference(key, channel, ledger, Set.of(ReconciliationDifferenceReason.AMOUNT_MISMATCH));
    final StoredReconciliationBatch closed = batch();
    final StoredReconciliationDifference storedDifference = new StoredReconciliationDifference("difference", "batch", "run", difference);
    final ReconciliationFileSnapshot evidence = new ReconciliationFileSnapshot("file", "private-storage", 100L, "d".repeat(64));
    final ReconciliationReviewService service = new ReconciliationReviewService(actor::get, accounts, batches, differences, reviews, files,
            new ReconciliationTransactions(new Tx()), spring.getBean(ReconciliationReviewConverter.class), spring.getBean(ReconciliationConverter.class),
            () -> "id-" + sequence.incrementAndGet(), validation.getValidator(), clock);

    static ReconciliationActor actor(String service) {
        return new ReconciliationActor("tenant", service, Set.of("pay:reconciliation:review:read", "pay:reconciliation:review:propose", "pay:reconciliation:review:decide"));
    }

    static void write() {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
    }

    @BeforeEach
    void setup() {
        when(batches.find("tenant", "batch")).thenReturn(Optional.of(closed));
        when(batches.lock("tenant", "batch")).thenAnswer(call -> {
            write();
            return Optional.of(closed);
        });
        when(differences.find("tenant", "batch", "difference")).thenReturn(Optional.of(storedDifference));
        when(reviews.find("tenant", "difference")).thenAnswer(call -> Optional.ofNullable(head.get()));
        when(reviews.lock("tenant", "difference")).thenAnswer(call -> {
            write();
            return Optional.ofNullable(head.get());
        });
        when(reviews.findRequest(anyString(), anyString(), anyString())).thenAnswer(call -> events.values().stream()
                .filter(value -> value.callerServiceId().equals(call.getArgument(1)) && value.requestKey().equals(call.getArgument(2))).findFirst());
        when(reviews.event(anyString(), anyString())).thenAnswer(call -> Optional.ofNullable(events.get(call.getArgument(1))));
        when(reviews.proposal(anyString(), anyString())).thenAnswer(call -> Optional.ofNullable(proposals.get(call.getArgument(1))));
        doAnswer(call -> {
            write();
            ReconciliationReviewProposal proposed = call.getArgument(3);
            ReconciliationReviewEvent event = call.getArgument(4);
            if (proposed != null) proposals.put(proposed.id(), proposed);
            events.put(event.id(), event);
            head.set(call.getArgument(2));
            return null;
        }).when(reviews).commit(anyString(), any(), any(), any(), any());
        when(files.inspect("tenant", "maker", "file")).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return evidence;
        });
        doAnswer(call -> {
            write();
            return null;
        }).when(files).attachReview(anyString(), anyString(), any(), anyString());
        when(reviews.history(eq("tenant"), eq("difference"), anyInt(), anyInt())).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            int page = call.getArgument(2), size = call.getArgument(3);
            var all = events.values().stream().sorted(Comparator.comparingLong(ReconciliationReviewEvent::version)).toList();
            long offset = (long) (page - 1) * size;
            return PageResult.of(all.stream().skip(offset).limit(size).toList(), all.size(), page, size);
        });
    }

    @AfterEach
    void close() {
        spring.close();
        validation.close();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void virtualInitAndIndependentApprovalDoNotChangeOriginalBatchOrDifference() {
        var initial = service.detail(new ReconciliationReviewQuery("batch", "difference"));
        assertEquals("INIT", initial.state().name());
        assertEquals(0L, initial.version());
        assertNull(initial.proposal());
        assertTrue(events.isEmpty());
        var proposed = service.propose(command("request", 0L, null));
        assertEquals("PROPOSED", proposed.state().name());
        assertEquals(1L, proposed.version());
        var self = new ReconciliationReviewDecideCommand("batch", "difference", proposed.proposalId(), "self", 1L, true, "private check");
        assertEquals(REVIEW_CHECKER_REQUIRED, assertThrows(CommonException.class, () -> service.decide(self)).getResultCode());
        actor.set(actor("checker"));
        var approved = service.decide(new ReconciliationReviewDecideCommand("batch", "difference", proposed.proposalId(), "approve", 1L, true, "private check"));
        assertEquals("APPROVED", approved.state().name());
        assertEquals(2L, approved.version());
        assertEquals(approved, service.decide(new ReconciliationReviewDecideCommand("batch", "difference", proposed.proposalId(), "approve", 1L, true, "private check")));
        var detail = service.detail(new ReconciliationReviewQuery("batch", "difference"));
        assertEquals(approved, detail.lastEvent());
        assertEquals("DIFFERENCES", closed.batch().state().name());
        verify(accounts, atLeastOnce()).authorize("tenant", "checker", "account", scope);
        verify(batches, never()).update(anyString(), any(), anyLong());
        verifyNoInteractions(files);
    }

    @Test
    void rejectionReproposalAndOldCommandReplayRetainAllPriorReceipts() {
        var first = service.propose(command("first", 0L, null));
        actor.set(actor("checker"));
        var rejected = service.decide(new ReconciliationReviewDecideCommand("batch", "difference", first.proposalId(), "reject", 1L, false, "more evidence needed"));
        actor.set(actor("maker"));
        var second = service.propose(command("second", 2L, "file"));
        assertEquals(3L, second.version());
        assertEquals(first, service.propose(command("first", 0L, null)));
        assertEquals(3L, head.get().version());
        assertNotEquals(first.proposalId(), second.proposalId());
        assertEquals(2, proposals.size());
        assertEquals(REVIEW_VERSION_CONFLICT, assertThrows(CommonException.class, () -> service.propose(command("stale", 1L, null))).getResultCode());
        assertEquals(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service.propose(command("first", 0L, "file"))).getResultCode());
        var history = service.history(new ReconciliationReviewHistoryQuery("batch", "difference", 1, 2));
        assertEquals(3L, history.getPageInfo().totalCount());
        assertEquals(List.of(first, rejected), List.copyOf(history.getData()));
        verify(files).inspect("tenant", "maker", "file");
        verify(files).attachReview("tenant", "maker", evidence, second.proposalId());
    }

    @Test
    void permissionsCurrentAccountGrantAndOriginalRunAreRequiredBeforeEvidenceIo() {
        actor.set(new ReconciliationActor("tenant", "maker", Set.of()));
        assertEquals(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.propose(command("request", 0L, "file"))).getResultCode());
        verifyNoInteractions(batches, differences, reviews, files);
        actor.set(actor("maker"));
        doThrow(new CommonException(ACTION_FORBIDDEN)).when(accounts).authorize("tenant", "maker", "account", scope);
        assertEquals(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.propose(command("request", 0L, "file"))).getResultCode());
        verifyNoInteractions(files);
        doNothing().when(accounts).authorize("tenant", "maker", "account", scope);
        when(differences.find("tenant", "batch", "difference")).thenReturn(Optional.of(new StoredReconciliationDifference("difference", "batch", "different-run", difference)));
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> service.propose(command("request", 0L, "file"))).getResultCode());
        verifyNoInteractions(files);
    }

    @Test
    void onlyTheExactApprovedNeedsVerificationProposalCanSupplyAnOwnerConsultationScope() {
        var command = new ReconciliationReviewProposeCommand("batch", "difference", "needs-verification", 0L,
                io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewConclusion.NEEDS_SOURCE_VERIFICATION, "consult original owner", null);
        var pending = service.propose(command);
        actor.set(actor("checker"));
        var approved = service.decide(new ReconciliationReviewDecideCommand("batch", "difference", pending.proposalId(), "approve", 1L, true, "independent check"));
        var tx = new ReconciliationTransactions(new Tx());
        var scope = tx.readSnapshot("tenant", () -> service.approvedForVerification(actor.get(), "batch", "difference", pending.proposalId(), approved.eventId(), 2L, false));
        assertEquals("payment", scope.merchantPaymentNo());
        assertEquals(2L, scope.reviewVersion());
        assertEquals(REVIEW_VERSION_CONFLICT, assertThrows(CommonException.class, () -> tx.readSnapshot("tenant", () -> service.approvedForVerification(actor.get(), "batch", "difference", pending.proposalId(), "different-event", 2L, false))).getResultCode());
    }

    @Test
    void evidenceAttachmentFailurePreventsHeadOrReceiptRegistration() {
        doThrow(new CommonException(DEPENDENCY_UNAVAILABLE)).when(files).attachReview(anyString(), anyString(), any(), anyString());
        assertEquals(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, () -> service.propose(command("request", 0L, "file"))).getResultCode());
        assertNull(head.get());
        assertTrue(events.isEmpty());
        verify(reviews, never()).commit(anyString(), any(), any(), any(), any());
    }

    ReconciliationReviewProposeCommand command(String key, long version, String file) {
        return new ReconciliationReviewProposeCommand("batch", "difference", key, version, io.github.loadup.pay.reconciliation.domain.model.ReconciliationReviewConclusion.TIMING_DIFFERENCE, "private rationale", file);
    }

    StoredReconciliationBatch batch() {
        var c = new ReconciliationDataset(manifest(ReconciliationSide.CHANNEL, channel), List.of(channel));
        var l = new ReconciliationDataset(manifest(ReconciliationSide.LEDGER, ledger), List.of(ledger));
        var result = new ReconciliationMatcher().compare(c, l);
        var closed = ReconciliationBatch.init("batch", scope).attach(c.manifest()).attach(l.manifest()).begin("run").complete("run", result);
        return new StoredReconciliationBatch(closed, "batch-owner", "create", "c".repeat(64), "account");
    }

    ReconciliationManifest manifest(ReconciliationSide side, ReconciliationLine line) {
        return new ReconciliationManifest(side, scope, side.name(), "b".repeat(64), ReconciliationDataset.normalizedHash(scope, side, "parser", List.of(line)), side.name() + "-source", "parser", ReconciliationTotals.count(List.of(line)));
    }

    static class Tx extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object value, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
