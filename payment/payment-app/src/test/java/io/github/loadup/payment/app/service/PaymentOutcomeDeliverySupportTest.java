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
package io.github.loadup.payment.app.service;

import static io.github.loadup.contracts.payment.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.contracts.payment.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.outbox.OutboxEvent;
import io.github.loadup.components.outbox.OutboxMessage;
import io.github.loadup.components.outbox.OutboxRecord;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.contracts.model.PaymentState;
import io.github.loadup.payment.app.converter.PaymentResultConverter;
import io.github.loadup.payment.app.model.PaymentDeliverySnapshot;
import io.github.loadup.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.payment.app.port.PaymentOutcomeEnvelopeCodec;
import io.github.loadup.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.payment.domain.gateway.PaymentResultGateway;
import io.github.loadup.payment.domain.model.ChannelTransactionClaim;
import io.github.loadup.payment.domain.model.PaymentOrder;
import io.github.loadup.payment.domain.model.PaymentRefundCapacity;
import io.github.loadup.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.payment.domain.model.StoredPaymentGate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PaymentOutcomeDeliverySupportTest {
    final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(PaymentResultConverter.class.getPackageName());
    final OutboxTemplate outbox = mock(OutboxTemplate.class);
    final PaymentOutcomeEnvelopeCodec envelopes = mock(PaymentOutcomeEnvelopeCodec.class);
    final PaymentAcceptanceGateway payments = mock(PaymentAcceptanceGateway.class);
    final PaymentAttemptGateway attempts = mock(PaymentAttemptGateway.class);
    final PaymentResultGateway results = mock(PaymentResultGateway.class);
    final PaymentExecutionLocks locks = mock(PaymentExecutionLocks.class);
    final PaymentAcceptanceCodec intents = mock(PaymentAcceptanceCodec.class);
    final PaymentAcceptanceServiceTest.UnitTransactions manager = new PaymentAcceptanceServiceTest.UnitTransactions();
    final PaymentOutcomeDeliverySupport support = new PaymentOutcomeDeliverySupport(
            outbox,
            envelopes,
            payments,
            attempts,
            results,
            locks,
            new PaymentAcceptanceTransactions(manager),
            intents,
            context.getBean(PaymentResultConverter.class));
    final AtomicReference<PaymentDeliverySnapshot> source = new AtomicReference<>();
    final AtomicReference<StoredPaymentGate> gate = new AtomicReference<>();
    final OutboxEvent event = new OutboxEvent(
            "queue-event",
            new OutboxMessage(PaymentEvents.LIMIT, 1, "payment", "payload"),
            AttemptFixtures.NOW.atZone(BusinessTime.zone()).toInstant(),
            null);

    void source(boolean succeeded) {
        var snapshot = OutcomeDeliveryFixtures.snapshot(succeeded, true, PaymentEvents.LIMIT);
        source.set(snapshot);
        var initial = AttemptFixtures.gate(false);
        gate.set(
                succeeded
                        ? initial
                        : new StoredPaymentGate(
                                initial.id(),
                                initial.payeeMerchantId(),
                                initial.callerServiceId(),
                                initial.gate().fence(1),
                                1));
        when(envelopes.decode("payload")).thenReturn(snapshot.envelope());
    }

    PaymentDeliverySnapshot load() {
        return TenantUtil.callWithTenant("tenant", () -> support.load(event, PaymentEvents.LIMIT));
    }

    @BeforeEach
    void setup() {
        source(true);
        when(outbox.find("queue-event"))
                .thenReturn(Optional.of(
                        new OutboxRecord("queue-event", PaymentEvents.LIMIT, "payment", "PROCESSING", 1, null)));
        when(payments.find("payment")).thenAnswer(c -> Optional.of(source.get().payment()));
        when(locks.lock("tenant", "payment", "acquire")).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return PaymentExecutionBindings.locked(source.get().payment(), AttemptFixtures.gate(true), gate.get());
        });
        when(attempts.lock("attempt"))
                .thenAnswer(c -> Optional.ofNullable(source.get().attempt()));
        when(intents.decode(any())).thenReturn(AttemptFixtures.command());
        when(results.attemptClaim("attempt")).thenAnswer(c -> {
            var current = source.get();
            return Optional.of(new ChannelTransactionClaim(
                    "claim",
                    current.attempt().attempt().identity(),
                    current.payment().payment().amount(),
                    "channel-transaction",
                    AttemptFixtures.NOW,
                    "evidence"));
        });
        when(results.capacity("payment"))
                .thenAnswer(c -> Optional.of(new PaymentRefundCapacity(
                        "payment",
                        "order",
                        "merchant",
                        AttemptFixtures.NOW,
                        source.get().payment().payment().amount(),
                        0,
                        0,
                        0)));
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void locksProduceAConsistentSnapshotAndAreReleasedBeforeReturningToTheSender() {
        var loaded = load();
        assertEquals(source.get().payment(), loaded.payment());
        assertEquals(AttemptFixtures.NOW, loaded.succeededAt());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(1, manager.commits);
        assertNull(TenantUtil.getTenantId());
    }

    @Test
    void aLaterExecutionFenceDoesNotInvalidateAnEarlierConfirmedSuccessEvent() {
        var original = gate.get();
        gate.set(new StoredPaymentGate(
                original.id(),
                original.payeeMerchantId(),
                original.callerServiceId(),
                original.gate().fence(1),
                1));
        assertEquals(PaymentState.SUCCEEDED, load().payment().payment().state());
    }

    @Test
    void currentRefundReservationsDoNotRewriteTheOriginalSuccessfulLimitBasis() {
        when(results.capacity("payment"))
                .thenReturn(Optional.of(new PaymentRefundCapacity(
                        "payment",
                        "order",
                        "merchant",
                        AttemptFixtures.NOW,
                        source.get().payment().payment().amount(),
                        500,
                        300,
                        2)));
        assertEquals(4000, load().payment().payment().amount().getCent());
    }

    @Test
    void failedPaymentMustStillHaveAPersistentlyFencedExecutionGate() {
        source(false);
        gate.set(AttemptFixtures.gate(false));
        assertSame(
                SNAPSHOT_INVALID,
                assertThrows(CommonException.class, this::load).getResultCode());
    }

    @Test
    void mismatchedQueueTargetIsRejectedBeforeReadingBusinessFacts() {
        when(outbox.find("queue-event"))
                .thenReturn(Optional.of(
                        new OutboxRecord("queue-event", PaymentEvents.RISK, "payment", "PROCESSING", 1, null)));
        assertThrows(CommonException.class, this::load);
        verifyNoInteractions(payments, locks, results);
    }

    @Test
    void completedOrFailedRowsCannotBeUsedAsArbitraryManualDispatchTickets() {
        when(outbox.find("queue-event"))
                .thenReturn(Optional.of(
                        new OutboxRecord("queue-event", PaymentEvents.LIMIT, "payment", "FAILED", 5, "Failure")));
        assertThrows(CommonException.class, this::load);
        verifyNoInteractions(payments, results);
    }

    @Test
    void anEventCannotReplaceItsPaymentVersionWithTheCurrentVersion() {
        var initial = source.get();
        var p = initial.payment().payment();
        var changed = new PaymentOrder(
                p.id(),
                p.orderId(),
                p.allocationId(),
                p.payeeMerchantId(),
                p.amount(),
                p.limitOperationId(),
                p.initiatedAt(),
                p.state(),
                p.activeAttemptId(),
                p.successfulAttemptId(),
                p.version() + 1);
        var stored = new StoredPaymentAcceptance(
                changed,
                initial.payment().admissionId(),
                initial.payment().requestDigest(),
                initial.payment().callerServiceId(),
                initial.payment().intentPayload(),
                initial.payment().payloadHash(),
                "evidence");
        source.set(new PaymentDeliverySnapshot(initial.envelope(), stored, initial.attempt(), initial.succeededAt()));
        assertSame(
                SNAPSHOT_INVALID,
                assertThrows(CommonException.class, this::load).getResultCode());
    }

    @Test
    void successWithoutATransactionClaimOrRefundCapacityCannotBeConfirmed() {
        when(results.attemptClaim("attempt")).thenReturn(Optional.empty());
        assertThrows(CommonException.class, this::load);
        when(results.attemptClaim("attempt"))
                .thenReturn(Optional.of(new ChannelTransactionClaim(
                        "claim",
                        source.get().attempt().attempt().identity(),
                        source.get().payment().payment().amount(),
                        "transaction",
                        AttemptFixtures.NOW,
                        "evidence")));
        when(results.capacity("payment")).thenReturn(Optional.empty());
        assertThrows(CommonException.class, this::load);
    }

    @Test
    void negativeOutcomeCannotReuseAnotherNoCollectionProof() {
        source(false);
        var initial = source.get();
        var wrong = AttemptFixtures.prepared().closeBeforeSubmission("another-evidence", null);
        source.set(new PaymentDeliverySnapshot(initial.envelope(), initial.payment(), wrong, null));
        assertThrows(CommonException.class, this::load);
    }

    @Test
    void outerTransactionCannotSurroundSourceLookupAndRemoteDelivery() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(
                    INVALID_STATE,
                    assertThrows(CommonException.class, this::load).getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(outbox, payments, results);
    }
}
