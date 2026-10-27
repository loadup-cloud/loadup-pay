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
package io.github.loadup.pay.payment.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.channel.api.model.*;
import io.github.loadup.pay.payment.app.converter.PaymentControlConverter;
import io.github.loadup.pay.payment.app.converter.PaymentSubmissionConverter;
import io.github.loadup.pay.payment.app.model.LockedPayment;
import io.github.loadup.pay.payment.app.model.PaymentResultApplication;
import io.github.loadup.pay.payment.app.model.PaymentSubmissionTicket;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.domain.gateway.*;
import io.github.loadup.pay.payment.domain.model.*;
import io.github.loadup.components.database.id.IdGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.domain.model.PaymentReceiptDisposition.APPLIED;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentExecutionServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(PaymentControlConverter.class.getPackageName());
    final PaymentAcceptanceGateway payments = mock(PaymentAcceptanceGateway.class);
    final PaymentAttemptGateway attempts = mock(PaymentAttemptGateway.class);
    final PaymentControlGateway changes = mock(PaymentControlGateway.class);
    final PaymentExecutionLocks locks = mock(PaymentExecutionLocks.class);
    final PaymentInitialAttemptService initial = mock(PaymentInitialAttemptService.class);
    final PaymentSubmissionPreparation submission = mock(PaymentSubmissionPreparation.class);
    final PaymentChannelGateway channels = mock(PaymentChannelGateway.class);
    final PaymentResultService results = mock(PaymentResultService.class);
    final PaymentAcceptanceCodec codec = mock(PaymentAcceptanceCodec.class);
    final PaymentResultEvents events = mock(PaymentResultEvents.class);
    final PaymentAcceptanceServiceTest.UnitTransactions manager = new PaymentAcceptanceServiceTest.UnitTransactions();
    final PaymentAcceptanceTransactions transactions = new PaymentAcceptanceTransactions(manager);
    final AtomicReference<StoredPaymentAcceptance> accepted = new AtomicReference<>(AttemptFixtures.accepted(true));
    final AtomicReference<StoredPaymentAttempt> stored = new AtomicReference<>(AttemptFixtures.prepared());
    final IdGenerator ids = mock(IdGenerator.class);
    final Clock clock = Clock.fixed(AttemptFixtures.NOW.atZone(BusinessTime.zone()).toInstant(), BusinessTime.zone());
    final PaymentExecutionService service = new PaymentExecutionService(payments, attempts, changes, locks, initial,
            submission, channels, results, codec, context.getBean(PaymentControlConverter.class), events,
            transactions, ids, clock, context.getBean(PaymentSubmissionConverter.class));
    final PaymentSubmissionConverter conversions = context.getBean(PaymentSubmissionConverter.class);
    final PreparedChannelPayment prepared = new PreparedChannelPayment(stored.get().attempt().identity(),
            stored.get().attempt().amount(), stored.get().attempt().method(), 1, stored.get().preparedInputRef());

    @BeforeEach
    void setup() {
        when(payments.find("tenant", "payment")).thenAnswer(c -> Optional.of(accepted.get()));
        when(locks.lock("tenant", "payment", "acquire")).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return PaymentExecutionBindings.locked(accepted.get(), AttemptFixtures.gate(true), AttemptFixtures.gate(false));
        });
        when(attempts.lock("tenant", "attempt")).thenAnswer(c -> Optional.of(stored.get()));
        when(codec.decode(any())).thenReturn(AttemptFixtures.command());
        when(codec.checksum(anyString())).thenReturn("a".repeat(64));
    }

    @AfterEach
    void close() { context.close(); }

    @Test
    void initWinnerSubmitsOnlyReturnedTicketAndRecordsOutsideLocalTransaction() {
        var ticket = new PaymentSubmissionTicket("tenant", "payment", "attempt", prepared);
        when(submission.prepare("tenant", "payment", "attempt")).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return Optional.of(ticket);
        });
        when(channels.submit(prepared)).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return submitted();
        });
        when(results.recordSubmission(eq("tenant"), eq(prepared), any())).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return result();
        });

        service.progress("tenant", "payment");

        var calls = inOrder(submission, channels, results);
        calls.verify(submission).prepare("tenant", "payment", "attempt");
        calls.verify(channels).submit(prepared);
        calls.verify(results).recordSubmission(eq("tenant"), eq(prepared), any());
        verifyNoInteractions(initial);
    }

    @Test
    void acceptedPaymentWithoutAttemptCreatesInitialAttemptUnderLockBeforeTakingTicket() {
        var before = AttemptFixtures.accepted(false);
        accepted.set(before);
        when(initial.create(eq("tenant"), eq(before), anyString(), anyString())).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            var created = AttemptFixtures.accepted(true);
            accepted.set(created);
            stored.set(AttemptFixtures.prepared());
            return created;
        });
        when(submission.prepare("tenant", "payment", "attempt")).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return Optional.of(new PaymentSubmissionTicket("tenant", "payment", "attempt", prepared));
        });
        when(channels.submit(prepared)).thenReturn(submitted());
        when(results.recordSubmission(eq("tenant"), eq(prepared), any())).thenReturn(result());

        service.progress("tenant", "payment");

        assertEquals("attempt", accepted.get().payment().activeAttemptId());
        assertEquals(PaymentAttemptState.INIT, stored.get().attempt().state());
        var calls = inOrder(initial, submission, channels, results);
        calls.verify(initial).create(eq("tenant"), eq(before), anyString(), anyString());
        calls.verify(submission).prepare("tenant", "payment", "attempt");
        calls.verify(channels).submit(prepared);
        calls.verify(results).recordSubmission(eq("tenant"), eq(prepared), any());
    }

    @Test
    void startedAttemptsRecoverTheirOriginalFrozenExecutionAndNeverAcquireAnotherTicket() {
        for (var state : new PaymentAttemptState[] {PaymentAttemptState.SUBMITTING, PaymentAttemptState.PENDING, PaymentAttemptState.UNKNOWN}) {
            clearInvocations(channels, submission, results);
            stored.set(started(state));
            var payableUntil = AttemptFixtures.command().payableUntil();
            var recovery = conversions.toRecovery(stored.get(), payableUntil);
            assertEquals(stored.get().attempt().identity(), recovery.identity());
            assertEquals(stored.get().attempt().amount(), recovery.amount());
            assertEquals(stored.get().attempt().method(), recovery.method());
            assertEquals(stored.get().attempt().executionEpoch(), recovery.executionEpoch());
            assertEquals(payableUntil, recovery.payableUntil());
            when(attempts.lock("tenant", "attempt")).thenReturn(Optional.of(stored.get()));
            var recovered = recovered(recovery);
            when(channels.recover(recovery)).thenReturn(recovered);
            when(results.recordRecovery("tenant", recovery, recovered)).thenReturn(result());
            service.progress("tenant", "payment");
            verify(channels).recover(recovery);
            verify(results).recordRecovery("tenant", recovery, recovered);
            verify(channels, never()).query(any());
            verify(submission, never()).prepare(anyString(), anyString(), anyString());
            verify(channels, never()).submit(any());
        }
    }

    @Test
    void closingPaymentUsesTheOriginalTargetCloseAndLeavesUnknownToResultRecording() {
        var original = accepted.get();
        var closing = new StoredPaymentAcceptance(original.payment().requestClose(), original.admissionId(),
                original.requestDigest(), original.callerServiceId(), original.intentPayload(), original.payloadHash(), null);
        accepted.set(closing);
        stored.set(started(PaymentAttemptState.UNKNOWN));
        var target = new ChannelPaymentTarget(stored.get().attempt().identity(), stored.get().attempt().amount());
        var unknown = new PaymentObservation(stored.get().attempt().identity(), ChannelPaymentOutcome.Kind.UNKNOWN,
                stored.get().attempt().amount(), null, null, PaymentObservation.Source.CLOSE_RESPONSE, "close", "f".repeat(64));
        when(channels.close(target)).thenReturn(unknown);
        when(results.record("tenant", unknown)).thenReturn(result());

        service.progress("tenant", "payment");

        verify(channels).close(target);
        verify(channels, never()).query(any());
        verify(channels, never()).recover(any());
        verify(channels, never()).submit(any());
        verify(results).record("tenant", unknown);
        verify(submission, never()).prepare(anyString(), anyString(), anyString());
    }

    @Test
    void terminalPaymentDoesNotTouchChannelOrSubmissionServices() {
        var original = accepted.get();
        var terminal = original.payment().fail(new NoCollectionEvidence("evidence", "payment", true));
        accepted.set(new StoredPaymentAcceptance(terminal, original.admissionId(), original.requestDigest(),
                original.callerServiceId(), original.intentPayload(), original.payloadHash(), "evidence"));

        service.progress("tenant", "payment");

        verifyNoInteractions(channels, submission, results);
        verify(attempts, never()).lock(anyString(), anyString());
    }

    @Test
    void expiredNeverSubmittedPaymentIsFencedLocallyWithoutChannelCallOrCollectionEvent() {
        when(codec.decode(any())).thenReturn(new PaymentFixturesCommandWithExpiredDeadline().command());
        when(ids.generate()).thenReturn("evidence");
        var updateConverter = context.getBean(PaymentControlConverter.class);
        var currentGate = AttemptFixtures.gate(false);
        var fencedGate = updateConverter.withGate(currentGate, currentGate.gate().fence(1));
        doAnswer(c -> { stored.set(c.getArgument(1)); return null; }).when(attempts).update(eq("tenant"), any(), anyLong());
        doAnswer(c -> { accepted.set(c.getArgument(1)); return null; }).when(changes).updatePayment(eq("tenant"), any(), anyLong());

        service.progress("tenant", "payment");

        verify(changes).updateGate("tenant", fencedGate, currentGate.rowVersion());
        verify(attempts).update(eq("tenant"), argThat(value -> value.attempt().state() == PaymentAttemptState.CLOSED), anyLong());
        verify(events).publish(eq("tenant"), any(), eq(true), any());
        verifyNoInteractions(channels);
    }

    @Test
    void submissionFailureCannotTurnTheAlreadyStartedAttemptIntoAnotherTicket() {
        when(submission.prepare("tenant", "payment", "attempt")).thenAnswer(c -> {
            stored.set(started(PaymentAttemptState.SUBMITTING));
            return Optional.of(new PaymentSubmissionTicket("tenant", "payment", "attempt", prepared));
        });
        when(channels.submit(prepared)).thenThrow(new CommonException(DEPENDENCY_UNAVAILABLE));

        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class,
                () -> service.progress("tenant", "payment")).getResultCode());

        var recovery = conversions.toRecovery(stored.get(), AttemptFixtures.command().payableUntil());
        var recovered = recovered(recovery);
        when(channels.recover(recovery)).thenReturn(recovered);
        when(results.recordRecovery("tenant", recovery, recovered)).thenReturn(result());
        service.progress("tenant", "payment");

        verify(submission, times(1)).prepare("tenant", "payment", "attempt");
        verify(channels, times(1)).submit(prepared);
        verify(channels).recover(recovery);
        verify(results).recordRecovery("tenant", recovery, recovered);
    }

    private StoredPaymentAttempt started(PaymentAttemptState target) {
        var base = AttemptFixtures.prepared().beginSubmission(1, "d".repeat(64), RiskDecision.notConnected(), AttemptFixtures.NOW);
        if (target == PaymentAttemptState.SUBMITTING) return base;
        var kind = target == PaymentAttemptState.PENDING ? ChannelPaymentOutcome.Kind.ACCEPTED : ChannelPaymentOutcome.Kind.UNKNOWN;
        var outcome = new ChannelPaymentOutcome(kind, base.attempt().identity(), base.attempt().amount(), null);
        return base.applyOutcome(outcome, false);
    }

    private ChannelPaymentSubmission submitted() {
        var observation = new PaymentObservation(prepared.identity(), ChannelPaymentOutcome.Kind.ACCEPTED, prepared.amount(),
                null, null, PaymentObservation.Source.SUBMIT_RESPONSE, "submit", "f".repeat(64));
        return new ChannelPaymentSubmission(prepared.identity(), prepared.amount(), observation, null);
    }

    private ChannelPaymentSubmission recovered(ChannelPaymentRecovery recovery) {
        var observation = new PaymentObservation(recovery.identity(), ChannelPaymentOutcome.Kind.UNKNOWN,
                recovery.amount(), null, null, PaymentObservation.Source.QUERY_RESPONSE, "query", "f".repeat(64));
        return new ChannelPaymentSubmission(recovery.identity(), recovery.amount(), observation, null);
    }

    private PaymentResultApplication result() {
        return new PaymentResultApplication(APPLIED, "receipt", null);
    }

    /** Keeps the deadline fixture local so the test does not alter shared payment fixtures. */
    private static final class PaymentFixturesCommandWithExpiredDeadline {
        io.github.loadup.pay.payment.client.command.PaymentExecuteCommand command() {
            var original = AttemptFixtures.command();
            return new io.github.loadup.pay.payment.client.command.PaymentExecuteCommand(original.paymentId(), original.orderId(),
                    original.allocationId(), original.admissionId(), original.payeeMerchantId(), original.amountMinor(),
                    original.currency(), original.requestDigest(), original.choice(), original.preparedInputRef(), original.admittedTerms(),
                    original.subject(), OffsetDateTime.parse("2026-10-20T00:00:00Z"));
        }
    }
}
