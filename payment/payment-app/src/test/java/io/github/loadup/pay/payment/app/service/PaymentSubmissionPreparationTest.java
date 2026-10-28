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
import io.github.loadup.pay.channel.api.model.NoChannelInput;
import io.github.loadup.pay.limitcenter.client.dto.LimitExecutionPermitDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitExecutionVerificationDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitReservationDTO;
import io.github.loadup.pay.limitcenter.client.enums.LimitReservationState;
import io.github.loadup.pay.payment.app.converter.PaymentChannelInputConverter;
import io.github.loadup.pay.payment.app.converter.PaymentControlConverter;
import io.github.loadup.pay.payment.app.converter.PaymentSubmissionConverter;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.app.port.PaymentLimitClient;
import io.github.loadup.pay.payment.client.dto.NoInteractionDTO;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentChannelGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentControlGateway;
import io.github.loadup.pay.payment.domain.gateway.RiskConsultGateway;
import io.github.loadup.pay.payment.domain.model.GateState;
import io.github.loadup.pay.payment.domain.model.PaymentAttemptState;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.domain.model.RiskDecision;
import io.github.loadup.pay.payment.domain.model.RiskDecisionType;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import io.github.loadup.pay.payment.domain.model.StoredPaymentGate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.EXECUTION_PERMIT_INVALID;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INPUT_EXPIRED;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.LIMIT_RESERVATION_EXPIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
    final PaymentEvents events = mock(PaymentEvents.class);
    final PaymentChannelGateway channels = mock(PaymentChannelGateway.class);
    final PaymentChannelInputConverter channelInputs = context.getBean(PaymentChannelInputConverter.class);
    final AtomicReference<StoredPaymentAcceptance> accepted = new AtomicReference<>(AttemptFixtures.accepted(true));
    final AtomicReference<StoredPaymentAttempt> stored = new AtomicReference<>(AttemptFixtures.prepared());
    final AtomicReference<StoredPaymentGate> gate = new AtomicReference<>(AttemptFixtures.gate(false));
    final PaymentAcceptanceServiceTest.UnitTransactions manager = new PaymentAcceptanceServiceTest.UnitTransactions();
    final PaymentSubmissionPreparation service = new PaymentSubmissionPreparation(payments, changes, attempts, locks, inputs, limits,
            risk, codec, context.getBean(PaymentSubmissionConverter.class), context.getBean(PaymentControlConverter.class),
            new PaymentAcceptanceTransactions(manager), () -> "local-no-submission-proof",
            Clock.fixed(AttemptFixtures.NOW.atZone(BusinessTime.zone()).toInstant(), BusinessTime.zone()), events, channels, channelInputs);

    LimitReservationDTO reservation(LimitReservationState state) {
        var policy = AttemptFixtures.command().admittedTerms().terms().limits();
        return new LimitReservationDTO("reservation", "operation", "payment", state, policy.policyId(), policy.policyVersion(),
                BusinessTime.external(AttemptFixtures.NOW), 1);
    }

    LimitExecutionPermitDTO permit() {
        return new LimitExecutionPermitDTO("operation", "payment", "attempt", 1, "e".repeat(64));
    }

    @BeforeEach
    void setup() {
        when(limits.supportsTenant("tenant")).thenReturn(true);
        when(payments.find("tenant", "payment")).thenAnswer(call -> Optional.of(accepted.get()));
        when(attempts.find("tenant", "attempt")).thenAnswer(call -> Optional.of(stored.get()));
        when(attempts.lock("tenant", "attempt")).thenAnswer(call -> Optional.of(stored.get()));
        when(codec.decode(any())).thenReturn(AttemptFixtures.command());
        when(inputs.openForPreparation(eq("tenant"), any(), eq("attempt"))).thenReturn(new NoInteractionDTO());
        when(codec.checksum(anyString())).thenReturn("b".repeat(64));
        when(risk.consult(any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return RiskDecision.notConnected();
        });
        when(limits.reserve(eq("tenant"), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return reservation(LimitReservationState.RESERVED);
        });
        when(limits.begin(eq("tenant"), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return permit();
        });
        when(limits.verify(eq("tenant"), any())).thenReturn(new LimitExecutionVerificationDTO(true, "VALID"));
        when(locks.lock("tenant", "payment", "acquire")).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return PaymentExecutionBindings.locked(accepted.get(), AttemptFixtures.gate(true), gate.get());
        });
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            stored.set(call.getArgument(1));
            return null;
        }).when(attempts).update(eq("tenant"), any(), anyLong());
        doAnswer(call -> {
            accepted.set(call.getArgument(1));
            return null;
        }).when(changes).updatePayment(eq("tenant"), any(), anyLong());
        doAnswer(call -> {
            gate.set(call.getArgument(1));
            return null;
        }).when(changes).updateGate(eq("tenant"), any(), anyLong());
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void remoteRiskAndLimitPreparationPrecedeOneDurableY2Ticket() {
        var result = service.prepare("tenant", "payment", "attempt").orElseThrow();
        assertEquals("attempt", result.attemptId());
        assertEquals(1, result.payment().executionEpoch());
        assertEquals(PaymentAttemptState.SUBMITTING, stored.get().attempt().state());
        assertEquals("b".repeat(64), stored.get().executionPermitHash());
        assertFalse(stored.get().toString().contains("e".repeat(64)));
        var calls = inOrder(risk, inputs, channels, limits, locks, attempts);
        calls.verify(risk).consult(any());
        calls.verify(inputs).openForPreparation(eq("tenant"), any(), eq("attempt"));
        calls.verify(channels).validate(argThat(validation -> validation.input() instanceof NoChannelInput
                && validation.method() == io.github.loadup.commons.payment.PaymentMethod.H5
                && validation.subject().equals(PaymentFixtures.SUBJECT)
                && validation.payableUntil().equals(PaymentFixtures.PAYABLE_UNTIL)));
        calls.verify(limits).reserve(eq("tenant"), any());
        calls.verify(limits).begin(eq("tenant"), argThat(value -> value.requestKey().equals("attempt/attempt")));
        calls.verify(limits).verify(eq("tenant"), any());
        calls.verify(locks).lock("tenant", "payment", "acquire");
        calls.verify(inputs).bindToAttempt(eq("tenant"), any(), eq("attempt"), any());
        calls.verify(attempts).update(eq("tenant"), any(), eq(0L));
    }

    @Test
    void alreadySubmittingAttemptNeverReceivesAnotherTicketOrCallsRemoteDependencies() {
        assertTrue(service.prepare("tenant", "payment", "attempt").isPresent());
        clearInvocations(risk, limits, inputs);
        assertTrue(service.prepare("tenant", "payment", "attempt").isEmpty());
        verifyNoInteractions(risk, inputs);
        verify(limits, never()).reserve(anyString(), any());
    }

    @Test
    void anotherWorkersCommittedY2MakesThisCallerLoseTheTicket() {
        when(limits.verify(eq("tenant"), any())).thenAnswer(call -> {
            stored.set(stored.get().beginSubmission(1, "b".repeat(64), RiskDecision.notConnected(), AttemptFixtures.NOW));
            return new LimitExecutionVerificationDTO(true, "VALID");
        });
        assertTrue(service.prepare("tenant", "payment", "attempt").isEmpty());
        verify(attempts, never()).update(anyString(), any(), anyLong());
        verify(inputs, never()).bindToAttempt(eq("tenant"), any(), eq("attempt"), any());
    }

    @Test
    void aFreshFencedGateViewRejectsY2WithoutBindingInputOrInventingNoCollectionEvidence() {
        var previous = gate.get();
        gate.set(new StoredPaymentGate(previous.id(), previous.payeeMerchantId(), previous.callerServiceId(),
                previous.gate().fence(1L), previous.rowVersion() + 1L));
        var failure = assertThrows(CommonException.class, () -> service.prepare("tenant", "payment", "attempt"));
        assertEquals(io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.EXECUTION_FENCED, failure.getResultCode());
        assertEquals(PaymentAttemptState.INIT, stored.get().attempt().state());
        assertEquals(0L, stored.get().attempt().executionEpoch());
        assertEquals(PaymentState.PROCESSING, accepted.get().payment().state());
        assertNull(accepted.get().terminalEvidenceId());
        verify(inputs, never()).bindToAttempt(anyString(), any(), anyString(), any());
        verify(attempts, never()).update(anyString(), any(), anyLong());
        verify(changes, never()).updatePayment(anyString(), any(), anyLong());
        verifyNoInteractions(events);
    }

    @Test
    void denyFencesAndFailsOnlyTheAttemptThatNeverCrossedY2() {
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

    @Test
    void mismatchedPermitCannotCrossY2OrConsumeInput() {
        when(limits.begin(eq("tenant"), any())).thenReturn(new LimitExecutionPermitDTO("other-operation", "payment", "attempt", 1, "a".repeat(64)));
        assertSame(EXECUTION_PERMIT_INVALID, assertThrows(CommonException.class,
                () -> service.prepare("tenant", "payment", "attempt")).getResultCode());
        assertEquals(PaymentAttemptState.INIT, stored.get().attempt().state());
        verify(inputs, never()).bindToAttempt(eq("tenant"), any(), eq("attempt"), any());
        verify(channels).validate(any());
        verify(channels, never()).submit(any());
    }

    @Test
    void unsupportedChannelPreflightStopsBeforeReservationBeginAndY2() {
        var unsupported = new CommonException(io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.UNSUPPORTED_CAPABILITY);
        doThrow(unsupported).when(channels).validate(any());

        assertSame(unsupported.getResultCode(), assertThrows(CommonException.class,
                () -> service.prepare("tenant", "payment", "attempt")).getResultCode());

        assertEquals(0, stored.get().attempt().executionEpoch());
        verify(inputs).openForPreparation(eq("tenant"), any(), eq("attempt"));
        verify(limits, never()).reserve(anyString(), any());
        verify(limits, never()).begin(anyString(), any());
        verify(channels, never()).submit(any());
    }

    @Test
    void invalidOrExpiredPreflightInputStopsBeforeChannelValidationAndY2() {
        var invalid = new CommonException(io.github.loadup.pay.channel.api.error.ChannelResultCodeEnum.INVALID_ARGUMENT);
        doThrow(invalid).when(inputs).openForPreparation(eq("tenant"), any(), eq("attempt"));
        assertSame(invalid.getResultCode(), assertThrows(CommonException.class,
                () -> service.prepare("tenant", "payment", "attempt")).getResultCode());
        assertEquals(0, stored.get().attempt().executionEpoch());
        verify(channels, never()).validate(any());
        verify(limits, never()).reserve(anyString(), any());
        verify(limits, never()).begin(anyString(), any());
        verify(channels, never()).submit(any());
    }

    @Test
    void expiredBoundInputStopsBeforeReservationBeginAndY2() {
        doThrow(new CommonException(INPUT_EXPIRED)).when(inputs).openForPreparation(eq("tenant"), any(), eq("attempt"));

        assertSame(INPUT_EXPIRED, assertThrows(CommonException.class,
                () -> service.prepare("tenant", "payment", "attempt")).getResultCode());

        assertEquals(0, stored.get().attempt().executionEpoch());
        verify(channels, never()).validate(any());
        verify(limits, never()).reserve(anyString(), any());
        verify(limits, never()).begin(anyString(), any());
        verify(channels, never()).submit(any());
    }

    @Test
    void reservationExpiryProducesOnlyLocalPreSubmissionNoCollectionProof() {
        when(limits.reserve(eq("tenant"), any())).thenReturn(reservation(LimitReservationState.EXPIRED));
        assertSame(LIMIT_RESERVATION_EXPIRED, assertThrows(CommonException.class,
                () -> service.prepare("tenant", "payment", "attempt")).getResultCode());
        assertEquals(PaymentState.FAILED, accepted.get().payment().state());
        assertEquals(0, stored.get().attempt().executionEpoch());
        verify(limits, never()).begin(anyString(), any());
    }

    @Test
    void inputExpiryRollsBackY2BeforeLocalFailureIsCommittedSeparately() {
        doThrow(new CommonException(INPUT_EXPIRED)).when(inputs).bindToAttempt(eq("tenant"), any(), eq("attempt"), any());
        assertSame(INPUT_EXPIRED, assertThrows(CommonException.class,
                () -> service.prepare("tenant", "payment", "attempt")).getResultCode());
        assertEquals(PaymentState.FAILED, accepted.get().payment().state());
        assertEquals(0, stored.get().attempt().executionEpoch());
        assertEquals(1, manager.rollbacks);
        assertEquals(1, manager.commits);
    }

    @Test
    void outerTransactionIsRejectedBeforeAnyQuotaOrRiskRequest() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(INVALID_STATE, assertThrows(CommonException.class,
                    () -> service.prepare("tenant", "payment", "attempt")).getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(risk, limits, payments);
    }
}
