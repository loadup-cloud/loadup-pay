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

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.money.Money;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.delayedtask.*;
import io.github.loadup.components.delayedtask.dataobject.DelayedTaskDO;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.components.testcontainers.annotation.ContainerType;
import io.github.loadup.components.testcontainers.annotation.EnableTestContainers;
import io.github.loadup.pay.channel.api.model.*;
import io.github.loadup.pay.payment.app.converter.*;
import io.github.loadup.pay.payment.app.model.RecoveryInventoryCell;
import io.github.loadup.pay.payment.app.model.UnknownRecoveryWindow;
import io.github.loadup.pay.payment.client.enums.PaymentRecoveryKind;
import io.github.loadup.pay.payment.app.port.*;
import io.github.loadup.pay.payment.app.service.*;
import io.github.loadup.pay.payment.domain.gateway.*;
import io.github.loadup.pay.payment.domain.model.*;
import io.github.loadup.pay.payment.domain.policy.FifoRefundPolicy;
import io.github.loadup.pay.payment.infrastructure.codec.RefundResultCodecImpl;
import io.github.loadup.pay.payment.infrastructure.converter.RefundResultStorageConverter;
import io.github.loadup.pay.payment.infrastructure.converter.RefundStorageConverter;
import io.github.loadup.pay.payment.infrastructure.mapper.*;
import io.github.loadup.pay.payment.infrastructure.task.RefundRecoveryTaskHandler;
import io.github.loadup.testify.starter.scenario.TestScenario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.payment.infrastructure.repository.FinancialPersistenceFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual collection success, refund persistence, projections and native recovery; no database substitutes. */
@SpringBootTest(classes = {PaymentResultPersistenceIT.Application.class, RefundRecoveryHandlerPersistenceIT.Configuration.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"loadup.delayedtask.max-attempts=3", "loadup.delayedtask.lease=PT5S", "loadup.delayedtask.batch-size=1",
                "loadup.modules.payment.recovery-polling.jitter-percent=0"})
@EnableTestContainers(value = ContainerType.MYSQL, reuse = false)
class RefundRecoveryHandlerPersistenceIT {
    private static final long REFUND_MINOR = 5000L;
    private final PaymentAcceptanceTransactions transactions;
    private final PaymentAcceptanceGateway payments;
    private final PaymentAttemptGateway attempts;
    private final ChannelBindingManagementGateway bindings;
    private final PaymentAcceptanceCodec codec;
    private final ChannelBindingCodec bindingCodec;
    private final PaymentResultService collectionResults;
    private final PaymentResultGateway collections;
    private final RefundGateway refunds;
    private final RefundResultGateway results;
    private final RefundExecutionLocks locks;
    private final RefundExecutionService execution;
    private final RefundResultService resultService;
    private final RefundProjectionEvents projections;
    private final RefundRecoveryService recovery;
    private final RefundRecoveryGateway cursors;
    private final RefundRecoveryTaskHandler handler;
    private final DelayedTaskTemplate tasks;
    private final DelayedTaskStore taskStore;
    private final DelayedTaskProperties taskProperties;
    private final DelayedTaskConverter taskConverter;
    private final PaymentResultPersistenceIT.ControlledClock clock;
    private final PaymentResultPersistenceIT.RemoteChannel remote;
    private final RefundChannelControl channel;
    private final RefundFaults faults;
    private final JsonMapper json;
    private final JdbcTemplate jdbc;
    private final TestScenario scenario;
    private final RecoveryInventoryReader inventory;
    private final UnknownRecoveryReader unknowns;
    private final FaultingRefundAcceptances acceptances;
    private final PaymentCaptureReader captureReader;

    @Autowired
    RefundRecoveryHandlerPersistenceIT(PaymentAcceptanceTransactions transactions, PaymentAcceptanceGateway payments,
            PaymentAttemptGateway attempts, ChannelBindingManagementGateway bindings, PaymentAcceptanceCodec codec,
            ChannelBindingCodec bindingCodec, PaymentResultService collectionResults, PaymentResultGateway collections,
            RefundGateway refunds, RefundResultGateway results, RefundExecutionLocks locks, RefundExecutionService execution,
            RefundResultService resultService, RefundProjectionEvents projections, RefundRecoveryService recovery,
            RefundRecoveryGateway cursors, RefundRecoveryTaskHandler handler, DelayedTaskTemplate tasks, DelayedTaskStore taskStore,
            DelayedTaskProperties taskProperties, DelayedTaskConverter taskConverter, PaymentResultPersistenceIT.ControlledClock clock,
            PaymentResultPersistenceIT.RemoteChannel remote, RefundChannelControl channel, RefundFaults faults,
            JsonMapper json, JdbcTemplate jdbc, TestScenario scenario, RecoveryInventoryReader inventory, UnknownRecoveryReader unknowns, FaultingRefundAcceptances acceptances, PaymentCaptureReader captureReader) {
        this.transactions = transactions; this.payments = payments; this.attempts = attempts; this.bindings = bindings;
        this.codec = codec; this.bindingCodec = bindingCodec; this.collectionResults = collectionResults; this.collections = collections;
        this.refunds = refunds; this.results = results; this.locks = locks; this.execution = execution; this.resultService = resultService;
        this.projections = projections; this.recovery = recovery; this.cursors = cursors; this.handler = handler; this.tasks = tasks;
        this.taskStore = taskStore; this.taskProperties = taskProperties; this.taskConverter = taskConverter; this.clock = clock;
        this.remote = remote; this.channel = channel; this.faults = faults; this.json = json; this.jdbc = jdbc; this.scenario = scenario;
        this.inventory = inventory;
        this.acceptances = acceptances;
        this.captureReader = captureReader;
        this.unknowns = unknowns;
    }

