/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.payment.infrastructure.repository;

import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.payment.domain.model.PaymentReceiptDisposition.APPLIED;
import static io.github.loadup.payment.domain.model.PaymentReceiptDisposition.CONFLICT;
import static io.github.loadup.payment.domain.model.PaymentReceiptDisposition.IGNORED;
import static io.github.loadup.payment.infrastructure.repository.FinancialPersistenceFixtures.AT;
import static io.github.loadup.payment.infrastructure.repository.FinancialPersistenceFixtures.Fixture;
import static io.github.loadup.payment.infrastructure.repository.FinancialPersistenceFixtures.id;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.payment.channel.api.model.ChannelPaymentOutcome;
import io.github.loadup.components.payment.channel.api.model.ChannelPaymentRecovery;
import io.github.loadup.components.payment.channel.api.model.ChannelPaymentSubmission;
import io.github.loadup.components.payment.channel.api.model.ChannelPaymentTarget;
import io.github.loadup.components.payment.channel.api.model.PaymentObservation;
import io.github.loadup.components.task.JdbcTaskRepository;
import io.github.loadup.components.task.TaskExecution;
import io.github.loadup.components.task.TaskProperties;
import io.github.loadup.components.task.TaskStatus;
import io.github.loadup.components.task.TaskSubmission;
import io.github.loadup.components.task.TaskTemplate;
import io.github.loadup.components.testcontainers.annotation.ContainerType;
import io.github.loadup.components.testcontainers.annotation.EnableTestContainers;
import io.github.loadup.contracts.event.payment.PaymentState;
import io.github.loadup.payment.app.config.RecoveryPollingConfiguration;
import io.github.loadup.payment.app.converter.PaymentAcceptanceConverter;
import io.github.loadup.payment.app.converter.PaymentControlConverter;
import io.github.loadup.payment.app.converter.PaymentRealtimeConverter;
import io.github.loadup.payment.app.converter.PaymentResultConverter;
import io.github.loadup.payment.app.dispatch.PaymentDeliveryRoutes;
import io.github.loadup.payment.app.dispatch.PaymentFactRoutes;
import io.github.loadup.payment.app.model.PaymentResultApplication;
import io.github.loadup.payment.app.model.RecoveryInventoryCell;
import io.github.loadup.payment.app.model.UnknownRecoveryWindow;
import io.github.loadup.payment.app.port.ChannelBindingCodec;
import io.github.loadup.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.payment.app.port.RecoveryInventoryReader;
import io.github.loadup.payment.app.port.UnknownRecoveryReader;
import io.github.loadup.payment.app.service.PaymentAcceptanceTransactions;
import io.github.loadup.payment.app.service.PaymentActionService;
import io.github.loadup.payment.app.service.PaymentCloseService;
import io.github.loadup.payment.app.service.PaymentEvents;
import io.github.loadup.payment.app.service.PaymentExecutionLocks;
import io.github.loadup.payment.app.service.PaymentExecutionService;
import io.github.loadup.payment.app.service.PaymentInitialAttemptService;
import io.github.loadup.payment.app.service.PaymentRecoveryService;
import io.github.loadup.payment.app.service.PaymentResultService;
import io.github.loadup.payment.app.service.PaymentSubmissionPreparation;
import io.github.loadup.payment.app.service.RecoveryExecutionMetrics;
import io.github.loadup.payment.client.enums.PaymentRecoveryKind;
import io.github.loadup.payment.domain.gateway.ChannelBindingManagementGateway;
import io.github.loadup.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.payment.domain.gateway.PaymentChannelGateway;
import io.github.loadup.payment.domain.gateway.PaymentControlGateway;
import io.github.loadup.payment.domain.gateway.PaymentRecoveryGateway;
import io.github.loadup.payment.domain.gateway.PaymentResultGateway;
import io.github.loadup.payment.domain.model.ChannelTransactionClaim;
import io.github.loadup.payment.domain.model.PaymentAttemptState;
import io.github.loadup.payment.domain.model.PaymentChannelReceipt;
import io.github.loadup.payment.domain.model.PaymentExecutionGate;
import io.github.loadup.payment.domain.model.PaymentOrder;
import io.github.loadup.payment.domain.model.PaymentRecoveryKey;
import io.github.loadup.payment.domain.model.PaymentRefundCapacity;
import io.github.loadup.payment.domain.model.RiskDecision;
import io.github.loadup.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.payment.domain.model.StoredPaymentGate;
import io.github.loadup.payment.infrastructure.codec.ChannelBindingCodecImpl;
import io.github.loadup.payment.infrastructure.codec.PaymentAcceptanceCodecImpl;
import io.github.loadup.payment.infrastructure.codec.PaymentResultCodecImpl;
import io.github.loadup.payment.infrastructure.converter.PaymentResultStorageConverter;
import io.github.loadup.payment.infrastructure.task.PaymentRecoveryTaskHandler;
import io.github.loadup.testify.starter.scenario.TestScenario;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.PersistenceExceptionTranslationAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Actual Y3 and recovery handler with native claims; the original Y2 is a persisted boundary precondition.
 */
@SpringBootTest(
        classes = PaymentResultPersistenceIT.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            "loadup.task.max-attempts=3",
            "loadup.task.lease=PT5S",
            "loadup.task.batch-size=1",
            "loadup.task.task-type=pay.payment.collection.progress",
            "loadup.modules.payment.recovery-polling.jitter-percent=0"
        })
@EnableTestContainers(value = ContainerType.POSTGRESQL, reuse = false)
@ExtendWith(DatabaseResetExtension.class)
class PaymentResultPersistenceIT {
    private final PaymentAcceptanceTransactions transactions;
    private final PaymentAcceptanceGateway payments;
    private final PaymentAttemptGateway attempts;
    private final PaymentControlGateway changes;
    private final ChannelBindingManagementGateway bindings;
    private final PaymentResultGateway results;
    private final PaymentResultService resultService;
    private final PaymentExecutionService execution;
    private final PaymentAcceptanceCodec codec;
    private final ChannelBindingCodec bindingCodec;
    private final PaymentControlConverter updates;
    private final Faults faults;
    private final RemoteChannel remote;
    private final JdbcTemplate jdbc;
    private final TestScenario scenario;
    private final PaymentRecoveryService recovery;
    private final PaymentRecoveryGateway cursors;
    private final PaymentRecoveryTaskHandler handler;
    private final TaskTemplate tasks;
    private final JdbcTaskRepository taskStore;
    private final TaskProperties taskProperties;
    private final ControlledClock clock;
    private final RecoveryInventoryReader inventory;
    private final UnknownRecoveryReader unknowns;

