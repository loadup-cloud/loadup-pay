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
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.model.ChannelPaymentOutcome;
import io.github.loadup.pay.channel.api.model.ChannelPaymentTarget;
import io.github.loadup.pay.channel.api.model.PaymentObservation;
import io.github.loadup.pay.payment.app.converter.PaymentCloseEvidenceConverter;
import io.github.loadup.pay.payment.app.model.PaymentResultApplication;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentChannelGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentCloseEvidenceGateway;
import io.github.loadup.pay.payment.domain.model.NoCollectionEvidence;
import io.github.loadup.pay.payment.domain.model.PaymentCloseEvidence;
import io.github.loadup.pay.payment.domain.model.PaymentReceiptDisposition;
import io.github.loadup.pay.payment.domain.model.RiskDecision;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentCloseServiceTest {
    final AnnotationConfigApplicationContext spring = new AnnotationConfigApplicationContext(PaymentCloseEvidenceConverter.class.getPackageName());
    final PaymentAcceptanceGateway payments = mock(PaymentAcceptanceGateway.class);
    final PaymentAttemptGateway attempts = mock(PaymentAttemptGateway.class);
    final PaymentExecutionLocks locks = mock(PaymentExecutionLocks.class);
    final PaymentCloseEvidenceGateway evidence = mock(PaymentCloseEvidenceGateway.class);
    final PaymentChannelGateway channels = mock(PaymentChannelGateway.class);
    final PaymentResultService results = mock(PaymentResultService.class);
    final PaymentAcceptanceTransactions transactions = new PaymentAcceptanceTransactions(new PaymentAcceptanceServiceTest.UnitTransactions());
    final Clock clock = Clock.fixed(AttemptFixtures.NOW.atZone(BusinessTime.zone()).toInstant(), BusinessTime.zone());
    final StoredPaymentAttempt original = AttemptFixtures.prepared().beginSubmission(1L, "d".repeat(64), RiskDecision.notConnected(), AttemptFixtures.NOW);
    final StoredPaymentAcceptance base = AttemptFixtures.accepted(true);
    final StoredPaymentAcceptance accepted = new StoredPaymentAcceptance(base.payment().requestClose(), base.admissionId(),
            base.requestDigest(), base.callerServiceId(), base.intentPayload(), base.payloadHash(), base.terminalEvidenceId());
    final AtomicReference<PaymentCloseEvidence> saved = new AtomicReference<>();
    final PaymentCloseService service = new PaymentCloseService(payments, attempts, locks, evidence, channels, results, transactions,
            spring.getBean(PaymentCloseEvidenceConverter.class), clock);
    final PaymentResultApplication applied = new PaymentResultApplication(PaymentReceiptDisposition.APPLIED, "receipt", null);

    private static void write() {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
    }

    @BeforeEach
    void setup() {
        when(attempts.find("tenant", "attempt")).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return Optional.of(original);
        });
        when(payments.find("tenant", "payment")).thenReturn(Optional.of(accepted));
        when(locks.lock("tenant", "payment", "acquire")).thenAnswer(call -> {
            write();
            return PaymentExecutionBindings.locked(accepted, AttemptFixtures.gate(true), AttemptFixtures.gate(false));
        });
        when(attempts.lock("tenant", "attempt")).thenAnswer(call -> {
            write();
            return Optional.of(original);
        });
        when(evidence.find("tenant", "attempt")).thenAnswer(call -> Optional.ofNullable(saved.get()));
        doAnswer(call -> {
            write();
            saved.set(call.getArgument(1));
            return null;
        }).when(evidence).insert(eq("tenant"), any());
        when(channels.close(any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("tenant", TenantUtil.getTenantId());
            assertEquals(new ChannelPaymentTarget(original.attempt().identity(), original.attempt().amount()), call.getArgument(0));
            return observation(ChannelPaymentOutcome.Kind.NO_COLLECTION, PaymentObservation.Source.CLOSE_RESPONSE, "a".repeat(64));
        });
        when(results.record(eq("tenant"), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return applied;
        });
    }

    @AfterEach
    void close() {
        spring.close();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void acknowledgedCloseIsCommittedBeforeY3AndReplaysTheSameSourceWithoutAnotherChannelCall() {
        assertEquals(applied, service.close("tenant", original));
        var proof = saved.get();
        assertNotNull(proof);
        assertEquals(1L, proof.executionEpoch());
        assertEquals(AttemptFixtures.NOW, proof.recordedAt());
        assertEquals(original.attempt().identity(), proof.identity());
        var ordered = inOrder(channels, evidence, results);
        ordered.verify(channels).close(any());
        ordered.verify(evidence).insert("tenant", proof);
        ordered.verify(results).record(eq("tenant"), any());
        service.close("tenant", original);
        verify(channels, times(1)).close(any());
        verify(evidence, times(1)).insert(anyString(), any());
        verify(results, times(2)).record(eq("tenant"), argThat(value -> value.source() == PaymentObservation.Source.CLOSE_RESPONSE
                && value.kind() == ChannelPaymentOutcome.Kind.NO_COLLECTION && value.sourceDigest().equals(proof.sourceDigest())
                && value.receiptKey().equals(proof.receiptKey())));
    }

    @Test
    void y3FailureKeepsTheOriginalProofForANewRecoveryAttempt() {
        when(results.record(eq("tenant"), any())).thenAnswer(call -> {
            assertNotNull(saved.get());
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            throw new IllegalStateException("controlled Y3 rollback");
        });
        assertThrows(IllegalStateException.class, () -> service.close("tenant", original));
        var first = saved.get();
        assertNotNull(first);
        when(results.record(eq("tenant"), any())).thenReturn(applied);
        assertEquals(applied, service.close("tenant", original));
        assertEquals(first, saved.get());
        verify(channels, times(1)).close(any());
    }

    @Test
    void ambiguousQueriesAndActualSuccessContinueThroughY3WithoutManufacturingCloseEvidence() {
        for (var kind : new ChannelPaymentOutcome.Kind[]{ChannelPaymentOutcome.Kind.UNKNOWN, ChannelPaymentOutcome.Kind.SUCCEEDED}) {
            var observed = observation(kind, PaymentObservation.Source.QUERY_RESPONSE, "b".repeat(64));
            when(channels.close(any())).thenReturn(observed);
            assertEquals(applied, service.close("tenant", original));
            verify(results).record("tenant", observed);
        }
        verify(evidence, never()).insert(anyString(), any());
        assertNull(saved.get());
    }

    @Test
    void aQueryOrCallbackCannotSupplyTheAcknowledgedNoCollectionProof() {
        for (var source : new PaymentObservation.Source[]{PaymentObservation.Source.QUERY_RESPONSE, PaymentObservation.Source.CALLBACK}) {
            when(channels.close(any())).thenReturn(observation(ChannelPaymentOutcome.Kind.NO_COLLECTION, source, "b".repeat(64)));
            assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> service.close("tenant", original)).getResultCode());
        }
        verify(evidence, never()).insert(anyString(), any());
        verifyNoInteractions(results);
    }

    @Test
    void lostNetworkResponseLeavesNoEvidenceAndDoesNotReleaseAnyFinancialState() {
        when(channels.close(any())).thenThrow(new CommonException(DEPENDENCY_UNAVAILABLE));
        assertThrows(CommonException.class, () -> service.close("tenant", original));
        assertNull(saved.get());
        verifyNoInteractions(results);
        verify(evidence, never()).insert(anyString(), any());
    }

    @Test
    void existingEvidenceForAnotherEpochIsRejectedBeforeChannelOrY3() {
        saved.set(new PaymentCloseEvidence(original.attempt().identity(), original.attempt().amount(), null,
                "close", "a".repeat(64), 2L, AttemptFixtures.NOW));
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> service.close("tenant", original)).getResultCode());
        verifyNoInteractions(channels, results);
    }

    @Test
    void theFirstConcurrentAcknowledgementRemainsTheOnlyRecordedProof() {
        var winner = new PaymentCloseEvidence(original.attempt().identity(), original.attempt().amount(), "transaction",
                "first-close", "e".repeat(64), 1L, AttemptFixtures.NOW);
        when(channels.close(any())).thenAnswer(call -> {
            saved.set(winner);
            return observation(ChannelPaymentOutcome.Kind.NO_COLLECTION, PaymentObservation.Source.CLOSE_RESPONSE, "f".repeat(64));
        });
        service.close("tenant", original);
        verify(evidence, never()).insert(anyString(), any());
        assertEquals(winner, saved.get());
        verify(results).record(eq("tenant"), argThat(value -> value.receiptKey().equals("first-close") && value.sourceDigest().equals("e".repeat(64))));
    }

    @Test
    void neverSubmittedAndOuterTransactionAreRejectedBeforeAnyStateOrNetworkRead() {
        assertEquals(INVALID_ARGUMENT, assertThrows(CommonException.class, () -> service.close("tenant", AttemptFixtures.prepared())).getResultCode());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertEquals(INVALID_STATE, assertThrows(CommonException.class, () -> service.close("tenant", original)).getResultCode());
        verifyNoInteractions(payments, attempts, locks, evidence, channels, results);
    }

    @Test
    void anOpenOrTerminalPaymentWithoutSavedEvidenceDoesNotStartAChannelClose() {
        when(payments.find("tenant", "payment")).thenReturn(Optional.of(base));
        assertEquals(INVALID_STATE, assertThrows(CommonException.class, () -> service.close("tenant", original)).getResultCode());
        var terminal = new StoredPaymentAcceptance(base.payment().fail(new NoCollectionEvidence("proof", "payment", true)),
                base.admissionId(), base.requestDigest(), base.callerServiceId(), base.intentPayload(), base.payloadHash(), "proof");
        when(payments.find("tenant", "payment")).thenReturn(Optional.of(terminal));
        assertEquals(INVALID_STATE, assertThrows(CommonException.class, () -> service.close("tenant", original)).getResultCode());
        verifyNoInteractions(channels, results);
    }

    @Test
    void targetMismatchNeverReachesEvidenceStorageOrY3() {
        var observed = observation(ChannelPaymentOutcome.Kind.NO_COLLECTION, PaymentObservation.Source.CLOSE_RESPONSE, "b".repeat(64));
        when(channels.close(any())).thenReturn(new PaymentObservation(observed.identity(), observed.kind(), Money.ofMinor(1L, "CNY"),
                observed.channelTransactionNo(), null, observed.source(), observed.receiptKey(), observed.sourceDigest()));
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> service.close("tenant", original)).getResultCode());
        verifyNoInteractions(results);
        verify(evidence, never()).insert(anyString(), any());
    }

    private PaymentObservation observation(ChannelPaymentOutcome.Kind kind, PaymentObservation.Source source, String digest) {
        return new PaymentObservation(original.attempt().identity(), kind, original.attempt().amount(), "transaction",
                kind == ChannelPaymentOutcome.Kind.SUCCEEDED ? AttemptFixtures.NOW : null, source, "close:" + digest, digest);
    }
}