    @BeforeEach
    void resetObservations() {
        clock.reset();
        faults.next.set(null);
        channel.pause.set(null);
        clearInvocations(remote.channels, remote.initial, remote.preparation, remote.actions);
    }

    @AfterEach
    void submittedRefundRecoveryNeverCreatesAnotherFirstSubmission() {
        faults.next.set(null);
        verify(remote.channels, never()).submitRefund(any());
        verify(remote.channels, never()).submit(any());
        verify(remote.channels, never()).recover(any());
        verifyNoInteractions(remote.initial, remote.preparation, remote.actions);
    }

    @ParameterizedTest
    @EnumSource(FailurePoint.class)
    void refundStateCapacitySummaryReceiptAndOutboxRollBackAndOriginalRequestCanRecover(FailurePoint point) {
        var fixture = seedSubmittedRefund();
        try {
            faults.next.set(point);
            assertThrows(IllegalStateException.class, () -> execution.progress(fixture.tenant(), fixture.requestId()));
            assertNull(faults.next.get());
            assertSubmitted(fixture);
            assertRefundCounts(fixture, 0L, 0L, 8L);

            var claimed = claim(fixture, 0L);
            handle(claimed);
            assertTrue(taskStore.finish(claimed, now(), true, taskProperties));
            assertSucceeded(fixture, 2L, false, 1L);
            assertCursor(fixture, 0L, true);
            var original = channel.observations.get(fixture.refundId());
            var receipt = resultService.record(fixture.tenant(), original);
            assertEquals(RefundReceiptDisposition.APPLIED, receipt.disposition());
            assertEquals(receipt, resultService.record(fixture.tenant(), original));
            execution.progress(fixture.tenant(), fixture.requestId());
            assertSucceeded(fixture, 2L, false, 1L);
            verify(remote.channels, times(2)).recoverRefund(eq(fixture.prepared()));
        } finally {
            cancelRemainingTasks(fixture);
        }
    }

    @Test
    void expiredAndReclaimedRefundClaimsCannotSkipCurrentWork() {
        var fixture = seedSubmittedRefund();
        try {
            var expired = claim(fixture, 0L);
            clock.advance(Duration.ofSeconds(6));
            assertEquals(INVALID_STATE, assertThrows(CommonException.class, () -> handle(expired)).getResultCode());
            verifyNoInteractions(remote.channels);
            var current = claim(fixture, 0L);
            assertEquals(expired.getId(), current.getId());
            assertEquals(2, current.getAttempts());
            assertNotEquals(expired.getClaimToken(), current.getClaimToken());
            assertEquals(INVALID_STATE, assertThrows(CommonException.class, () -> handle(expired)).getResultCode());
            verifyNoInteractions(remote.channels);
            handle(current);
            assertTrue(taskStore.finish(current, now(), true, taskProperties));
            assertFalse(taskStore.finish(expired, now(), true, taskProperties));
            assertCursor(fixture, 0L, true);
            assertNativeCount(fixture, 1L);
            assertSucceeded(fixture, 2L, false, 1L);
            verify(remote.channels, times(1)).recoverRefund(eq(fixture.prepared()));
        } finally {
            cancelRemainingTasks(fixture);
        }
    }

