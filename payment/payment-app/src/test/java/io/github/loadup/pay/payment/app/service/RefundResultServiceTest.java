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
import io.github.loadup.pay.payment.app.converter.RefundExecutionConverter;
import io.github.loadup.pay.payment.app.converter.RefundPlanningConverter;
import io.github.loadup.pay.payment.app.model.LockedRefund;
import io.github.loadup.pay.payment.app.port.RefundResultCodec;
import io.github.loadup.pay.payment.domain.gateway.RefundResultGateway;
import io.github.loadup.pay.payment.domain.model.ChannelRefundOutcome;
import io.github.loadup.pay.payment.domain.model.PaymentRefundCapacity;
import io.github.loadup.pay.payment.domain.model.RefundChannelReceipt;
import io.github.loadup.pay.payment.domain.model.RefundExecutionFacts;
import io.github.loadup.pay.payment.domain.model.RefundObservation;
import io.github.loadup.pay.payment.domain.model.RefundProgressChange;
import io.github.loadup.pay.payment.domain.model.RefundResultAnomaly;
import io.github.loadup.pay.payment.domain.model.RefundState;
import io.github.loadup.pay.payment.domain.model.RefundTransactionClaim;
import io.github.loadup.pay.payment.domain.model.StoredRefundRequest;
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

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.domain.model.RefundReceiptDisposition.APPLIED;
import static io.github.loadup.pay.payment.domain.model.RefundReceiptDisposition.CONFLICT;
import static io.github.loadup.pay.payment.domain.model.RefundReceiptDisposition.IGNORED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundResultServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(RefundExecutionConverter.class.getPackageName());
    final RefundExecutionConverter converter = context.getBean(RefundExecutionConverter.class);
    final RefundResultGateway results = mock(RefundResultGateway.class);
    final RefundProjectionEvents projections = mock(RefundProjectionEvents.class);
    final RefundExecutionLocks locks = mock(RefundExecutionLocks.class);
    final RefundResultCodec codec = mock(RefundResultCodec.class);
    final PaymentAcceptanceServiceTest.UnitTransactions manager = new PaymentAcceptanceServiceTest.UnitTransactions();
    final AtomicReference<StoredRefundRequest> request = new AtomicReference<>(RefundResultFixtures.initial());
    final AtomicReference<RefundExecutionFacts> facts = new AtomicReference<>();
    final AtomicReference<PaymentRefundCapacity> capacity = new AtomicReference<>(RefundResultFixtures.capacity());
    final AtomicReference<RefundTransactionClaim> claim = new AtomicReference<>();
    final Map<String, RefundChannelReceipt> receipts = new HashMap<>();
    final List<RefundResultAnomaly> anomalies = new ArrayList<>();
    final AtomicInteger ids = new AtomicInteger();
    final RefundResultService service = new RefundResultService(results, locks, new PaymentAcceptanceTransactions(manager), codec,
            converter, context.getBean(RefundPlanningConverter.class), () -> "receipt-" + ids.incrementAndGet(),
            Clock.fixed(RefundResultFixtures.NOW.atZone(io.github.loadup.commons.time.BusinessTime.zone()).toInstant(), io.github.loadup.commons.time.BusinessTime.zone()), projections);

    @BeforeEach
    void setup() {
        var original = request.get();
        var child = original.allocations().getFirst();
        var started = converter.withRefund(child, child.refund().beginSubmission());
        request.set(converter.withRequest(original, original.request().apply(started.refund()), List.of(started, original.allocations().get(1))));
        facts.set(new RefundExecutionFacts(started, RefundResultFixtures.NOW, null, null));
        when(results.requestId("tenant", "refund")).thenReturn(Optional.of("request"));
        when(locks.lock("tenant", "request", "refund")).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return new LockedRefund(request.get(), facts.get(), capacity.get());
        });
        when(codec.encode(any())).thenReturn("{}");
        when(codec.factsDigest(any())).thenAnswer(c -> {
            RefundObservation o = c.getArgument(0);
            return "%064x".formatted(Integer.toUnsignedLong(Objects.hash(o.refundId(), o.original(), o.channelRefundNo(), o.originalChannelTransactionNo(),
                    o.kind(), o.amount(), o.channelRefundTransactionNo(), o.succeededAt())));
        });
        when(results.receipt(eq("tenant"), eq("refund"), any(), anyString())).thenAnswer(c -> Optional.ofNullable(receipts.get(c.getArgument(2) + "/" + c.getArgument(3))));
        when(results.claim("tenant", "refund")).thenAnswer(c -> Optional.ofNullable(claim.get()).filter(v -> v.tenantId().equals("tenant") && v.refundId().equals("refund")));
        when(results.transaction(any(), anyString(), anyString())).thenAnswer(c -> Optional.ofNullable(claim.get()).filter(v -> v.channelRefundTransactionNo().equals(c.getArgument(2))));
        doAnswer(c -> {
            claim.set(c.getArgument(0));
            return null;
        }).when(results).insertClaim(any());
        doAnswer(c -> {
            RefundProgressChange change = c.getArgument(1);
            var updated = converter.withRefund(facts.get().child(), change.refund());
            request.set(converter.withRequest(request.get(), change.request(), List.of(updated, request.get().allocations().get(1))));
            facts.set(new RefundExecutionFacts(updated, change.submissionStartedAt(), change.channelRefundTransactionNo(), change.succeededAt()));
            capacity.set(change.capacity());
            return null;
        }).when(results).update(eq("tenant"), any());
        doAnswer(c -> {
            RefundChannelReceipt r = c.getArgument(1);
            receipts.put(r.source() + "/" + r.receiptKey(), r);
            return null;
        })
                .when(results).insertReceipt(eq("tenant"), any());
        doAnswer(c -> {
            anomalies.add(c.getArgument(1));
            return null;
        }).when(results).recordAnomaly(eq("tenant"), any());
    }

    @AfterEach
    void close() {
        context.close();
    }

    RefundObservation observation(ChannelRefundOutcome.Kind kind, String key) {
        return RefundResultFixtures.observation(kind, key, RefundObservation.Source.CALLBACK);
    }

    @Test
    void successAtomicallyConsumesTheHoldAndAdvancesOnlyTheFrozenChildAndRequest() {
        var result = service.record("tenant", observation(ChannelRefundOutcome.Kind.SUCCEEDED, "event"));
        assertEquals(APPLIED, result.disposition());
        assertEquals(1000, capacity.get().refundedMinor());
        assertEquals(0, capacity.get().reservedMinor());
        assertEquals(4000, capacity.get().succeededAmount().getCent());
        assertEquals(RefundState.SUCCEEDED, facts.get().child().refund().state());
        assertEquals("refund2", request.get().request().nextExecutable().orElseThrow().id());
        assertEquals(1000, request.get().request().succeededMinor());
        assertEquals(1, manager.commits);
        assertEquals(result.receiptId(), claim.get().receiptId());
        assertEquals("original-transaction", claim.get().originalChannelTransactionNo());
        assertEquals(RefundResultFixtures.NOW, facts.get().succeededAt());
    }

    @Test
    void sameReceiptAndAnotherObservationOfTheSameRefundCannotConsumeCapacityTwice() {
        var first = service.record("tenant", observation(ChannelRefundOutcome.Kind.SUCCEEDED, "event"));
        var replay = service.record("tenant", observation(ChannelRefundOutcome.Kind.SUCCEEDED, "event"));
        assertEquals(first.receiptId(), replay.receiptId());
        assertEquals(IGNORED, service.record("tenant", observation(ChannelRefundOutcome.Kind.SUCCEEDED, "other-event")).disposition());
        verify(results, times(1)).update(eq("tenant"), any());
        verify(results, times(1)).insertClaim(any());
        assertEquals(1000, capacity.get().refundedMinor());
        verify(projections, times(1)).changed(eq("tenant"), any());
    }

    @Test
    void unknownKeepsCapacityAndBlocksTheNextChildWhileAcceptedCannotRegressIt() {
        service.record("tenant", observation(ChannelRefundOutcome.Kind.UNKNOWN, "unknown"));
        assertEquals(1000, capacity.get().reservedMinor());
        assertEquals(0, capacity.get().refundedMinor());
        assertTrue(request.get().request().nextExecutable().isEmpty());
        assertEquals(1, request.get().request().unknownCount());
        assertEquals(IGNORED, service.record("tenant", observation(ChannelRefundOutcome.Kind.ACCEPTED, "accepted")).disposition());
        assertEquals(RefundState.UNKNOWN, facts.get().child().refund().state());
    }

    @Test
    void definiteFailureReleasesTheHoldButDoesNotReallocateOrReopenTheFailedChild() {
        service.record("tenant", observation(ChannelRefundOutcome.Kind.FAILED_WITH_NO_REFUND, "failed"));
        assertEquals(0, capacity.get().reservedMinor());
        assertEquals(0, capacity.get().refundedMinor());
        assertEquals(1000, request.get().request().failedMinor());
        assertEquals("refund2", request.get().request().nextExecutable().orElseThrow().id());
        assertEquals(CONFLICT, service.record("tenant", observation(ChannelRefundOutcome.Kind.SUCCEEDED, "late-success")).disposition());
        assertEquals("TERMINAL_FACT_CONFLICT", anomalies.getFirst().reasonCode());
        verify(results, never()).insertClaim(any());
    }

    @Test
    void changedFactsUnderTheSameReceiptBecomeADurableAnomaly() {
        service.record("tenant", observation(ChannelRefundOutcome.Kind.UNKNOWN, "event"));
        var result = service.record("tenant", observation(ChannelRefundOutcome.Kind.SUCCEEDED, "event"));
        assertEquals(CONFLICT, result.disposition());
        assertEquals("RECEIPT_FACTS_CONFLICT", anomalies.getFirst().reasonCode());
        assertEquals(1, receipts.size());
        assertEquals(1000, capacity.get().reservedMinor());
    }

    @Test
    void amountOrOriginalTransactionMismatchNeverConsumesMoney() {
        var original = observation(ChannelRefundOutcome.Kind.SUCCEEDED, "event");
        var changed = new RefundObservation(original.refundId(), original.original(), original.channelRefundNo(), "other-payment-transaction",
                original.kind(), original.amount(), original.channelRefundTransactionNo(), original.succeededAt(), original.source(), original.receiptKey(), original.sourceDigest());
        assertEquals(CONFLICT, service.record("tenant", changed).disposition());
        assertEquals("REFUND_IDENTITY_MISMATCH", anomalies.getFirst().reasonCode());
        verify(results, never()).update(anyString(), any());
        verify(results, never()).insertClaim(any());
    }

    @Test
    void aRealRefundTransactionClaimedInAnotherTenantIsNotReused() {
        var o = observation(ChannelRefundOutcome.Kind.SUCCEEDED, "event");
        claim.set(new RefundTransactionClaim("other-claim", "other-tenant", "other-refund", o.original(), "other-request",
                o.originalChannelTransactionNo(), o.amount(), o.channelRefundTransactionNo(), o.succeededAt(), "other-receipt"));
        assertEquals(CONFLICT, service.record("tenant", o).disposition());
        assertEquals("REFUND_TRANSACTION_ALREADY_CLAIMED", anomalies.getFirst().reasonCode());
        assertEquals(0, capacity.get().refundedMinor());
        verify(results, never()).update(anyString(), any());
    }

    @Test
    void verifiedObservationBeforeTheLocalSubmissionBoundaryIsRejected() {
        var child = RefundResultFixtures.initial().allocations().getFirst();
        facts.set(new RefundExecutionFacts(child, null, null, null));
        request.set(RefundResultFixtures.initial());
        assertEquals(CONFLICT, service.record("tenant", observation(ChannelRefundOutcome.Kind.SUCCEEDED, "event")).disposition());
        assertEquals("OBSERVATION_BEFORE_SUBMISSION", anomalies.getFirst().reasonCode());
        verify(results, never()).update(anyString(), any());
    }

    @Test
    void snapshotFailureCannotCommitARefundFinancialTransition() {
        doThrow(new CommonException(DEPENDENCY_UNAVAILABLE)).when(projections).changed(anyString(), any());
        assertThrows(CommonException.class, () -> service.record("tenant", observation(ChannelRefundOutcome.Kind.SUCCEEDED, "event")));
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
        verify(results, never()).insertReceipt(anyString(), any());
        // Source-only boundary proof; real database rollback remains unverified.
    }

    @Test
    void receiptFailureAbortsTheWholeResultTransactionBoundary() {
        doThrow(new CommonException(CONCURRENT_UPDATE)).when(results).insertReceipt(anyString(), any());
        assertThrows(CommonException.class, () -> service.record("tenant", observation(ChannelRefundOutcome.Kind.SUCCEEDED, "event")));
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
        // Mock state is not a rollback proof. Real claim/child/request/capacity rollback still needs MySQL acceptance.
    }

    @Test
    void localTransportUncertaintyIsNotAChannelReceiptOrRefundSuccess() {
        service.uncertain("tenant", "request", "refund");
        assertEquals(RefundState.UNKNOWN, facts.get().child().refund().state());
        assertEquals(1000, capacity.get().reservedMinor());
        verify(results, never()).insertReceipt(anyString(), any());
        verify(results, never()).insertClaim(any());
    }
}