    @Autowired
    PaymentResultPersistenceIT(
            PaymentAcceptanceTransactions transactions,
            PaymentAcceptanceGateway payments,
            PaymentAttemptGateway attempts,
            PaymentControlGateway changes,
            ChannelBindingManagementGateway bindings,
            PaymentResultGateway results,
            PaymentResultService resultService,
            PaymentExecutionService execution,
            PaymentAcceptanceCodec codec,
            ChannelBindingCodec bindingCodec,
            PaymentControlConverter updates,
            Faults faults,
            RemoteChannel remote,
            JdbcTemplate jdbc,
            TestScenario scenario,
            PaymentRecoveryService recovery,
            PaymentRecoveryGateway cursors,
            PaymentRecoveryTaskHandler handler,
            TaskTemplate tasks,
            JdbcTaskRepository taskStore,
            TaskProperties taskProperties,
            ControlledClock clock,
            RecoveryInventoryReader inventory,
            UnknownRecoveryReader unknowns) {
        this.transactions = transactions;
        this.payments = payments;
        this.attempts = attempts;
        this.changes = changes;
        this.bindings = bindings;
        this.results = results;
        this.resultService = resultService;
        this.execution = execution;
        this.codec = codec;
        this.bindingCodec = bindingCodec;
        this.updates = updates;
        this.faults = faults;
        this.remote = remote;
        this.jdbc = jdbc;
        this.scenario = scenario;
        this.recovery = recovery;
        this.cursors = cursors;
        this.handler = handler;
        this.tasks = tasks;
        this.taskStore = taskStore;
        this.taskProperties = taskProperties;
        this.clock = clock;
        this.inventory = inventory;
        this.unknowns = unknowns;
    }

    @BeforeEach
    void resetFaultsAndObservations() {
        clock.reset();
        faults.next.set(null);
        remote.pause.set(null);
        clearInvocations(remote.channels, remote.initial, remote.preparation, remote.actions);
    }

    @AfterEach
    void recoveryNeverObtainsAnotherSubmissionTicketOrCallsSubmit() {
        faults.next.set(null);
        verifyNoInteractions(remote.initial, remote.preparation, remote.actions);
        verify(remote.channels, never()).submit(any());
        verify(remote.channels, never()).validate(any());
    }

    @ParameterizedTest
    @EnumSource(FailurePoint.class)
    void originalChannelSuccessSurvivesLocalFailureAndRecoveryOnlyQueriesTheOriginalAttempt(FailurePoint failure) {
        var fixture = seedSubmitted();
        var original = remote.observations.get(fixture.attempt().attempt().id());
        faults.next.set(failure);
        assertThrows(
                IllegalStateException.class,
                () -> execution.progress(fixture.command().paymentId()));
        assertNull(faults.next.get());
        assertUnsettled(fixture);
        assertCounts(fixture, 0L, 0L, 0L, 0L, 0L);
        assertSame(original, remote.observations.get(fixture.attempt().attempt().id()));

        execution.progress(fixture.command().paymentId());
        assertSettledOnce(fixture);
        var committed = resultService.record(original);
        assertEquals(APPLIED, committed.disposition());
        // The original accepted ACK may be lost; replay returns the durable receipt without another financial effect.
        assertEquals(committed, resultService.record(original));
        execution.progress(fixture.command().paymentId());
        assertSettledOnce(fixture);
        verify(remote.channels, times(2))
                .recover(argThat(request -> original.identity().equals(request.identity())
                        && request.executionEpoch() == 1L
                        && request.method() == fixture.attempt().attempt().method()
                        && request.payableUntil().equals(fixture.command().payableUntil())));
        verify(remote.channels, never()).close(any());
    }