    @Test
    void oldRefundQueryReturningAfterCurrentGenerationSucceededCannotRefundOrPublishAgain() throws Exception {
        var fixture = seedSubmittedRefund();
        var original = channel.observations.get(fixture.refundId());
        var unknown = new RefundObservation(original.refundId(), original.original(), original.channelRefundNo(),
                original.originalChannelTransactionNo(), ChannelRefundOutcome.Kind.UNKNOWN, original.amount(), null, null,
                RefundObservation.Source.QUERY_RESPONSE, "original-unknown", "b".repeat(64), null);
        channel.observations.put(fixture.refundId(), unknown);
        var pause = new PaymentResultPersistenceIT.PausedQuery();
        channel.pause.set(pause);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var oldClaim = claim(fixture, 0L);
            var oldWorker = workers.submit(() -> handle(oldClaim));
            try {
                assertTrue(pause.entered.await(10, TimeUnit.SECONDS));
                clock.advance(Duration.ofSeconds(6));
                var replacement = claim(fixture, 0L);
                assertEquals(oldClaim.getId(), replacement.getId());
                assertEquals(2, replacement.getAttempts());
                handle(replacement);
                assertTrue(taskStore.finish(replacement, now(), true, taskProperties));
                assertCursor(fixture, 1L, false);
                assertNativeCount(fixture, 2L);
                assertRefundCounts(fixture, 0L, 1L, 9L);
                assertReserved(fixture, RefundState.UNKNOWN, 2L);

                channel.observations.put(fixture.refundId(), original);
                clock.advance(Duration.ofSeconds(30));
                var current = claim(fixture, 1L);
                assertNotEquals(oldClaim.getId(), current.getId());
                handle(current);
                assertTrue(taskStore.finish(current, now(), true, taskProperties));
                assertCursor(fixture, 1L, true);
            } finally {
                pause.resume.countDown();
            }
            oldWorker.get(20, TimeUnit.SECONDS);
            assertFalse(taskStore.finish(oldClaim, now(), true, taskProperties));
            assertSucceeded(fixture, 3L, true, 2L);
            assertCursor(fixture, 1L, true);
            assertNativeCount(fixture, 2L);
            transactions.readSnapshot(fixture.tenant(), () -> {
                assertTrue(tasks.find(RefundRecoveryService.TYPE, key(fixture, 2L)).isEmpty());
                assertEquals(DelayedTaskStatus.SUCCEEDED, tasks.find(RefundRecoveryService.TYPE, key(fixture, 0L)).orElseThrow().status());
                assertEquals(DelayedTaskStatus.SUCCEEDED, tasks.find(RefundRecoveryService.TYPE, key(fixture, 1L)).orElseThrow().status());
                return true;
            });
            verify(remote.channels, times(3)).recoverRefund(eq(fixture.prepared()));
        } finally {
            pause.resume.countDown();
            channel.pause.set(null);
            cancelRemainingTasks(fixture);
        }
    }

    @Test
    void aFutureRefundGenerationCannotPreventTheOriginalCurrentTask() {
        var fixture = seedSubmittedRefund();
        try {
            transactions.writeLocal(fixture.tenant(), () -> {
                tasks.register(RefundRecoveryService.TYPE, key(fixture, 1L), AT.minusSeconds(1));
                return true;
            });
            var future = claim(fixture, 1L);
            assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> handle(future)).getResultCode());
            assertTrue(taskStore.finish(future, now(), false, taskProperties));
            verifyNoInteractions(remote.channels);
            assertCursor(fixture, 0L, false);
            assertSubmitted(fixture);
            var current = claim(fixture, 0L);
            handle(current);
            assertTrue(taskStore.finish(current, now(), true, taskProperties));
            assertCursor(fixture, 0L, true);
            assertSucceeded(fixture, 2L, false, 1L);
        } finally {
            cancelRemainingTasks(fixture);
        }
    }

    @Test
    void anotherReceiptForTheSameRefundFactCannotConsumeCapacityOrRepublishEventsAgain() {
        var fixture = seedSubmittedRefund();
        try {
            var current = claim(fixture, 0L);
            handle(current);
            assertTrue(taskStore.finish(current, now(), true, taskProperties));
            var fact = channel.observations.get(fixture.refundId());
            var later = new RefundObservation(fact.refundId(), fact.original(), fact.channelRefundNo(), fact.originalChannelTransactionNo(),
                    fact.kind(), fact.amount(), fact.channelRefundTransactionNo(), fact.succeededAt(), RefundObservation.Source.CALLBACK,
                    "same-refund-callback", "e".repeat(64), null);
            var ignored = resultService.record(fixture.tenant(), later);
            assertEquals(RefundReceiptDisposition.IGNORED, ignored.disposition());
            assertEquals(ignored, resultService.record(fixture.tenant(), later));
            assertSucceeded(fixture, 2L, false, 2L);
        } finally {
            cancelRemainingTasks(fixture);
        }
    }

    @Test
    void inventoryUsesTheFirstCommittedUnknownTimeAndDoesNotRewriteRefundCapacity() {
        var fixture = seedSubmittedRefund();
        try {
            var original = channel.observations.get(fixture.refundId());
            var unknown = new RefundObservation(original.refundId(), original.original(), original.channelRefundNo(),
                    original.originalChannelTransactionNo(), ChannelRefundOutcome.Kind.UNKNOWN, original.amount(), null, null,
                    RefundObservation.Source.QUERY_RESPONSE, "inventory-unknown", "b".repeat(64), null);
            faults.next.set(FailurePoint.AFTER_STATE);
            assertThrows(IllegalStateException.class, () -> resultService.record(fixture.tenant(), unknown));
            assertSubmitted(fixture);
            assertNull(jdbc.queryForObject("SELECT first_unknown_at FROM refund_order WHERE tenant_id=? AND id=?",
                    LocalDateTime.class, fixture.tenant(), fixture.refundId()));
            clock.advance(Duration.ofMinutes(2));
            var firstUnknownAt = now();
            resultService.record(fixture.tenant(), unknown);
            clock.advance(Duration.ofMinutes(1));
            resultService.record(fixture.tenant(), new RefundObservation(unknown.refundId(), unknown.original(), unknown.channelRefundNo(),
                    unknown.originalChannelTransactionNo(), unknown.kind(), unknown.amount(), null, null,
                    unknown.source(), "inventory-unknown-again", "b".repeat(64), null));
            transactions.readSnapshot(fixture.tenant(), () -> {
                var cells = inventory.read(fixture.tenant());
                var refund = cells.stream().filter(value -> value.kind() == RecoveryInventoryCell.Kind.REFUND).findFirst().orElseThrow();
                assertEquals(1L, refund.unknownCount());
                assertEquals(REFUND_MINOR, refund.nominalAmountMinor());
                assertEquals(firstUnknownAt, refund.oldestUnknownAt());
                var queue = unknowns.list(fixture.tenant(), PaymentRecoveryKind.REFUND_REQUEST,
                        new UnknownRecoveryWindow(firstUnknownAt, null, null, 10));
                assertEquals(1, queue.size());
                var child = queue.getFirst();
                assertEquals(fixture.refundId(), child.recordId());
                assertEquals(fixture.requestId(), child.subjectId());
                assertEquals(firstUnknownAt, child.firstUnknownAt());
                assertEquals(REFUND_MINOR, child.amount().getCent());
                assertEquals(0L, child.recoveryGeneration());
                assertFalse(child.recoveryCompleted());
                assertTrue(unknowns.list(fixture.tenant(), PaymentRecoveryKind.REFUND_REQUEST,
                        new UnknownRecoveryWindow(firstUnknownAt, child.firstUnknownAt(), child.recordId(), 10)).isEmpty());
                assertEquals(RecoveryInventoryCell.empty(RecoveryInventoryCell.Kind.PAYMENT), cells.stream()
                        .filter(value -> value.kind() == RecoveryInventoryCell.Kind.PAYMENT).findFirst().orElseThrow());
                return true;
            });
            assertReserved(fixture, RefundState.UNKNOWN, 2L);
            resultService.record(fixture.tenant(), original);
            assertEquals(firstUnknownAt, jdbc.queryForObject("SELECT first_unknown_at FROM refund_order WHERE tenant_id=? AND id=?",
                    LocalDateTime.class, fixture.tenant(), fixture.refundId()));
            transactions.readSnapshot(fixture.tenant(), () -> {
                assertEquals(RecoveryInventoryCell.empty(RecoveryInventoryCell.Kind.REFUND), inventory.read(fixture.tenant()).stream()
                        .filter(value -> value.kind() == RecoveryInventoryCell.Kind.REFUND).findFirst().orElseThrow());
                assertTrue(unknowns.list(fixture.tenant(), PaymentRecoveryKind.REFUND_REQUEST,
                        new UnknownRecoveryWindow(now(), null, null, 10)).isEmpty());
                return true;
            });
        } finally {
            cancelRemainingTasks(fixture);
        }
    }


    @Test
    void providerAcceptanceMetadataPreservesFirstReceiptAndLeavesFinancialVersionsAndAuditUntouched() {
        var fixture = seedSubmittedRefund();
        try {
            var original = channel.observations.get(fixture.refundId());
            var before = jdbc.queryForMap("SELECT state,row_version,updated_at FROM refund_order WHERE tenant_id = ? AND id = ?", fixture.tenant(), fixture.refundId());
            var first = acceptance(original, ChannelRefundOutcome.Kind.ACCEPTED, "accepted-original", AT);
            var receipt = resultService.record(fixture.tenant(), first);
            assertReserved(fixture, RefundState.PROCESSING, 1L);
            assertEquals(before, jdbc.queryForMap("SELECT state,row_version,updated_at FROM refund_order WHERE tenant_id = ? AND id = ?", fixture.tenant(), fixture.refundId()));
            assertEquals(receipt.receiptId(), jdbc.queryForObject("SELECT receipt_id FROM refund_acceptance_evidence WHERE tenant_id = ? AND id = ?", String.class, fixture.tenant(), fixture.refundId()));
            resultService.record(fixture.tenant(), acceptance(original, ChannelRefundOutcome.Kind.ACCEPTED, "accepted-conflict", AT.plusSeconds(1)));
            assertEquals(AT, jdbc.queryForObject("SELECT accepted_at FROM refund_acceptance_evidence WHERE tenant_id = ? AND id = ?", java.sql.Timestamp.class,
                    fixture.tenant(), fixture.refundId()).toLocalDateTime());
            assertEquals(1L, count("refund_acceptance_evidence", fixture.tenant()));
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM refund_result_anomaly WHERE tenant_id = ? AND reason_code = ?", Long.class,
                    fixture.tenant(), "REFUND_ACCEPTANCE_TIME_CONFLICT").longValue());
            assertEquals(before, jdbc.queryForMap("SELECT state,row_version,updated_at FROM refund_order WHERE tenant_id = ? AND id = ?", fixture.tenant(), fixture.refundId()));
        } finally { cancelRemainingTasks(fixture); }
    }

    @Test
    void failureAfterAcceptanceInsertRollsBackReceiptClaimStateCapacityAndOutboxTogether() {
        var fixture = seedSubmittedRefund();
        try {
            long beforeOutbox = count("loadup_outbox_event", fixture.tenant());
            var observed = acceptance(channel.observations.get(fixture.refundId()), ChannelRefundOutcome.Kind.SUCCEEDED, "accepted-success", AT);
            acceptances.failAfterWrite.set(fixture.refundId());
            assertThrows(IllegalStateException.class, () -> resultService.record(fixture.tenant(), observed));
            assertReserved(fixture, RefundState.PROCESSING, 1L);
            assertRefundCounts(fixture, 0L, 0L, beforeOutbox);
            assertEquals(0L, count("refund_acceptance_evidence", fixture.tenant()));
            resultService.record(fixture.tenant(), observed);
            assertEquals(1L, count("refund_acceptance_evidence", fixture.tenant()));
            assertEquals(1L, count("refund_transaction_claim", fixture.tenant()));
            transactions.readSnapshot(fixture.tenant(), () -> {
                assertEquals(RefundState.SUCCEEDED, refunds.load(fixture.tenant(), fixture.requestId()).request().children().getFirst().state());
                return true;
            });
        } finally { acceptances.failAfterWrite.set(null); cancelRemainingTasks(fixture); }
    }

    @Test
    void reconciliationNeverTurnsASubmittedRefundWithoutChannelAcceptanceDateIntoAnEmptyExport() {
        var fixture = seedSubmittedRefund();
        try {
            clock.advance(Duration.ofDays(2));
            var read = sourceCapture(fixture, AT);
            assertEquals(io.github.loadup.pay.payment.domain.model.PaymentCaptureBlocker.REFUND_ACCEPTANCE_MISSING, read.blocker());
            assertEquals(fixture.refundId(), read.blockerSubjectId()); assertTrue(read.lines().isEmpty());
            assertSubmitted(fixture);
        } finally { cancelRemainingTasks(fixture); }
    }

    @Test
    void reconciliationRefundDayComesFromAcceptedAtAndPendingStatusIsNotExportedAsSuccess() {
        var fixture = seedSubmittedRefund();
        try {
            clock.advance(Duration.ofDays(2));
            var acceptedAt = AT.plusDays(1); var original = channel.observations.get(fixture.refundId());
            resultService.record(fixture.tenant(), acceptance(original, ChannelRefundOutcome.Kind.ACCEPTED, "accepted-next-day", acceptedAt));
            var oldDay = sourceCapture(fixture, AT); var refundDay = sourceCapture(fixture, acceptedAt);
            assertNull(oldDay.blocker()); assertEquals(1L, oldDay.paymentCount()); assertEquals(0L, oldDay.refundCount());
            assertNull(refundDay.blocker()); assertEquals(0L, refundDay.paymentCount()); assertEquals(1L, refundDay.refundCount());
            var row = refundDay.lines().getFirst();
            assertEquals(io.github.loadup.pay.payment.domain.model.PaymentLedgerStatus.PROCESSING, row.status());
            assertEquals(acceptedAt, row.sourceAt()); assertEquals(fixture.prepared().channelRefundNo(), row.channelRefundNo());
            assertEquals(original.originalChannelTransactionNo(), row.channelTransactionNo()); assertEquals(REFUND_MINOR, row.amount().getCent());
            assertSubmitted(fixture);
            var success = new RefundObservation(original.refundId(), original.original(), original.channelRefundNo(), original.originalChannelTransactionNo(),
                    ChannelRefundOutcome.Kind.SUCCEEDED, original.amount(), original.channelRefundTransactionNo(), acceptedAt.plusMinutes(1),
                    RefundObservation.Source.QUERY_RESPONSE, "refund-succeeded-next-day", "c".repeat(64), acceptedAt);
            resultService.record(fixture.tenant(), success);
            assertEquals(io.github.loadup.pay.payment.domain.model.PaymentLedgerStatus.SUCCEEDED, sourceCapture(fixture, acceptedAt).lines().getFirst().status());
            assertEquals(io.github.loadup.pay.payment.domain.model.PaymentLedgerStatus.PROCESSING, row.status());
            resultService.record(fixture.tenant(), new RefundObservation(success.refundId(), success.original(), success.channelRefundNo(), success.originalChannelTransactionNo(),
                    success.kind(), success.amount(), success.channelRefundTransactionNo(), success.succeededAt(), success.source(), "conflicting-acceptance-date", "d".repeat(64), acceptedAt.plusSeconds(1)));
            var conflict = sourceCapture(fixture, acceptedAt);
            assertEquals(io.github.loadup.pay.payment.domain.model.PaymentCaptureBlocker.REFUND_ACCEPTANCE_CONFLICT, conflict.blocker()); assertTrue(conflict.lines().isEmpty());
        } finally { cancelRemainingTasks(fixture); }
    }

    private io.github.loadup.pay.payment.domain.model.PaymentCaptureRead sourceCapture(RefundFixture fixture, LocalDateTime sourceAt) {
        var day = sourceAt.atZone(clock.getZone()).withZoneSameInstant(java.time.ZoneOffset.ofHours(8)).toLocalDate();
        var scope = new io.github.loadup.pay.payment.domain.model.PaymentCaptureScope(fixture.prepared().original().channel(), fixture.prepared().original().accountScopeHash(), day,
                io.github.loadup.pay.payment.domain.model.PaymentCaptureScope.WECHAT_ALL, clock.getZone().getId());
        return transactions.readSnapshot(fixture.tenant(), 10, () -> captureReader.read(fixture.tenant(), scope));
    }

    private RefundObservation acceptance(RefundObservation value, ChannelRefundOutcome.Kind kind, String key, LocalDateTime acceptedAt) {
        return new RefundObservation(value.refundId(), value.original(), value.channelRefundNo(), value.originalChannelTransactionNo(), kind, value.amount(),
                kind == ChannelRefundOutcome.Kind.SUCCEEDED ? value.channelRefundTransactionNo() : null,
                kind == ChannelRefundOutcome.Kind.SUCCEEDED ? value.succeededAt() : null, value.source(), key, value.sourceDigest(), acceptedAt);
    }

    private RefundFixture seedSubmittedRefund() {
        var payment = FinancialPersistenceFixtures.prepared(codec, bindingCodec);
        transactions.writeLocal(payment.tenant(), () -> {
            bindings.insertHead(payment.tenant(), payment.head());
            bindings.insertRevision(payment.tenant(), payment.revision());
            payments.insertParent(payment.tenant(), new StoredPaymentGate(id("parent"), payment.command().payeeMerchantId(), "acquire",
                    PaymentExecutionGate.init(payment.command().orderId(), null).open(), 0L));
            payments.insertPaymentGate(payment.tenant(), new StoredPaymentGate(id("gate"), payment.command().payeeMerchantId(), "acquire",
                    PaymentExecutionGate.init(payment.command().orderId(), payment.command().paymentId()).open(), 0L));
            payments.insert(payment.tenant(), payment.payment());
            attempts.insert(payment.tenant(), payment.attempt());
            attempts.update(payment.tenant(), payment.attempt().beginSubmission(1L, "d".repeat(64), RiskDecision.notConnected(), AT), 0L);
            return true;
        });
        var identity = payment.attempt().attempt().identity();
        var success = new PaymentObservation(identity, ChannelPaymentOutcome.Kind.SUCCEEDED, payment.payment().payment().amount(),
                id("collection-transaction"), AT, PaymentObservation.Source.QUERY_RESPONSE, "original-payment-success", "c".repeat(64));
        collectionResults.record(payment.tenant(), success);
        var fixture = transactions.writeLocal(payment.tenant(), () -> {
            payments.lockParent(payment.tenant(), payment.command().orderId()).orElseThrow();
            var capacity = refunds.lockCapacities(payment.tenant(), payment.command().orderId()).getFirst();
            var amount = Money.ofMinor(REFUND_MINOR, "CNY");
            var item = new FifoRefundPolicy().plan(payment.command().orderId(), payment.command().payeeMerchantId(), amount, true, List.of(capacity)).getFirst();
            String requestId = id("refund-request");
            var refund = RefundOrder.init(id("refund"), requestId, item.paymentId(), item.amount(), UUID.randomUUID().toString().replace("-", ""));
            var child = new StoredRefundChild(refund, 1, identity, success.channelTransactionNo(), success.succeededAt());
            var request = RefundRequest.init(requestId, payment.command().orderId(), payment.command().payeeMerchantId(), requestId, amount, List.of(refund));
            String terms = json.writeValueAsString(payment.command().admittedTerms());
            var stored = new StoredRefundRequest(request, "CUSTOMER_REQUEST", codec.checksum(requestId), "a".repeat(64),
                    payment.command().admissionId(), terms, codec.checksum(terms), AT, List.of(child));
            refunds.insertPlan(payment.tenant(), stored, List.of(new RefundCapacityReservation(capacity, capacity.reserve(REFUND_MINOR))));
            projections.planned(payment.tenant(), stored, payment.payment().payment().amount());
            recovery.register(payment.tenant(), stored);
            var locked = locks.lock(payment.tenant(), requestId, refund.id());
            var submitting = refund.beginSubmission();
            var change = new RefundProgressChange(locked.child(), submitting, request, request.apply(submitting),
                    locked.capacity(), locked.capacity(), AT, null, null);
            results.update(payment.tenant(), change);
            projections.changed(payment.tenant(), change);
            return new RefundFixture(payment, requestId, refund.id(), new PreparedChannelRefund(identity, refund.id(), refund.channelRefundNo(),
                    amount, capacity.succeededAmount(), success.channelTransactionNo()));
        });
        var prepared = fixture.prepared();
        channel.originals.put(fixture.refundId(), prepared);
        channel.observations.put(fixture.refundId(), new RefundObservation(prepared.refundId(), prepared.original(), prepared.channelRefundNo(),
                prepared.originalChannelTransactionNo(), ChannelRefundOutcome.Kind.SUCCEEDED, prepared.amount(), id("refund-transaction"),
                AT, RefundObservation.Source.QUERY_RESPONSE, "original-refund-success", "c".repeat(64), null));
        return fixture;
    }

    private void assertSubmitted(RefundFixture fixture) {
        assertReserved(fixture, RefundState.PROCESSING, 1L);
    }

    private void assertReserved(RefundFixture fixture, RefundState state, long version) {
        transactions.readSnapshot(fixture.tenant(), () -> {
            var stored = refunds.load(fixture.tenant(), fixture.requestId());
            assertEquals(RefundRequestState.PROCESSING, stored.request().state());
            assertEquals(version, stored.request().version());
            assertEquals(state, stored.request().children().getFirst().state());
            assertEquals(version, stored.request().children().getFirst().version());
            assertEquals(fixture.prepared().channelRefundNo(), stored.request().children().getFirst().channelRefundNo());
            var capacity = collections.capacity(fixture.tenant(), fixture.payment().command().paymentId()).orElseThrow();
            assertEquals(REFUND_MINOR, capacity.reservedMinor());
            assertEquals(0L, capacity.refundedMinor());
            assertEquals(1L, capacity.version());
            scenario.assertDb("order_refund_summary").where("tenant_id", fixture.tenant()).exists()
                    .has("refunded_minor", 0L).has("reserved_minor", REFUND_MINOR).has("row_version", 1L);
            assertOriginalPayment(fixture);
            return true;
        });
    }

    private void assertSucceeded(RefundFixture fixture, long version, boolean hadUnknown, long receiptCount) {
        transactions.readSnapshot(fixture.tenant(), () -> {
            var stored = refunds.load(fixture.tenant(), fixture.requestId());
            assertEquals(RefundRequestState.SUCCEEDED, stored.request().state());
            assertEquals(version, stored.request().version());
            assertEquals(REFUND_MINOR, stored.request().succeededMinor());
            var refund = stored.request().children().getFirst();
            assertEquals(RefundState.SUCCEEDED, refund.state());
            assertEquals(version, refund.version());
            assertEquals(fixture.prepared().channelRefundNo(), refund.channelRefundNo());
            var claim = results.claim(fixture.tenant(), fixture.refundId()).orElseThrow();
            assertEquals(refund.terminalEvidenceId(), claim.receiptId());
            assertEquals(fixture.prepared().original(), claim.original());
            var capacity = collections.capacity(fixture.tenant(), fixture.payment().command().paymentId()).orElseThrow();
            assertEquals(REFUND_MINOR, capacity.refundedMinor());
            assertEquals(0L, capacity.reservedMinor());
            assertEquals(5000L, capacity.availableMinor());
            assertEquals(2L, capacity.version());
            scenario.assertDb("order_refund_summary").where("tenant_id", fixture.tenant()).exists()
                    .has("refunded_minor", REFUND_MINOR).has("reserved_minor", 0L).has("row_version", 2L);
            assertOriginalPayment(fixture);
            return true;
        });
        assertRefundCounts(fixture, 1L, receiptCount, hadUnknown ? 15L : 14L);
        transactions.readSnapshot(fixture.tenant(), () -> {
            assertTarget(fixture, RefundProjectionEvents.CHILD, hadUnknown ? 4L : 3L);
            assertTarget(fixture, RefundProjectionEvents.SUMMARY, 2L);
            assertTarget(fixture, RefundProjectionEvents.FEE, 2L);
            assertTarget(fixture, RefundProjectionEvents.MERCHANT, 2L);
            assertTarget(fixture, RefundProjectionEvents.LIMIT, 1L);
            return true;
        });
    }

    private void assertOriginalPayment(RefundFixture fixture) {
        var payment = payments.find(fixture.tenant(), fixture.payment().command().paymentId()).orElseThrow();
        assertEquals(PaymentState.SUCCEEDED, payment.payment().state());
        assertEquals(10000L, payment.payment().amount().getCent());
        assertEquals(2L, payment.payment().version());
        assertEquals(fixture.payment().attempt().attempt().id(), payment.payment().successfulAttemptId());
    }

    private void assertRefundCounts(RefundFixture fixture, long claims, long receipts, long outbox) {
        transactions.readSnapshot(fixture.tenant(), () -> {
            assertEquals(claims, count("refund_transaction_claim", fixture.tenant()));
            assertEquals(receipts, count("refund_channel_receipt", fixture.tenant()));
            assertEquals(0L, count("refund_result_anomaly", fixture.tenant()));
            assertEquals(outbox, count("loadup_outbox_event", fixture.tenant()));
            return true;
        });
    }

    private long count(String table, String tenant) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?", Long.class, tenant);
    }

    private void assertTarget(RefundFixture fixture, String type, long expected) {
        assertEquals(expected, jdbc.queryForObject("SELECT COUNT(*) FROM loadup_outbox_event WHERE tenant_id = ? AND event_type = ?",
                Long.class, fixture.tenant(), type).longValue());
    }

    private DelayedTaskDO claim(RefundFixture fixture, long generation) {
        var claimed = taskStore.claim(now(), taskProperties).orElseThrow();
        assertEquals(fixture.tenant(), claimed.getTenantId());
        assertEquals(RefundRecoveryService.TYPE, claimed.getTaskType());
        assertEquals(key(fixture, generation), claimed.getBizId());
        return claimed;
    }

    private void handle(DelayedTaskDO claimed) {
        transactions.withTenantContext(claimed.getTenantId(), () -> {
            handler.handle(taskConverter.toTask(claimed));
            return true;
        });
    }

    private void assertCursor(RefundFixture fixture, long generation, boolean completed) {
        transactions.readSnapshot(fixture.tenant(), () -> {
            var cursor = cursors.find(fixture.tenant(), fixture.requestId()).orElseThrow();
            assertEquals(generation, cursor.generation());
            assertEquals(completed, cursor.completed());
            assertEquals(cursor.dueAt(), tasks.find(RefundRecoveryService.TYPE, key(fixture, generation)).orElseThrow().executeAt());
            return true;
        });
    }

    private void assertNativeCount(RefundFixture fixture, long expected) {
        transactions.readSnapshot(fixture.tenant(), () -> {
            assertEquals(expected, jdbc.queryForObject("SELECT COUNT(*) FROM delayed_task WHERE tenant_id = ? AND task_type = ?",
                    Long.class, fixture.tenant(), RefundRecoveryService.TYPE).longValue());
            return true;
        });
    }

    private void cancelRemainingTasks(RefundFixture fixture) {
        transactions.writeLocal(fixture.tenant(), () -> {
            for (long generation = 0; generation <= 2; generation++) tasks.cancel(RefundRecoveryService.TYPE, key(fixture, generation));
            return true;
        });
    }

    private String key(RefundFixture fixture, long generation) { return new RefundRecoveryKey(fixture.requestId(), generation).bizId(); }
    private LocalDateTime now() { return LocalDateTime.now(clock); }

    private record RefundFixture(FinancialPersistenceFixtures.Fixture payment, String requestId, String refundId, PreparedChannelRefund prepared) {
        String tenant() { return payment.tenant(); }
        @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
    }

    enum FailurePoint { AFTER_CLAIM, AFTER_STATE, AFTER_OUTBOX, AFTER_RECEIPT }

    static final class RefundFaults {
        final AtomicReference<FailurePoint> next = new AtomicReference<>();
        void after(FailurePoint point) {
            if (next.compareAndSet(point, null)) throw new IllegalStateException("Injected after refund " + point);
        }
    }

    static class FaultingRefundResults extends RefundResultGatewayImpl {
        private final RefundFaults faults;

        FaultingRefundResults(RefundChannelReceiptDOMapper receipts, RefundResultAnomalyDOMapper anomalies, RefundTransactionClaimDOMapper claims,
                RefundOrderDOMapper children, RefundRequestDOMapper requests, RefundAllocationPlanDOMapper plans,
                PaymentRefundCapacityDOMapper capacities, RefundResultStorageConverter converter, RefundStorageConverter storage,
                Clock clock, RefundFaults faults) {
            super(receipts, anomalies, claims, children, requests, plans, capacities, converter, storage, clock);
            this.faults = faults;
        }

        @Override public void insertClaim(RefundTransactionClaim claim) {
            super.insertClaim(claim);
            faults.after(FailurePoint.AFTER_CLAIM);
        }
        @Override public void update(String tenant, RefundProgressChange change) {
            super.update(tenant, change);
            faults.after(FailurePoint.AFTER_STATE);
        }
        @Override public void insertReceipt(String tenant, RefundChannelReceipt receipt) {
            super.insertReceipt(tenant, receipt);
            faults.after(FailurePoint.AFTER_RECEIPT);
        }
    }

    static class FaultingRefundProjections extends RefundProjectionEvents {
        private final RefundFaults faults;

        FaultingRefundProjections(RefundSummaryGateway summaries, RefundSnapshotReader reader, RefundProjectionConverter converter,
                OutboxTemplate outbox, IdGenerator ids, PaymentAcceptanceGateway payments, RefundSettlementConverter financial, RefundFaults faults) {
            super(summaries, reader, converter, outbox, ids, payments, financial);
            this.faults = faults;
        }

        @Override public void changed(String tenant, RefundProgressChange change) {
            super.changed(tenant, change);
            faults.after(FailurePoint.AFTER_OUTBOX);
        }
    }

    static final class FaultingRefundAcceptances extends RefundAcceptanceGatewayImpl {
        final AtomicReference<String> failAfterWrite = new AtomicReference<>();
        FaultingRefundAcceptances(io.github.loadup.pay.payment.infrastructure.mapper.RefundAcceptanceDOMapper mapper,
                RefundOrderDOMapper children, RefundChannelReceiptDOMapper receipts,
                io.github.loadup.pay.payment.infrastructure.converter.RefundAcceptanceStorageConverter converter, RefundStorageConverter storage) {
            super(mapper, children, receipts, converter, storage);
        }
        @Override public boolean remember(String tenant, io.github.loadup.pay.payment.domain.model.RefundAcceptanceEvidence value, long expected) {
            boolean result = super.remember(tenant, value, expected);
            String target = failAfterWrite.get();
            if (value.refundId().equals(target) && failAfterWrite.compareAndSet(target, null)) throw new IllegalStateException("Injected after actual acceptance evidence insert");
            return result;
        }
    }

    static final class RefundChannelControl {
        final Map<String, PreparedChannelRefund> originals = new ConcurrentHashMap<>();
        final Map<String, RefundObservation> observations = new ConcurrentHashMap<>();
        final AtomicReference<PaymentResultPersistenceIT.PausedQuery> pause = new AtomicReference<>();

        RefundChannelControl(PaymentResultPersistenceIT.RemoteChannel remote) {
            when(remote.channels.canRefund(any())).thenAnswer(call -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                return true;
            });
            when(remote.channels.recoverRefund(any())).thenAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                PreparedChannelRefund request = call.getArgument(0);
                assertEquals(originals.get(request.refundId()), request);
                var fact = observations.get(request.refundId());
                var blocked = pause.getAndSet(null);
                if (blocked != null) blocked.waitForRelease();
                return fact;
            });
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import({PaymentCaptureReaderImpl.class, RefundGatewayImpl.class, RefundSnapshotReaderImpl.class, RefundSummaryGatewayImpl.class, RefundRecoveryGatewayImpl.class,
            RefundResultCodecImpl.class, RefundExecutionLocks.class, RefundExecutionService.class, RefundResultService.class,
            RefundRecoveryService.class, RefundRecoveryTaskHandler.class})
    static class Configuration {
        @Bean FaultingRefundAcceptances refundAcceptances(io.github.loadup.pay.payment.infrastructure.mapper.RefundAcceptanceDOMapper mapper,
                RefundOrderDOMapper children, RefundChannelReceiptDOMapper receipts,
                io.github.loadup.pay.payment.infrastructure.converter.RefundAcceptanceStorageConverter converter, RefundStorageConverter storage) {
            return new FaultingRefundAcceptances(mapper, children, receipts, converter, storage);
        }

        @Bean RefundFaults refundFaults() { return new RefundFaults(); }
        @Bean RefundChannelControl refundChannelControl(PaymentResultPersistenceIT.RemoteChannel remote) { return new RefundChannelControl(remote); }

        @Bean
        RefundResultGateway refundResults(RefundChannelReceiptDOMapper receipts, RefundResultAnomalyDOMapper anomalies,
                RefundTransactionClaimDOMapper claims, RefundOrderDOMapper children, RefundRequestDOMapper requests,
                RefundAllocationPlanDOMapper plans, PaymentRefundCapacityDOMapper capacities, RefundResultStorageConverter converter,
                RefundStorageConverter storage, @Qualifier("paymentClock") Clock clock, RefundFaults faults) {
            return new FaultingRefundResults(receipts, anomalies, claims, children, requests, plans, capacities, converter, storage, clock, faults);
        }

        @Bean
        RefundProjectionEvents refundProjections(RefundSummaryGateway summaries, RefundSnapshotReader reader, RefundProjectionConverter converter,
                OutboxTemplate outbox, IdGenerator ids, PaymentAcceptanceGateway payments, RefundSettlementConverter financial, RefundFaults faults) {
            return new FaultingRefundProjections(summaries, reader, converter, outbox, ids, payments, financial, faults);
        }
    }
}
