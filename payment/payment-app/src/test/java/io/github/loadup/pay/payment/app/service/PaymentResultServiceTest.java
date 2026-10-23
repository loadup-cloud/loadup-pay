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
import io.github.loadup.pay.common.BusinessTime;
import io.github.loadup.pay.common.DomainChecks;
import io.github.loadup.pay.payment.app.converter.PaymentAcceptanceConverter;
import io.github.loadup.pay.payment.app.converter.PaymentControlConverter;
import io.github.loadup.pay.payment.app.converter.PaymentResultConverter;
import io.github.loadup.pay.payment.app.model.LockedPayment;
import io.github.loadup.pay.payment.app.port.PaymentResultCodec;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentControlGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentResultGateway;
import io.github.loadup.pay.payment.domain.model.ChannelPaymentIdentity;
import io.github.loadup.pay.payment.domain.model.ChannelPaymentOutcome;
import io.github.loadup.pay.payment.domain.model.ChannelTransactionClaim;
import io.github.loadup.pay.payment.domain.model.GateState;
import io.github.loadup.pay.payment.domain.model.PaymentAttemptState;
import io.github.loadup.pay.payment.domain.model.PaymentChannelReceipt;
import io.github.loadup.pay.payment.domain.model.PaymentObservation;
import io.github.loadup.pay.payment.domain.model.PaymentRefundCapacity;
import io.github.loadup.pay.payment.domain.model.PaymentResultAnomaly;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.domain.model.RiskDecision;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import io.github.loadup.pay.payment.domain.model.StoredPaymentGate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.domain.model.PaymentReceiptDisposition.APPLIED;
import static io.github.loadup.pay.payment.domain.model.PaymentReceiptDisposition.CONFLICT;
import static io.github.loadup.pay.payment.domain.model.PaymentReceiptDisposition.IGNORED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentResultServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(PaymentResultConverter.class.getPackageName());
    final PaymentAcceptanceGateway payments = mock(PaymentAcceptanceGateway.class);
    final PaymentControlGateway changes = mock(PaymentControlGateway.class);
    final PaymentAttemptGateway attempts = mock(PaymentAttemptGateway.class);
    final PaymentResultGateway results = mock(PaymentResultGateway.class);
    final PaymentExecutionLocks locks = mock(PaymentExecutionLocks.class);
    final PaymentResultCodec codec = mock(PaymentResultCodec.class);
    final PaymentResultEvents events = mock(PaymentResultEvents.class);
    final AtomicReference<StoredPaymentAcceptance> accepted = new AtomicReference<>(AttemptFixtures.accepted(true));
    final AtomicReference<StoredPaymentAttempt> attempt = new AtomicReference<>(AttemptFixtures.prepared()
            .beginSubmission(1, "e".repeat(64), RiskDecision.notConnected(), AttemptFixtures.NOW));
    final AtomicReference<StoredPaymentGate> gate = new AtomicReference<>(AttemptFixtures.gate(false));
    final AtomicReference<ChannelTransactionClaim> claim = new AtomicReference<>();
    final AtomicReference<PaymentRefundCapacity> capacity = new AtomicReference<>();
    final Map<String, PaymentChannelReceipt> receipts = new HashMap<>();
    final List<PaymentResultAnomaly> anomalies = new ArrayList<>();
    final AtomicInteger ids = new AtomicInteger();
    final PaymentAcceptanceServiceTest.UnitTransactions manager = new PaymentAcceptanceServiceTest.UnitTransactions();
    final PaymentResultService service = new PaymentResultService(payments, changes, attempts, results, locks,
            new PaymentAcceptanceTransactions(manager), codec, context.getBean(PaymentResultConverter.class),
            context.getBean(PaymentControlConverter.class), context.getBean(PaymentAcceptanceConverter.class), events,
            () -> "result-" + ids.incrementAndGet(),
            Clock.fixed(AttemptFixtures.NOW.atZone(BusinessTime.ZONE).toInstant(), BusinessTime.ZONE));

    PaymentObservation observation(ChannelPaymentOutcome.Kind kind, String key, PaymentObservation.Source source) {
        boolean collected = kind == ChannelPaymentOutcome.Kind.SUCCEEDED || kind == ChannelPaymentOutcome.Kind.CLOSED_WITH_FUNDS_RETURNED;
        return new PaymentObservation(attempt.get().attempt().identity(), kind, accepted.get().payment().amount(),
                collected ? "channel-transaction" : null, collected ? AttemptFixtures.NOW : null, source, key, "f".repeat(64));
    }

    PaymentObservation success() {
        return observation(ChannelPaymentOutcome.Kind.SUCCEEDED, "notification", PaymentObservation.Source.CALLBACK);
    }

    void active() {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @BeforeEach
    void setup() {
        when(payments.find("tenant", "payment")).thenAnswer(c -> Optional.of(accepted.get()));
        when(locks.lock("tenant", "payment", "acquire")).thenAnswer(c -> {
            active();
            return new LockedPayment(accepted.get(), AttemptFixtures.gate(true), gate.get());
        });
        when(attempts.lock("tenant", "attempt")).thenAnswer(c -> {
            active();
            return Optional.of(attempt.get());
        });
        when(codec.encode(any())).thenReturn("{\"normalized\":true}");
        when(codec.factsDigest(any())).thenAnswer(c -> {
            PaymentObservation o = c.getArgument(0);
            return "%064x".formatted(Integer.toUnsignedLong(Objects.hash(o.identity(), o.kind(), o.amount(), o.channelTransactionNo(), o.succeededAt())));
        });
        when(results.receipt(eq("tenant"), eq("attempt"), any(), anyString())).thenAnswer(c ->
                Optional.ofNullable(receipts.get(c.getArgument(2) + "/" + c.getArgument(3))));
        when(results.transaction(any(), anyString(), anyString())).thenAnswer(c ->
                Optional.ofNullable(claim.get()).filter(v -> v.channelTransactionNo().equals(c.getArgument(2))));
        when(results.attemptClaim("tenant", "attempt")).thenAnswer(c ->
                Optional.ofNullable(claim.get()).filter(v -> v.tenantId().equals("tenant") && v.identity().attemptId().equals("attempt")));
        when(results.capacity("tenant", "payment")).thenAnswer(c -> Optional.ofNullable(capacity.get()));
        doAnswer(c -> {
            active();
            attempt.set(c.getArgument(1));
            return null;
        }).when(attempts).update(eq("tenant"), any(), anyLong());
        doAnswer(c -> {
            active();
            accepted.set(c.getArgument(1));
            return null;
        }).when(changes).updatePayment(eq("tenant"), any(), anyLong());
        doAnswer(c -> {
            active();
            accepted.set(c.getArgument(1));
            return null;
        }).when(results).completeSuccess(eq("tenant"), any(), anyLong(), any());
        doAnswer(c -> {
            active();
            gate.set(c.getArgument(1));
            return null;
        }).when(changes).updateGate(eq("tenant"), any(), anyLong());
        doAnswer(c -> {
            active();
            claim.set(c.getArgument(0));
            return null;
        }).when(results).insertClaim(any());
        doAnswer(c -> {
            active();
            capacity.set(c.getArgument(2));
            return null;
        }).when(results).insertCapacity(eq("tenant"), anyString(), any());
        doAnswer(c -> {
            active();
            PaymentChannelReceipt r = c.getArgument(1);
            receipts.put(r.source() + "/" + r.receiptKey(), r);
            return null;
        }).when(results).insertReceipt(eq("tenant"), any());
        doAnswer(c -> {
            active();
            anomalies.add(c.getArgument(1));
            return null;
        }).when(results).recordAnomaly(eq("tenant"), any());
        doAnswer(c -> {
            active();
            return null;
        }).when(events).publish(anyString(), any(), anyBoolean(), any());
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void successCommitsClaimAttemptPaymentRefundCapacityReceiptAndTargetEventsTogether() {
        var result = service.record("tenant", success());
        assertEquals(APPLIED, result.disposition());
        assertEquals(PaymentState.SUCCEEDED, accepted.get().payment().state());
        assertEquals(PaymentAttemptState.SUCCEEDED, attempt.get().attempt().state());
        assertEquals(result.receiptId(), accepted.get().terminalEvidenceId());
        assertEquals(result.receiptId(), claim.get().receiptId());
        assertEquals(4000, capacity.get().availableMinor());
        assertEquals(AttemptFixtures.NOW, capacity.get().succeededAt());
        assertEquals(1, manager.commits);
        verify(events).publish(eq("tenant"), eq(accepted.get()), eq(false), any());
    }

    @Test
    void identicalReceiptWithAnotherSignatureDigestDoesNotRepeatFinancialEffects() {
        var original = success();
        var result = service.record("tenant", original);
        clearInvocations(results, changes, attempts, events);
        var rewrapped = new PaymentObservation(original.identity(), original.kind(), original.amount(), original.channelTransactionNo(),
                original.succeededAt(), original.source(), original.receiptKey(), "d".repeat(64));
        assertEquals(result, service.record("tenant", rewrapped));
        verifyNoInteractions(events);
        verify(results, never()).insertClaim(any());
        verify(changes, never()).updatePayment(anyString(), any(), anyLong());
    }

    @Test
    void laterIndependentSuccessReceiptKeepsTheFirstClaimEvidenceAndCapacity() {
        service.record("tenant", success());
        var evidence = accepted.get().terminalEvidenceId();
        var initialCapacity = capacity.get();
        clearInvocations(events);
        var result = service.record("tenant", observation(ChannelPaymentOutcome.Kind.SUCCEEDED, "query-1", PaymentObservation.Source.QUERY_RESPONSE));
        assertEquals(IGNORED, result.disposition());
        assertEquals(evidence, accepted.get().terminalEvidenceId());
        assertSame(initialCapacity, capacity.get());
        verifyNoInteractions(events);
    }

    @Test
    void sameReceiptWithChangedFinancialFactsRetainsAnomalyWithoutRewritingSuccess() {
        var original = success();
        service.record("tenant", original);
        var changed = new PaymentObservation(original.identity(), original.kind(), DomainChecks.amount(3999, "CNY"),
                original.channelTransactionNo(), original.succeededAt(), original.source(), original.receiptKey(), original.sourceDigest());
        assertEquals(CONFLICT, service.record("tenant", changed).disposition());
        assertEquals("RECEIPT_FACTS_CONFLICT", anomalies.getFirst().reasonCode());
        assertEquals(4000, accepted.get().payment().amount().getCent());
        assertEquals(1, receipts.size());
    }

    @Test
    void mismatchedAmountIsEvidenceOnlyAndDoesNotInitializeRefundCapacity() {
        var o = success();
        var wrong = new PaymentObservation(o.identity(), o.kind(), DomainChecks.amount(3999, "CNY"), o.channelTransactionNo(),
                o.succeededAt(), o.source(), o.receiptKey(), o.sourceDigest());
        assertEquals(CONFLICT, service.record("tenant", wrong).disposition());
        assertNull(claim.get());
        assertNull(capacity.get());
        assertEquals("PAYMENT_AMOUNT_MISMATCH", anomalies.getFirst().reasonCode());
        verifyNoInteractions(events);
    }

    @Test
    void mismatchedStableRequestNumberCannotConfirmTheExpectedPayment() {
        var o = success();
        var i = o.identity();
        var identity = new ChannelPaymentIdentity(i.paymentId(), i.attemptId(), i.channel(), i.bindingRevisionId(), i.accountScopeHash(), "other-request");
        var wrong = new PaymentObservation(identity, o.kind(), o.amount(), o.channelTransactionNo(), o.succeededAt(), o.source(), o.receiptKey(), o.sourceDigest());
        assertEquals(CONFLICT, service.record("tenant", wrong).disposition());
        assertEquals("PAYMENT_IDENTITY_MISMATCH", anomalies.getFirst().reasonCode());
        assertNull(claim.get());
    }

    @Test
    void observationBeforeY2CannotInventAnExternalSubmission() {
        attempt.set(AttemptFixtures.prepared());
        assertEquals(CONFLICT, service.record("tenant", success()).disposition());
        assertEquals("OBSERVATION_BEFORE_SUBMISSION", anomalies.getFirst().reasonCode());
        assertNull(claim.get());
    }

    @Test
    void unknownRemainsInFlightAndAcceptedCannotUndoThatUncertainty() {
        service.record("tenant", observation(ChannelPaymentOutcome.Kind.UNKNOWN, "query-1", PaymentObservation.Source.QUERY_RESPONSE));
        assertEquals(PaymentState.UNKNOWN, accepted.get().payment().state());
        var result = service.record("tenant", observation(ChannelPaymentOutcome.Kind.ACCEPTED, "query-2", PaymentObservation.Source.QUERY_RESPONSE));
        assertEquals(IGNORED, result.disposition());
        assertEquals(PaymentAttemptState.UNKNOWN, attempt.get().attempt().state());
        assertNull(capacity.get());
        verifyNoInteractions(events);
    }

    @Test
    void verifiedCollectionDuringClosingStillRecordsTheFundsForDownstreamCompensation() {
        accepted.set(context.getBean(PaymentControlConverter.class).withPayment(accepted.get(), accepted.get().payment().requestClose(), null));
        gate.set(context.getBean(PaymentControlConverter.class).withGate(gate.get(), gate.get().gate().fence(1)));
        service.record("tenant", success());
        assertEquals(PaymentState.SUCCEEDED, accepted.get().payment().state());
        verify(events).publish(eq("tenant"), any(), eq(true), any());
    }

    @Test
    void definitiveNoCollectionFencesExecutionAndFailsPaymentWithoutRefundCapacity() {
        var result = service.record("tenant", observation(ChannelPaymentOutcome.Kind.NO_COLLECTION, "query-1", PaymentObservation.Source.QUERY_RESPONSE));
        assertEquals(APPLIED, result.disposition());
        assertEquals(PaymentState.FAILED, accepted.get().payment().state());
        assertEquals(PaymentAttemptState.FAILED, attempt.get().attempt().state());
        assertEquals(GateState.FENCED, gate.get().gate().state());
        assertEquals(result.receiptId(), accepted.get().terminalEvidenceId());
        assertNull(capacity.get());
        verify(events).publish(eq("tenant"), any(), eq(true), any());
    }

    @Test
    void definitiveNoCollectionCompletesAnExistingCloseRequest() {
        accepted.set(context.getBean(PaymentControlConverter.class).withPayment(accepted.get(), accepted.get().payment().requestClose(), null));
        service.record("tenant", observation(ChannelPaymentOutcome.Kind.NO_COLLECTION, "query-1", PaymentObservation.Source.QUERY_RESPONSE));
        assertEquals(PaymentState.CLOSED, accepted.get().payment().state());
    }

    @Test
    void negativeTerminalObservationCannotEraseACommittedSuccess() {
        service.record("tenant", success());
        clearInvocations(events);
        assertEquals(CONFLICT, service.record("tenant", observation(ChannelPaymentOutcome.Kind.NO_COLLECTION, "query-1", PaymentObservation.Source.QUERY_RESPONSE)).disposition());
        assertEquals(PaymentState.SUCCEEDED, accepted.get().payment().state());
        verifyNoInteractions(events);
    }

    @Test
    void channelTransactionClaimConflictsAcrossTenantAliasesWithoutReturningTheOtherOwner() {
        claim.set(new ChannelTransactionClaim("other-claim", "other-tenant", attempt.get().attempt().identity(),
                accepted.get().payment().amount(), "channel-transaction", AttemptFixtures.NOW, "other-evidence"));
        var result = service.record("tenant", success());
        assertEquals(CONFLICT, result.disposition());
        assertEquals("CHANNEL_TRANSACTION_ALREADY_CLAIMED", anomalies.getFirst().reasonCode());
        assertFalse(result.toString().contains("other-tenant"));
        assertNull(capacity.get());
    }

    @Test
    void secondRealTransactionForTheSameAttemptIsAnomalyAndCannotReplaceTheFirst() {
        var original = success();
        service.record("tenant", original);
        var other = new PaymentObservation(original.identity(), original.kind(), original.amount(), "second-transaction",
                original.succeededAt(), PaymentObservation.Source.QUERY_RESPONSE, "query-1", original.sourceDigest());
        assertEquals(CONFLICT, service.record("tenant", other).disposition());
        assertEquals("ATTEMPT_TRANSACTION_CONFLICT", anomalies.getFirst().reasonCode());
        assertEquals("channel-transaction", claim.get().channelTransactionNo());
    }

    @Test
    void reversalRequiresIndependentCompensationInsteadOfRewritingGrossPaidAmount() {
        assertEquals(CONFLICT, service.record("tenant", observation(ChannelPaymentOutcome.Kind.CLOSED_WITH_FUNDS_RETURNED, "reversal", PaymentObservation.Source.CALLBACK)).disposition());
        assertEquals("FUNDS_RETURNED_REQUIRES_COMPENSATION", anomalies.getFirst().reasonCode());
        assertNull(claim.get());
        assertNull(capacity.get());
    }

    @Test
    void intermediateObservationAfterSuccessCannotRegressTheTerminalState() {
        service.record("tenant", success());
        clearInvocations(events);
        var result = service.record("tenant", observation(ChannelPaymentOutcome.Kind.UNKNOWN, "query-1", PaymentObservation.Source.QUERY_RESPONSE));
        assertEquals(IGNORED, result.disposition());
        assertEquals(PaymentState.SUCCEEDED, accepted.get().payment().state());
        verifyNoInteractions(events);
    }

    @Test
    void outboxFailureRollsBackTheY3BoundaryInsteadOfAcknowledgingSuccess() {
        doThrow(new CommonException(DEPENDENCY_UNAVAILABLE)).when(events).publish(anyString(), any(), anyBoolean(), any());
        assertThrows(CommonException.class, () -> service.record("tenant", success()));
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
        // Mock maps do not model database rollback; actual persistence is a separate MySQL acceptance gate.
    }

    @Test
    void externalTransactionIsRejectedBeforeAuthorityOrReceiptAccess() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> service.record("tenant", success())).getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(payments, results, events);
    }
}
