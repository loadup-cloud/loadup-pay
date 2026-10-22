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
import io.github.loadup.pay.payment.app.converter.*;
import io.github.loadup.pay.payment.app.model.LockedPayment;
import io.github.loadup.pay.payment.app.port.*;
import io.github.loadup.pay.payment.domain.gateway.*;
import io.github.loadup.pay.payment.domain.model.*;
import io.github.loadup.pay.limitcenter.client.dto.*;
import io.github.loadup.pay.limitcenter.client.enums.LimitReservationState;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentSubmissionPreparationTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(PaymentSubmissionConverter.class.getPackageName());
    final PaymentAcceptanceGateway payments = mock(PaymentAcceptanceGateway.class);
    final PaymentControlGateway changes = mock(PaymentControlGateway.class);
    final PaymentAttemptGateway attempts = mock(PaymentAttemptGateway.class);
    final PaymentExecutionLocks locks = mock(PaymentExecutionLocks.class);
    final PaymentInputAccess inputs = mock(PaymentInputAccess.class);
    final PaymentLimitClient limits = mock(PaymentLimitClient.class);
    final RiskConsultGateway risk = mock(RiskConsultGateway.class);
    final PaymentAcceptanceCodec codec = mock(PaymentAcceptanceCodec.class);
    final PaymentResultEvents events = mock(PaymentResultEvents.class);
    final AtomicReference<StoredPaymentAcceptance> accepted = new AtomicReference<>(AttemptFixtures.accepted(true));
    final AtomicReference<StoredPaymentAttempt> stored = new AtomicReference<>(AttemptFixtures.prepared());
    final AtomicReference<StoredPaymentGate> gate = new AtomicReference<>(AttemptFixtures.gate(false));
    final PaymentAcceptanceServiceTest.UnitTransactions manager = new PaymentAcceptanceServiceTest.UnitTransactions();
    final PaymentSubmissionPreparation service = new PaymentSubmissionPreparation(payments, changes, attempts, locks, inputs, limits,
            risk, codec, context.getBean(PaymentSubmissionConverter.class), context.getBean(PaymentControlConverter.class),
            new PaymentAcceptanceTransactions(manager), () -> "local-no-submission-proof",
            Clock.fixed(AttemptFixtures.NOW.atZone(BusinessTime.ZONE).toInstant(), BusinessTime.ZONE), events);
    LimitReservationDTO reservation(LimitReservationState state) {
        var policy = AttemptFixtures.command().admittedTerms().terms().limits();
        return new LimitReservationDTO("reservation", "operation", "payment", state, policy.policyId(), policy.policyVersion(),
                BusinessTime.external(AttemptFixtures.NOW), 1);
    }
    LimitExecutionPermitDTO permit() { return new LimitExecutionPermitDTO("operation", "payment", "attempt", 1, "e".repeat(64)); }
    @BeforeEach void setup() {
        when(limits.supportsTenant("tenant")).thenReturn(true);
        when(payments.find("tenant", "payment")).thenAnswer(call -> Optional.of(accepted.get()));
        when(attempts.find("tenant", "attempt")).thenAnswer(call -> Optional.of(stored.get()));
        when(attempts.lock("tenant", "attempt")).thenAnswer(call -> Optional.of(stored.get()));
        when(codec.decode(any())).thenReturn(AttemptFixtures.command());
        when(codec.checksum(anyString())).thenReturn("b".repeat(64));
        when(risk.consult(any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return RiskDecision.notConnected();
        });
        when(limits.reserve(eq("tenant"), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return reservation(LimitReservationState.RESERVED);
        });
        when(limits.begin(eq("tenant"), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return permit();
        });
        when(limits.verify(eq("tenant"), any())).thenReturn(new LimitExecutionVerificationDTO(true, "VALID"));
        when(locks.lock("tenant", "payment", "acquire")).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return new LockedPayment(accepted.get(), AttemptFixtures.gate(true), gate.get());
        });
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); stored.set(call.getArgument(1)); return null;
        }).when(attempts).update(eq("tenant"), any(), anyLong());
        doAnswer(call -> { accepted.set(call.getArgument(1)); return null; }).when(changes).updatePayment(eq("tenant"), any(), anyLong());
        doAnswer(call -> { gate.set(call.getArgument(1)); return null; }).when(changes).updateGate(eq("tenant"), any(), anyLong());
    }
    @AfterEach void close() { context.close(); }
    @Test void remoteRiskAndLimitPreparationPrecedeOneDurableY2Ticket() {
        var result = service.prepare("tenant", "payment", "attempt").orElseThrow();
        assertEquals("attempt", result.attemptId()); assertEquals(1, result.payment().executionEpoch());
        assertEquals(PaymentAttemptState.SUBMITTING, stored.get().attempt().state());
        assertEquals("b".repeat(64), stored.get().executionPermitHash());
        assertFalse(stored.get().toString().contains("e".repeat(64)));
        var calls = inOrder(risk, limits, locks, inputs, attempts);
        calls.verify(risk).consult(any()); calls.verify(limits).reserve(eq("tenant"), any());
        calls.verify(limits).begin(eq("tenant"), argThat(value -> value.requestKey().equals("attempt/attempt")));
        calls.verify(limits).verify(eq("tenant"), any()); calls.verify(locks).lock("tenant", "payment", "acquire");
        calls.verify(inputs).bindToAttempt(eq("tenant"), any(), eq("attempt"), any());
        calls.verify(attempts).update(eq("tenant"), any(), eq(0L));
    }
    @Test void alreadySubmittingAttemptNeverReceivesAnotherTicketOrCallsRemoteDependencies() {
        assertTrue(service.prepare("tenant", "payment", "attempt").isPresent());
        clearInvocations(risk, limits, inputs);
        assertTrue(service.prepare("tenant", "payment", "attempt").isEmpty());
        verifyNoInteractions(risk, inputs); verify(limits, never()).reserve(anyString(), any());
    }
    @Test void anotherWorkersCommittedY2MakesThisCallerLoseTheTicket() {
        when(limits.verify(eq("tenant"), any())).thenAnswer(call -> {
            stored.set(stored.get().beginSubmission(1, "b".repeat(64), RiskDecision.notConnected(), AttemptFixtures.NOW));
            return new LimitExecutionVerificationDTO(true, "VALID");
        });
        assertTrue(service.prepare("tenant", "payment", "attempt").isEmpty());
        verify(attempts, never()).update(anyString(), any(), anyLong()); verifyNoInteractions(inputs);
    }
    @Test void denyFencesAndFailsOnlyTheAttemptThatNeverCrossedY2() {
        when(risk.consult(any())).thenReturn(new RiskDecision(RiskDecisionType.DENY, "RULE_REJECTED", "consultation"));
        assertTrue(service.prepare("tenant", "payment", "attempt").isEmpty());
        assertEquals(PaymentState.FAILED, accepted.get().payment().state());
        assertEquals(PaymentAttemptState.CLOSED, stored.get().attempt().state());
        assertEquals(GateState.FENCED, gate.get().gate().state());
        assertNotNull(accepted.get().terminalEvidenceId());
        verify(limits, never()).reserve(anyString(), any());
        assertTrue(service.prepare("tenant", "payment", "attempt").isEmpty());
        verify(events, times(1)).publish(eq("tenant"), eq(accepted.get()), eq(true), any());
    }
    @Test void mismatchedPermitCannotCrossY2OrConsumeInput() {
        when(limits.begin(eq("tenant"), any())).thenReturn(new LimitExecutionPermitDTO("other-operation", "payment", "attempt", 1, "a".repeat(64)));
        assertSame(EXECUTION_PERMIT_INVALID, assertThrows(CommonException.class,
                () -> service.prepare("tenant", "payment", "attempt")).getResultCode());
        assertEquals(PaymentAttemptState.INIT, stored.get().attempt().state()); verifyNoInteractions(inputs);
    }
    @Test void reservationExpiryProducesOnlyLocalPreSubmissionNoCollectionProof() {
        when(limits.reserve(eq("tenant"), any())).thenReturn(reservation(LimitReservationState.EXPIRED));
        assertSame(LIMIT_RESERVATION_EXPIRED, assertThrows(CommonException.class,
                () -> service.prepare("tenant", "payment", "attempt")).getResultCode());
        assertEquals(PaymentState.FAILED, accepted.get().payment().state());
        assertEquals(0, stored.get().attempt().executionEpoch());
        verify(limits, never()).begin(anyString(), any());
    }
    @Test void inputExpiryRollsBackY2BeforeLocalFailureIsCommittedSeparately() {
        doThrow(new CommonException(INPUT_EXPIRED)).when(inputs).bindToAttempt(eq("tenant"), any(), eq("attempt"), any());
        assertSame(INPUT_EXPIRED, assertThrows(CommonException.class,
                () -> service.prepare("tenant", "payment", "attempt")).getResultCode());
        assertEquals(PaymentState.FAILED, accepted.get().payment().state());
        assertEquals(0, stored.get().attempt().executionEpoch());
        assertEquals(1, manager.rollbacks); assertEquals(1, manager.commits);
    }
    @Test void outerTransactionIsRejectedBeforeAnyQuotaOrRiskRequest() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(INVALID_STATE, assertThrows(CommonException.class,
                    () -> service.prepare("tenant", "payment", "attempt")).getResultCode());
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(risk, limits, payments);
    }
}