    @Test
    void concurrentOriginalSuccessHasOneClaimCapacityReceiptAndOneDeliveryPerTarget() throws Exception {
        var fixture = seedSubmitted();
        var fact = remote.observations.get(fixture.attempt().attempt().id());
        var ready = new CyclicBarrier(2);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<PaymentResultApplication> record = () -> {
                ready.await(10, TimeUnit.SECONDS);
                return resultService.record(fact);
            };
            var first = workers.submit(record);
            var second = workers.submit(record);
            var a = first.get(20, TimeUnit.SECONDS);
            var b = second.get(20, TimeUnit.SECONDS);
            assertEquals(a, b);
            assertEquals(APPLIED, a.disposition());
        }
        assertSettledOnce(fixture);
    }

    @Test
    void anotherReceiptForTheSameFinancialFactDoesNotCreateAnotherClaimCapacityOrOutbox() {
        var fixture = seedSubmitted();
        var fact = remote.observations.get(fixture.attempt().attempt().id());
        var first = resultService.record(fact);
        var later = new PaymentObservation(
                fact.identity(),
                fact.kind(),
                fact.amount(),
                fact.channelTransactionNo(),
                fact.succeededAt(),
                PaymentObservation.Source.CALLBACK,
                "same-success-callback",
                "e".repeat(64));
        var duplicate = resultService.record(later);
        assertEquals(IGNORED, duplicate.disposition());
        assertNotEquals(first.receiptId(), duplicate.receiptId());
        assertEquals(first.payment(), duplicate.payment());
        assertEquals(duplicate, resultService.record(later));
        assertSuccessfulState(fixture);
        assertCounts(fixture, 1L, 1L, 2L, 0L, 4L);
        assertTargetCounts(fixture);
    }

    @Test
    void changedFactsForAnExistingReceiptAreQuarantinedWithoutChangingMoneyOrRepublishing() {
        var fixture = seedSubmitted();
        var fact = remote.observations.get(fixture.attempt().attempt().id());
        var applied = resultService.record(fact);
        var changed = new PaymentObservation(
                fact.identity(),
                fact.kind(),
                fact.amount(),
                id("conflicting-transaction"),
                fact.succeededAt(),
                fact.source(),
                fact.receiptKey(),
                "f".repeat(64));
        var conflict = resultService.record(changed);
        assertEquals(CONFLICT, conflict.disposition());
        assertEquals(applied.receiptId(), conflict.receiptId());
        assertEquals(applied.payment(), conflict.payment());
        assertEquals(conflict, resultService.record(changed));
        assertSuccessfulState(fixture);
        assertCounts(fixture, 1L, 1L, 1L, 1L, 4L);
        assertTargetCounts(fixture);
        transactions.readSnapshot(() -> {
            scenario.assertDb("payment_result_anomaly")
                    .where("receipt_id", applied.receiptId())
                    .exists()
                    .has("reason_code", "RECEIPT_FACTS_CONFLICT");
            return true;
        });
    }

    @Test
    void theSameCollectingAccountTransactionCannotSettleAnotherTenantsPayment() {
        var winner = seedSubmitted();
        var other = seedSubmitted();
        assertNotEquals(winner.tenant(), other.tenant());
        var fact = remote.observations.get(winner.attempt().attempt().id());
        var otherFact = remote.observations.get(other.attempt().attempt().id());
        assertEquals(fact.identity().accountScopeHash(), otherFact.identity().accountScopeHash());
        resultService.record(fact);
        var alias = new PaymentObservation(
                otherFact.identity(),
                fact.kind(),
                otherFact.amount(),
                fact.channelTransactionNo(),
                fact.succeededAt(),
                otherFact.source(),
                otherFact.receiptKey(),
                otherFact.sourceDigest());
        var rejected = resultService.record(alias);
        assertEquals(CONFLICT, rejected.disposition());
        assertEquals(rejected, resultService.record(alias));
        assertSettledOnce(winner);
        assertUnsettled(other);
        assertCounts(other, 0L, 0L, 1L, 1L, 0L);
        transactions.readSnapshot(() -> {
            scenario.assertDb("payment_result_anomaly")
                    .where("receipt_id", rejected.receiptId())
                    .exists()
                    .has("reason_code", "CHANNEL_TRANSACTION_ALREADY_CLAIMED");
            return true;
        });
    }

    @Test
    void authoritativeCloseResponseCanCommitItsReceiptAndTerminalOutboxWithoutCreatingCapacity() {
        var fixture = seedSubmitted();
        transactions.writeLocal(() -> {
            var parent = payments.lockParent(fixture.command().orderId()).orElseThrow();
            changes.updateParent(updates.withGate(parent, parent.gate().fence(1L)), parent.rowVersion());
            return true;
        });
        var submitted = fixture.attempt().attempt();
        var closed = new PaymentObservation(
                submitted.identity(),
                ChannelPaymentOutcome.Kind.NO_COLLECTION,
                submitted.amount(),
                null,
                null,
                PaymentObservation.Source.CLOSE_RESPONSE,
                "authoritative-close",
                "a".repeat(64));
        remote.observations.put(submitted.id(), closed);
        execution.progress(fixture.command().paymentId());
        execution.progress(fixture.command().paymentId());
        transactions.readSnapshot(() -> {
            var payment = payments.find(fixture.command().paymentId()).orElseThrow();
            assertEquals(PaymentState.CLOSED, payment.payment().state());
            assertEquals(3L, payment.payment().version());
            var attempt = attempts.find(submitted.id()).orElseThrow();
            assertEquals(PaymentAttemptState.FAILED, attempt.attempt().state());
            assertEquals(1L, attempt.attempt().executionEpoch());
            var receipt = results.receipt(submitted.id(), closed.source(), closed.receiptKey())
                    .orElseThrow();
            assertEquals(APPLIED, receipt.disposition());
            assertEquals(payment.terminalEvidenceId(), receipt.id());
            scenario.assertDb("payment_gate")
                    .where("payment_id", fixture.command().paymentId())
                    .exists()
                    .has("state", "FENCED");
            return true;
        });
        assertCounts(fixture, 0L, 0L, 1L, 0L, 5L);
        assertTargetCounts(fixture, 2L);
        verify(remote.channels, times(1))
                .close(argThat(target -> submitted.identity().equals(target.identity())));
        verify(remote.channels, never()).recover(any());
    }

    @Test
    void acknowledgedCloseSurvivesY3RollbackAndRecoveryDoesNotCallTheChannelAgain() {
        var fixture = seedSubmitted();
        transactions.writeLocal(() -> {
            var parent = payments.lockParent(fixture.command().orderId()).orElseThrow();
            changes.updateParent(updates.withGate(parent, parent.gate().fence(1L)), parent.rowVersion());
            return true;
        });
        var submitted = fixture.attempt().attempt();
        var closed = new PaymentObservation(
                submitted.identity(),
                ChannelPaymentOutcome.Kind.NO_COLLECTION,
                submitted.amount(),
                null,
                null,
                PaymentObservation.Source.CLOSE_RESPONSE,
                "durable-authoritative-close",
                "a".repeat(64));
        remote.observations.put(submitted.id(), closed);
        faults.next.set(FailurePoint.AFTER_RECEIPT);
        assertThrows(
                IllegalStateException.class,
                () -> execution.progress(fixture.command().paymentId()));
        transactions.readSnapshot(() -> {
            assertEquals(
                    PaymentState.CLOSING,
                    payments.find(fixture.command().paymentId())
                            .orElseThrow()
                            .payment()
                            .state());
            assertEquals(
                    PaymentAttemptState.SUBMITTING,
                    attempts.find(submitted.id()).orElseThrow().attempt().state());
            assertEquals(1L, count("payment_close_evidence", fixture));
            assertEquals(0L, count("payment_channel_receipt", fixture));
            assertEquals(0L, count("payment_refund_capacity", fixture));
            return true;
        });
        // The provider can now return ambiguous CLOSED/UNKNOWN. Recovery must consume the earlier acknowledged proof.
        remote.observations.put(
                submitted.id(),
                new PaymentObservation(
                        submitted.identity(),
                        ChannelPaymentOutcome.Kind.UNKNOWN,
                        submitted.amount(),
                        null,
                        null,
                        PaymentObservation.Source.QUERY_RESPONSE,
                        "ambiguous-after-close",
                        "b".repeat(64)));
        execution.progress(fixture.command().paymentId());
        execution.progress(fixture.command().paymentId());
        transactions.readSnapshot(() -> {
            var payment = payments.find(fixture.command().paymentId()).orElseThrow();
            assertEquals(PaymentState.CLOSED, payment.payment().state());
            var receipt = results.receipt(submitted.id(), closed.source(), closed.receiptKey())
                    .orElseThrow();
            assertEquals(APPLIED, receipt.disposition());
            assertEquals(payment.terminalEvidenceId(), receipt.id());
            assertEquals(1L, count("payment_close_evidence", fixture));
            return true;
        });
        assertCounts(fixture, 0L, 0L, 1L, 0L, 5L);
        verify(remote.channels, times(1))
                .close(argThat(target -> submitted.identity().equals(target.identity())));
        verify(remote.channels, never()).recover(any());
    }

    @Test
    void expiredAndReclaimedTaskSnapshotsCannotSkipTheCurrentRecoveryOrObtainSubmissionRights() {
        var fixture = seedSubmitted(true);
        try {
            var expired = claim(fixture, 0L);
            clock.advance(Duration.ofSeconds(6));
            var noLease = assertThrows(CommonException.class, () -> handle(fixture, expired));
            assertEquals(INVALID_STATE, noLease.getResultCode());
            verifyNoInteractions(remote.channels);

            var current = claim(fixture, 0L);
            var staleClaim = assertThrows(CommonException.class, () -> handle(fixture, expired));
            assertEquals(INVALID_STATE, staleClaim.getResultCode());
            verifyNoInteractions(remote.channels);
            handle(fixture, current);
            assertTrue(taskStore.complete(current));
            assertFalse(taskStore.complete(expired));
            assertSettledOnce(fixture);
            assertCursor(fixture, 0L, true);
            assertNativeCount(fixture, 0L);
            assertTrue(transactions.readSnapshot(() -> tasks.find(PaymentRecoveryService.TYPE, recoveryKey(fixture, 0L))
                    .isEmpty()));
            verify(remote.channels, times(1)).recover(any());
        } finally {
            cancelRemainingTasks(fixture);
        }
    }

    @Test
    void oldInflightWorkerCannotOverwriteTheNewGenerationOrAcknowledgeItsExpiredClaim() throws Exception {
        var fixture = seedSubmitted(true);
        var original = remote.observations.get(fixture.attempt().attempt().id());
        var unknown = new PaymentObservation(
                original.identity(),
                ChannelPaymentOutcome.Kind.UNKNOWN,
                original.amount(),
                null,
                null,
                PaymentObservation.Source.QUERY_RESPONSE,
                "original-unknown",
                "b".repeat(64));
        remote.observations.put(original.identity().attemptId(), unknown);
        var paused = new PausedQuery();
        remote.pause.set(paused);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var oldClaim = claim(fixture, 0L);
            var oldWorker = workers.submit(() -> handle(fixture, oldClaim));
            try {
                assertTrue(paused.entered.await(10, TimeUnit.SECONDS));
                clock.advance(Duration.ofSeconds(6));
                var replacement = claim(fixture, 0L);
                handle(fixture, replacement);
                assertTrue(taskStore.complete(replacement));
                assertCursor(fixture, 1L, false);
                assertNativeCount(fixture, 1L);
                assertCounts(fixture, 0L, 0L, 1L, 0L, 1L);
                assertEquals(TaskStatus.PENDING, nativeTask(fixture, 1L).status());

                // The current generation can finish while an expired generation-zero query is still in flight.
                remote.observations.put(original.identity().attemptId(), original);
                clock.advance(Duration.ofSeconds(30));
                var successor = claim(fixture, 1L);
                handle(fixture, successor);
                assertTrue(taskStore.complete(successor));
                assertCursor(fixture, 1L, true);
            } finally {
                paused.resume.countDown();
            }
            oldWorker.get(20, TimeUnit.SECONDS);
            assertFalse(taskStore.complete(oldClaim));
            assertSuccessfulState(fixture, 3L, 3L);
            assertCounts(fixture, 1L, 1L, 2L, 0L, 5L);
            assertTargetCounts(fixture, 2L);
            assertCursor(fixture, 1L, true);
            assertNativeCount(fixture, 0L);
            assertTrue(transactions.readSnapshot(() -> tasks.find(PaymentRecoveryService.TYPE, recoveryKey(fixture, 0L))
                    .isEmpty()));
            assertTrue(transactions.readSnapshot(() -> tasks.find(PaymentRecoveryService.TYPE, recoveryKey(fixture, 1L))
                    .isEmpty()));
            transactions.readSnapshot(() -> {
                assertTrue(tasks.find(PaymentRecoveryService.TYPE, recoveryKey(fixture, 2L))
                        .isEmpty());
                return true;
            });
            verify(remote.channels, times(3)).recover(any());
        } finally {
            paused.resume.countDown();
            remote.pause.set(null);
            cancelRemainingTasks(fixture);
        }
    }

    @Test
    void aFutureGenerationTaskIsRejectedWithoutPreventingTheOriginalCurrentTask() {
        var fixture = seedSubmitted(true);
        try {
            transactions.writeLocal(() -> {
                tasks.submit(TaskSubmission.scheduled(
                        PaymentRecoveryService.TYPE, recoveryKey(fixture, 1L), AT.minusSeconds(1)));
                return true;
            });
            var future = claim(fixture, 1L);
            var rejected = assertThrows(CommonException.class, () -> handle(fixture, future));
            assertEquals(SNAPSHOT_INVALID, rejected.getResultCode());
            assertTrue(taskStore.fail(future, new IllegalStateException("Injected failure")));
            verifyNoInteractions(remote.channels);
            assertCursor(fixture, 0L, false);
            assertUnsettled(fixture);

            var original = claim(fixture, 0L);
            handle(fixture, original);
            assertTrue(taskStore.complete(original));
            assertSettledOnce(fixture);
            assertCursor(fixture, 0L, true);
            verify(remote.channels, times(1)).recover(any());
        } finally {
            cancelRemainingTasks(fixture);
        }
    }

    @Test
    void unknownStateSnapshotAndReceiptRollBackTogetherAndReplaysDoNotDuplicateTheVersion() {
        var fixture = seedSubmitted();
        var original = remote.observations.get(fixture.attempt().attempt().id());
        var unknown = new PaymentObservation(
                original.identity(),
                ChannelPaymentOutcome.Kind.UNKNOWN,
                original.amount(),
                null,
                null,
                PaymentObservation.Source.QUERY_RESPONSE,
                "unknown-with-snapshot",
                "b".repeat(64));
        faults.next.set(FailurePoint.AFTER_OUTBOX);
        assertThrows(IllegalStateException.class, () -> resultService.record(unknown));
        assertUnsettled(fixture);
        assertCounts(fixture, 0L, 0L, 0L, 0L, 0L);
        assertNull(jdbc.queryForObject(
                "SELECT first_unknown_at FROM payment_order WHERE id=?",
                LocalDateTime.class,
                fixture.command().paymentId()));

        var first = resultService.record(unknown);
        assertEquals(first, resultService.record(unknown));
        resultService.record(new PaymentObservation(
                unknown.identity(),
                unknown.kind(),
                unknown.amount(),
                null,
                null,
                unknown.source(),
                "another-same-state-query",
                "c".repeat(64)));
        assertCounts(fixture, 0L, 0L, 2L, 0L, 1L);
        transactions.readSnapshot(() -> {
            var stored = payments.find(fixture.command().paymentId()).orElseThrow();
            assertEquals(PaymentState.UNKNOWN, stored.payment().state());
            assertEquals(2L, stored.payment().version());
            return true;
        });
        resultService.record(original);
        assertSuccessfulState(fixture, 3L, 3L);
        assertCounts(fixture, 1L, 1L, 3L, 0L, 5L);
        assertTargetCounts(fixture, 2L);
    }

    @Test
    void inventoryUsesTheFirstCommittedUnknownTime() {
        var first = seedSubmitted();
        var original = remote.observations.get(first.attempt().attempt().id());
        var firstUnknown = new PaymentObservation(
                original.identity(),
                ChannelPaymentOutcome.Kind.UNKNOWN,
                original.amount(),
                null,
                null,
                PaymentObservation.Source.QUERY_RESPONSE,
                "inventory-unknown",
                "b".repeat(64));
        faults.next.set(FailurePoint.AFTER_RECEIPT);
        assertThrows(IllegalStateException.class, () -> resultService.record(firstUnknown));
        assertUnsettled(first);
        assertNull(jdbc.queryForObject(
                "SELECT first_unknown_at FROM payment_order WHERE id=?",
                LocalDateTime.class,
                first.command().paymentId()));
        clock.advance(Duration.ofMinutes(2));
        var firstUnknownAt = now();
        var attempt = first.attempt().attempt();
        resultService.record(new PaymentObservation(
                attempt.identity(),
                ChannelPaymentOutcome.Kind.UNKNOWN,
                attempt.amount(),
                null,
                null,
                PaymentObservation.Source.QUERY_RESPONSE,
                "inventory-unknown",
                "b".repeat(64)));
        clock.advance(Duration.ofMinutes(1));
        resultService.record(new PaymentObservation(
                firstUnknown.identity(),
                firstUnknown.kind(),
                firstUnknown.amount(),
                null,
                null,
                firstUnknown.source(),
                "inventory-unknown-again",
                "b".repeat(64)));
        transactions.readSnapshot(() -> {
            var cells = inventory.read();
            var payments = cells.stream()
                    .filter(value -> value.kind() == RecoveryInventoryCell.Kind.PAYMENT)
                    .findFirst()
                    .orElseThrow();
            assertEquals(1L, payments.unknownCount());
            assertEquals(10000L, payments.nominalAmountMinor());
            assertEquals(firstUnknownAt, payments.oldestUnknownAt());
            assertEquals(
                    RecoveryInventoryCell.empty(RecoveryInventoryCell.Kind.REFUND),
                    cells.stream()
                            .filter(value -> value.kind() == RecoveryInventoryCell.Kind.REFUND)
                            .findFirst()
                            .orElseThrow());
            return true;
        });
        assertEquals(
                INVALID_STATE,
                assertThrows(CommonException.class, () -> inventory.read()).getResultCode());
        assertEquals(
                INVALID_STATE,
                assertThrows(CommonException.class, () -> transactions.writeLocal(() -> inventory.read()))
                        .getResultCode());
        resultService.record(original);
        assertEquals(
                firstUnknownAt,
                jdbc.queryForObject(
                        "SELECT first_unknown_at FROM payment_order WHERE id=?",
                        LocalDateTime.class,
                        first.command().paymentId()));
        transactions.readSnapshot(() -> {
            assertEquals(
                    RecoveryInventoryCell.empty(RecoveryInventoryCell.Kind.PAYMENT),
                    inventory.read().stream()
                            .filter(value -> value.kind() == RecoveryInventoryCell.Kind.PAYMENT)
                            .findFirst()
                            .orElseThrow());
            return true;
        });
    }

    @Test
    void unknownInvestigationKeepsKeysetBoundariesAndDoesNotBlockFinancialWrites() {
        var first = seedSubmitted(true);
        var generated = FinancialPersistenceFixtures.prepared(codec, bindingCodec);
        var second = persistSubmitted(
                new Fixture(
                        first.tenant(),
                        generated.command(),
                        generated.payment(),
                        generated.attempt(),
                        generated.head(),
                        generated.revision()),
                true);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var fixture : List.of(first, second)) {
                var original =
                        remote.observations.get(fixture.attempt().attempt().id());
                resultService.record(new PaymentObservation(
                        original.identity(),
                        ChannelPaymentOutcome.Kind.UNKNOWN,
                        original.amount(),
                        null,
                        null,
                        PaymentObservation.Source.QUERY_RESPONSE,
                        "list-unknown",
                        "b".repeat(64)));
            }
            transactions.readSnapshot(() -> {
                var query = new UnknownRecoveryWindow(AT, null, null, 1);
                var rows = unknowns.list(PaymentRecoveryKind.COLLECTION, query);
                var expected = List.of(
                                first.command().paymentId(), second.command().paymentId())
                        .stream()
                        .sorted()
                        .toList();
                assertEquals(
                        expected, rows.stream().map(value -> value.recordId()).toList());
                assertEquals(2, rows.size()); // limit + 1, without a total-count query.
                for (var row : rows) {
                    assertEquals(row.recordId(), row.subjectId());
                    assertEquals(AT, row.firstUnknownAt());
                    assertEquals(0L, row.recoveryGeneration());
                    assertFalse(row.recoveryCompleted());
                }
                var cursor = rows.getFirst();
                var following = unknowns.list(
                        PaymentRecoveryKind.COLLECTION,
                        new UnknownRecoveryWindow(AT, cursor.firstUnknownAt(), cursor.recordId(), 1));
                assertEquals(
                        List.of(expected.getLast()),
                        following.stream().map(value -> value.recordId()).toList());
                var last = following.getLast();
                assertTrue(unknowns.list(
                                PaymentRecoveryKind.COLLECTION,
                                new UnknownRecoveryWindow(AT, last.firstUnknownAt(), last.recordId(), 1))
                        .isEmpty());
                assertTrue(unknowns.list(
                                PaymentRecoveryKind.COLLECTION,
                                new UnknownRecoveryWindow(AT.minusNanos(1000), null, null, 10))
                        .isEmpty());
                assertTrue(
                        unknowns.list(PaymentRecoveryKind.REFUND_REQUEST, query).isEmpty());

                var original = remote.observations.get(first.attempt().attempt().id());
                var committed = workers.submit(() -> resultService.record(original));
                assertDoesNotThrow(() -> committed.get(10, TimeUnit.SECONDS));
                // The same RR view stays consistent while the financial transaction commits independently.
                assertEquals(
                        expected,
                        unknowns.list(PaymentRecoveryKind.COLLECTION, query).stream()
                                .map(value -> value.recordId())
                                .toList());
                return true;
            });
            transactions.readSnapshot(() -> {
                assertEquals(
                        List.of(second.command().paymentId()),
                        unknowns
                                .list(PaymentRecoveryKind.COLLECTION, new UnknownRecoveryWindow(AT, null, null, 10))
                                .stream()
                                .map(value -> value.recordId())
                                .toList());
                return true;
            });
            assertEquals(
                    INVALID_STATE,
                    assertThrows(
                                    CommonException.class,
                                    () -> unknowns.list(
                                            PaymentRecoveryKind.COLLECTION,
                                            new UnknownRecoveryWindow(AT, null, null, 10)))
                            .getResultCode());
            assertEquals(
                    INVALID_STATE,
                    assertThrows(
                                    CommonException.class,
                                    () -> transactions.writeLocal(() -> unknowns.list(
                                            PaymentRecoveryKind.COLLECTION,
                                            new UnknownRecoveryWindow(AT, null, null, 10))))
                            .getResultCode());
        } finally {
            for (var fixture : List.of(first, second)) {
                cancelRemainingTasks(fixture);
            }
        }
    }

    private Fixture seedSubmitted() {
        return seedSubmitted(false);
    }

    private Fixture seedSubmitted(boolean registerRecovery) {
        return persistSubmitted(FinancialPersistenceFixtures.prepared(codec, bindingCodec), registerRecovery);
    }

    private Fixture persistSubmitted(Fixture fixture, boolean registerRecovery) {
        transactions.writeLocal(() -> {
            bindings.insertHead(fixture.head());
            bindings.insertRevision(fixture.revision());
            payments.insertParent(new StoredPaymentGate(
                    id("parent"),
                    fixture.command().payeeMerchantId(),
                    "acquire",
                    PaymentExecutionGate.init(fixture.command().orderId(), null).open(),
                    0L));
            payments.insertPaymentGate(new StoredPaymentGate(
                    id("gate"),
                    fixture.command().payeeMerchantId(),
                    "acquire",
                    PaymentExecutionGate.init(
                                    fixture.command().orderId(),
                                    fixture.command().paymentId())
                            .open(),
                    0L));
            if (registerRecovery) {
                var prepared = fixture.payment();
                var initial = new StoredPaymentAcceptance(
                        PaymentOrder.init(
                                prepared.payment().id(),
                                prepared.payment().orderId(),
                                prepared.payment().allocationId(),
                                prepared.payment().payeeMerchantId(),
                                prepared.payment().amount(),
                                prepared.payment().limitOperationId(),
                                prepared.payment().initiatedAt()),
                        prepared.admissionId(),
                        prepared.requestDigest(),
                        prepared.callerServiceId(),
                        prepared.intentPayload(),
                        prepared.payloadHash(),
                        null);
                payments.insert(initial);
                recovery.register(initial);
                changes.updatePayment(prepared, 0L);
            } else {
                payments.insert(fixture.payment());
            }
            attempts.insert(fixture.attempt());
            var submitted = fixture.attempt().beginSubmission(1L, "d".repeat(64), RiskDecision.notConnected(), AT);
            attempts.update(submitted, fixture.attempt().rowVersion());
            return true;
        });
        var attempt = fixture.attempt().attempt();
        var success = new PaymentObservation(
                attempt.identity(),
                ChannelPaymentOutcome.Kind.SUCCEEDED,
                attempt.amount(),
                id("channel-transaction"),
                AT,
                PaymentObservation.Source.QUERY_RESPONSE,
                "original-success",
                "c".repeat(64));
        remote.observations.put(attempt.id(), success);
        remote.originals.put(attempt.id(), fixture);
        return fixture;
    }

    private void assertUnsettled(Fixture fixture) {
        transactions.readSnapshot(() -> {
            assertEquals(
                    fixture.payment(),
                    payments.find(fixture.command().paymentId()).orElseThrow());
            var attempt = attempts.find(fixture.attempt().attempt().id()).orElseThrow();
            assertEquals(PaymentAttemptState.SUBMITTING, attempt.attempt().state());
            assertEquals(1L, attempt.attempt().executionEpoch());
            assertEquals(1L, attempt.rowVersion());
            assertEquals(
                    fixture.attempt().attempt().channelRequestNo(),
                    attempt.attempt().channelRequestNo());
            return true;
        });
    }

    private void assertSettledOnce(Fixture fixture) {
        assertSuccessfulState(fixture);
        assertCounts(fixture, 1L, 1L, 1L, 0L, 4L);
        assertTargetCounts(fixture);
    }

    private void assertSuccessfulState(Fixture fixture) {
        assertSuccessfulState(fixture, 2L, 2L);
    }

    private void assertSuccessfulState(Fixture fixture, long paymentVersion, long attemptVersion) {
        transactions.readSnapshot(() -> {
            var payment = payments.find(fixture.command().paymentId()).orElseThrow();
            assertEquals(PaymentState.SUCCEEDED, payment.payment().state());
            assertEquals(paymentVersion, payment.payment().version());
            var attempt = attempts.find(fixture.attempt().attempt().id()).orElseThrow();
            assertEquals(PaymentAttemptState.SUCCEEDED, attempt.attempt().state());
            assertEquals(1L, attempt.attempt().executionEpoch());
            assertEquals(attemptVersion, attempt.rowVersion());
            assertEquals(
                    fixture.attempt().attempt().channelRequestNo(),
                    attempt.attempt().channelRequestNo());
            var claim = results.attemptClaim(attempt.attempt().id()).orElseThrow();
            assertEquals(payment.terminalEvidenceId(), claim.receiptId());
            assertEquals(attempt.attempt().identity(), claim.identity());
            var capacity = results.capacity(fixture.command().paymentId()).orElseThrow();
            assertEquals(10000L, capacity.succeededAmount().getCent());
            assertEquals(10000L, capacity.availableMinor());
            assertEquals(0L, capacity.refundedMinor());
            assertEquals(0L, capacity.reservedMinor());
            assertEquals(0L, capacity.version());
            scenario.assertDb("payment_order")
                    .where("id", fixture.command().paymentId())
                    .exists()
                    .has("state", "SUCCEEDED")
                    .has("row_version", paymentVersion);
            return true;
        });
    }

    private void assertCounts(
            Fixture fixture, long claims, long capacities, long receipts, long anomalies, long outbox) {
        transactions.readSnapshot(() -> {
            assertEquals(claims, count("channel_transaction_claim", fixture));
            assertEquals(capacities, count("payment_refund_capacity", fixture));
            assertEquals(receipts, count("payment_channel_receipt", fixture));
            assertEquals(anomalies, count("payment_result_anomaly", fixture));
            assertEquals(
                    outbox,
                    jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM loadup_task WHERE payload IS NOT NULL AND payload::jsonb ->> 'aggregateId' = ?",
                                    Long.class,
                                    fixture.command().paymentId())
                            .longValue());
            return true;
        });
    }

    private long count(String table, Fixture fixture) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE payment_id = ?",
                Long.class,
                fixture.command().paymentId());
    }

    private void assertTargetCounts(Fixture fixture) {
        assertTargetCounts(fixture, 1L);
    }

    private void assertTargetCounts(Fixture fixture, long realtimeSnapshots) {
        transactions.readSnapshot(() -> {
            for (String type : List.of(
                    PaymentDeliveryRoutes.ACQUIRE,
                    PaymentDeliveryRoutes.LIMIT,
                    PaymentDeliveryRoutes.RISK,
                    PaymentDeliveryRoutes.REALTIME)) {
                assertEquals(
                        type.equals(PaymentDeliveryRoutes.REALTIME) ? realtimeSnapshots : 1L,
                        jdbc.queryForObject(
                                        "SELECT COUNT(*) FROM loadup_task"
                                                + " WHERE payload IS NOT NULL AND payload::jsonb ->> 'aggregateId' = ? AND task_type = ?",
                                        Long.class,
                                        fixture.command().paymentId(),
                                        type)
                                .longValue());
            }
            return true;
        });
    }

    private JdbcTaskRepository.Claim claim(Fixture fixture, long generation) {
        var claimed = taskStore.claim(List.of(PaymentRecoveryService.TYPE)).orElseThrow();
        assertEquals(PaymentRecoveryService.TYPE, claimed.event().taskType());
        assertEquals(recoveryKey(fixture, generation), claimed.event().bizId());
        return claimed;
    }

    private void handle(Fixture fixture, JdbcTaskRepository.Claim claimed) {
        transactions.guarded(() -> {
            handler.handle(claimed.event());
            return true;
        });
    }

    private TaskExecution nativeTask(Fixture fixture, long generation) {
        return transactions.readSnapshot(() -> tasks.find(PaymentRecoveryService.TYPE, recoveryKey(fixture, generation))
                .orElseThrow());
    }

    private void assertCursor(Fixture fixture, long generation, boolean completed) {
        transactions.readSnapshot(() -> {
            var cursor = cursors.find(fixture.command().paymentId()).orElseThrow();
            assertEquals(generation, cursor.generation());
            assertEquals(completed, cursor.completed());
            assertEquals(
                    cursor.dueAt(),
                    tasks.find(PaymentRecoveryService.TYPE, recoveryKey(fixture, generation))
                            .orElseThrow()
                            .nextExecuteAt());
            return true;
        });
    }

    private void assertNativeCount(Fixture fixture, long expected) {
        transactions.readSnapshot(() -> {
            assertEquals(
                    expected,
                    jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM loadup_task WHERE task_type = ? AND biz_id LIKE ?",
                                    Long.class,
                                    PaymentRecoveryService.TYPE,
                                    fixture.command().paymentId() + ".%")
                            .longValue());
            return true;
        });
    }

    private void cancelRemainingTasks(Fixture fixture) {
        transactions.writeLocal(() -> {
            for (long generation = 0; generation <= 2; generation++) {
                tasks.find(PaymentRecoveryService.TYPE, recoveryKey(fixture, generation))
                        .ifPresent(task -> tasks.cancel(task.id()));
            }
            return true;
        });
    }

    private String recoveryKey(Fixture fixture, long generation) {
        return new PaymentRecoveryKey(fixture.command().paymentId(), generation).bizId();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    enum FailurePoint {
        AFTER_CLAIM,
        AFTER_CAPACITY,
        AFTER_SUCCESS_STATE,
        AFTER_RECEIPT,
        AFTER_OUTBOX
    }

    /**
     * Virtual business/native time, independent of persistence audit timestamps and the machine clock.
     */
    static class ControlledClock extends Clock {
        private final AtomicReference<Instant> instant;
        private final ZoneId zone;

        ControlledClock() {
            this(new AtomicReference<>(), ZoneId.systemDefault());
            reset();
        }

        private ControlledClock(AtomicReference<Instant> instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void reset() {
            instant.set(AT.atZone(zone).toInstant());
        }

        void advance(Duration duration) {
            instant.updateAndGet(value -> value.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId target) {
            return new ControlledClock(instant, target);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }

    static class PausedQuery {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch resume = new CountDownLatch(1);

        void waitForRelease() throws InterruptedException {
            entered.countDown();
            assertTrue(resume.await(20, TimeUnit.SECONDS));
        }
    }

    static class Faults {
        final AtomicReference<FailurePoint> next = new AtomicReference<>();

        void after(FailurePoint point) {
            if (next.compareAndSet(point, null)) {
                throw new IllegalStateException("Injected after " + point);
            }
        }
    }

    static class FaultingResults extends PaymentResultGatewayImpl {
        private final Faults faults;

        FaultingResults(
                NamedParameterJdbcTemplate jdbc,
                PaymentResultStorageConverter converter,
                Clock databaseClock,
                Faults faults) {
            super(jdbc, converter, databaseClock);
            this.faults = faults;
        }

        @Override
        public void insertClaim(ChannelTransactionClaim claim) {
            super.insertClaim(claim);
            faults.after(FailurePoint.AFTER_CLAIM);
        }

        @Override
        public void insertCapacity(String id, PaymentRefundCapacity capacity) {
            super.insertCapacity(id, capacity);
            faults.after(FailurePoint.AFTER_CAPACITY);
        }

        @Override
        public void completeSuccess(StoredPaymentAcceptance payment, long expectedVersion, LocalDateTime succeededAt) {
            super.completeSuccess(payment, expectedVersion, succeededAt);
            faults.after(FailurePoint.AFTER_SUCCESS_STATE);
        }

        @Override
        public void insertReceipt(PaymentChannelReceipt receipt) {
            super.insertReceipt(receipt);
            faults.after(FailurePoint.AFTER_RECEIPT);
        }
    }

    static class FaultingEvents extends PaymentEvents {
        private final Faults faults;

        FaultingEvents(
                TaskTemplate outbox,
                PaymentResultConverter converter,
                IdGenerator ids,
                PaymentAttemptGateway attempts,
                PaymentResultGateway results,
                PaymentRealtimeConverter snapshots,
                Faults faults) {
            super(outbox, converter, ids, attempts, results, snapshots, PaymentFactRoutes.defaults());
            this.faults = faults;
        }

        public void publish(StoredPaymentAcceptance payment, boolean fenced, LocalDateTime occurredAt) {
            super.publish(payment, fenced, occurredAt);
            faults.after(FailurePoint.AFTER_OUTBOX);
        }

        public void publishSnapshot(StoredPaymentAcceptance payment, LocalDateTime occurredAt) {
            super.publishSnapshot(payment, occurredAt);
            faults.after(FailurePoint.AFTER_OUTBOX);
        }
    }

    /**
     * Only authenticated external facts and unreachable first-submit/action branches are test doubles.
     */
    static class RemoteChannel {
        final PaymentChannelGateway channels = mock(PaymentChannelGateway.class);
        final PaymentInitialAttemptService initial = mock(PaymentInitialAttemptService.class);
        final PaymentSubmissionPreparation preparation = mock(PaymentSubmissionPreparation.class);
        final PaymentActionService actions = mock(PaymentActionService.class);
        final Map<String, PaymentObservation> observations = new ConcurrentHashMap<>();
        final Map<String, Fixture> originals = new ConcurrentHashMap<>();
        final AtomicReference<PausedQuery> pause = new AtomicReference<>();

        RemoteChannel() {
            when(channels.recover(any())).thenAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                ChannelPaymentRecovery request = call.getArgument(0);
                var fixture = originals.get(request.identity().attemptId());
                assertEquals(fixture.attempt().attempt().identity(), request.identity());
                assertEquals(fixture.attempt().attempt().amount(), request.amount());
                assertEquals(fixture.attempt().attempt().method(), request.method());
                assertEquals(1L, request.executionEpoch());
                assertEquals(fixture.command().payableUntil(), request.payableUntil());
                var observation = observations.get(request.identity().attemptId());
                var blocked = pause.getAndSet(null);
                if (blocked != null) {
                    blocked.waitForRelease();
                }
                return new ChannelPaymentSubmission(request.identity(), request.amount(), observation, null);
            });
            when(channels.close(any())).thenAnswer(call -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                ChannelPaymentTarget target = call.getArgument(0);
                var original = originals.get(target.identity().attemptId());
                assertEquals(original.attempt().attempt().identity(), target.identity());
                assertEquals(original.attempt().attempt().amount(), target.amount());
                return observations.get(target.identity().attemptId());
            });
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = PersistenceExceptionTranslationAutoConfiguration.class)
    @ComponentScan(basePackageClasses = {PaymentResultStorageConverter.class, PaymentAcceptanceConverter.class})
    @Import({
        PaymentAcceptanceGatewayImpl.class,
        PaymentControlGatewayImpl.class,
        PaymentAttemptGatewayImpl.class,
        ChannelBindingGatewayImpl.class,
        PaymentAcceptanceCodecImpl.class,
        ChannelBindingCodecImpl.class,
        PaymentResultCodecImpl.class,
        PaymentExecutionLocks.class,
        PaymentAcceptanceTransactions.class,
        PaymentResultService.class,
        PaymentExecutionService.class,
        PaymentCloseService.class,
        PaymentCloseEvidenceGatewayImpl.class,
        PaymentRecoveryGatewayImpl.class,
        PaymentRecoveryService.class,
        PaymentRecoveryTaskHandler.class,
        RecoveryPollingConfiguration.class,
        RecoveryExecutionMetrics.class,
        RecoveryInventoryReaderImpl.class,
        UnknownRecoveryReaderImpl.class
    })
    static class Application {
        @Bean({"paymentClock", "delayedTaskClock"})
        ControlledClock paymentClock() {
            return new ControlledClock();
        }

        @Bean
        Faults faults() {
            return new Faults();
        }

        @Bean
        RemoteChannel remoteChannel() {
            return new RemoteChannel();
        }

        @Bean
        PaymentChannelGateway channels(RemoteChannel remote) {
            return remote.channels;
        }

        @Bean
        PaymentInitialAttemptService initial(RemoteChannel remote) {
            return remote.initial;
        }

        @Bean
        PaymentSubmissionPreparation preparation(RemoteChannel remote) {
            return remote.preparation;
        }

        @Bean
        PaymentActionService actions(RemoteChannel remote) {
            return remote.actions;
        }

        @Bean
        FaultingResults results(
                NamedParameterJdbcTemplate jdbc,
                PaymentResultStorageConverter converter,
                Clock databaseClock,
                Faults faults) {
            return new FaultingResults(jdbc, converter, databaseClock, faults);
        }

        @Bean
        PaymentEvents events(
                TaskTemplate outbox,
                PaymentResultConverter converter,
                IdGenerator ids,
                PaymentAttemptGateway attempts,
                PaymentResultGateway results,
                PaymentRealtimeConverter snapshots,
                Faults faults) {
            return new FaultingEvents(outbox, converter, ids, attempts, results, snapshots, faults);
        }
    }
}
