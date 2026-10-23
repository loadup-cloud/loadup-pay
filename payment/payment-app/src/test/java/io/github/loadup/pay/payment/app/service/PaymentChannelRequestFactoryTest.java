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
import io.github.loadup.pay.channel.api.model.ChannelPaymentInput;
import io.github.loadup.pay.channel.api.model.ChannelPaymentRequest;
import io.github.loadup.pay.channel.api.model.NoChannelInput;
import io.github.loadup.pay.channel.api.model.PreparedChannelPayment;
import io.github.loadup.pay.payment.app.converter.PaymentChannelInputConverter;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.dto.NoInteractionDTO;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.model.PaymentAttempt;
import io.github.loadup.pay.payment.domain.model.PaymentAttemptState;
import io.github.loadup.pay.payment.domain.model.RiskDecision;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.PAYMENT_EXPIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentChannelRequestFactoryTest {
    private static final String TENANT = "tenant";
    private static final String HASH = "d".repeat(64);

    private final PaymentAcceptanceGateway payments = mock(PaymentAcceptanceGateway.class);
    private final PaymentAttemptGateway attempts = mock(PaymentAttemptGateway.class);
    private final PaymentAcceptanceCodec codec = mock(PaymentAcceptanceCodec.class);
    private final PaymentInputAccess inputs = mock(PaymentInputAccess.class);
    private final PaymentChannelInputConverter converter = mock(PaymentChannelInputConverter.class);
    private final PaymentAcceptanceTransactions transactions = mock(PaymentAcceptanceTransactions.class);
    private final PaymentChannelRequestFactory factory = new PaymentChannelRequestFactory(payments, attempts, codec,
            inputs, converter, transactions, Clock.fixed(AttemptFixtures.NOW.atZone(BusinessTime.zone()).toInstant(), BusinessTime.zone()));

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void buildsChannelRequestFromOriginalAcceptanceAndBoundInputForSubmittingAttempt() {
        var command = PaymentFixtures.command();
        var storedAttempt = submittingAttempt();
        var acceptance = AttemptFixtures.accepted(true);
        var prepared = prepared(storedAttempt.attempt());
        var plaintext = new NoInteractionDTO();
        ChannelPaymentInput channelInput = new NoChannelInput();
        var expected = new ChannelPaymentRequest(prepared, command.subject(), command.payableUntil(), channelInput);
        stubRead(acceptance, storedAttempt, command, plaintext, channelInput, expected);

        assertSame(expected, factory.create(TENANT, prepared));

        verify(payments).find(TENANT, prepared.identity().paymentId());
        verify(attempts).find(TENANT, prepared.identity().attemptId());
        verify(codec).decode(acceptance);
        verify(inputs).openForAttempt(TENANT, prepared.identity().paymentId(), prepared.identity().attemptId());
        verify(converter).toInput(plaintext);
        verify(converter).toRequest(prepared, command, channelInput);
    }

    @Test
    void rejectsStaleAttemptAndEveryChangedPreparedOrPersistedFactBeforeOpeningInput() {
        var command = PaymentFixtures.command();
        var submitting = submittingAttempt();
        var staleAttempt = submitting.attempt();
        var staleStoredAttempt = copy(submitting, new PaymentAttempt(staleAttempt.id(), staleAttempt.paymentId(),
                staleAttempt.attemptNo(), staleAttempt.amount(), staleAttempt.binding(), staleAttempt.method(),
                staleAttempt.channelRequestNo(), PaymentAttemptState.PENDING, staleAttempt.executionEpoch(), null),
                submitting.callerServiceId(), submitting.paymentRequestDigest(), submitting.preparedInputRef());
        var acceptance = AttemptFixtures.accepted(true);
        var stalePrepared = prepared(staleStoredAttempt.attempt());
        stubRecords(acceptance, staleStoredAttempt, command);
        assertThrows(CommonException.class, () -> factory.create(TENANT, stalePrepared));
        verifyNoInteractions(inputs, converter);

        var storedAttempt = submittingAttempt();
        var validPrepared = prepared(storedAttempt.attempt());
        var identity = validPrepared.identity();
        var changedPreparedFacts = new PreparedChannelPayment[] {
                new PreparedChannelPayment(new io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity(
                        identity.paymentId(), identity.attemptId(), identity.channel(), identity.bindingRevisionId(),
                        identity.accountScopeHash(), "different-request"), validPrepared.amount(), validPrepared.method(),
                        validPrepared.executionEpoch(), validPrepared.preparedInputRef()),
                new PreparedChannelPayment(identity, Money.ofMinor(4001, "CNY"), validPrepared.method(),
                        validPrepared.executionEpoch(), validPrepared.preparedInputRef()),
                new PreparedChannelPayment(identity, validPrepared.amount(), io.github.loadup.commons.payment.PaymentMethod.JSAPI,
                        validPrepared.executionEpoch(), validPrepared.preparedInputRef()),
                new PreparedChannelPayment(identity, validPrepared.amount(), validPrepared.method(), 2,
                        validPrepared.preparedInputRef()),
                new PreparedChannelPayment(identity, validPrepared.amount(), validPrepared.method(),
                        validPrepared.executionEpoch(), "changed-input")
        };
        for (var changed : changedPreparedFacts) {
            clearInvocations(payments, attempts, codec, inputs, converter, transactions);
            stubRecords(acceptance, storedAttempt, command);
            assertThrows(CommonException.class, () -> factory.create(TENANT, changed));
            verifyNoInteractions(inputs, converter);
        }

        var mismatchedDigest = copy(storedAttempt, storedAttempt.attempt(), storedAttempt.callerServiceId(),
                "e".repeat(64), storedAttempt.preparedInputRef());
        clearInvocations(payments, attempts, codec, inputs, converter, transactions);
        stubRecords(acceptance, mismatchedDigest, command);
        assertThrows(CommonException.class, () -> factory.create(TENANT, validPrepared));
        verifyNoInteractions(inputs, converter);

        var mismatchedCaller = copy(storedAttempt, storedAttempt.attempt(), "other-caller", storedAttempt.paymentRequestDigest(),
                storedAttempt.preparedInputRef());
        clearInvocations(payments, attempts, codec, inputs, converter, transactions);
        stubRecords(acceptance, mismatchedCaller, command);
        assertThrows(CommonException.class, () -> factory.create(TENANT, validPrepared));
        verifyNoInteractions(inputs, converter);
    }

    @Test
    void rejectsExpiredFrozenDeadlineWithoutDecryptingBoundInput() {
        var command = withDeadline(PaymentFixtures.command(), OffsetDateTime.parse("2020-01-01T00:00:00Z"));
        var acceptance = AttemptFixtures.accepted(true);
        var storedAttempt = submittingAttempt();
        var prepared = prepared(storedAttempt.attempt());
        stubRecords(acceptance, storedAttempt, command);
        when(transactions.read(eq(TENANT), any())).thenAnswer(invocation -> invocation.<Supplier<ChannelPaymentRequest>>getArgument(1).get());
        when(codec.decode(acceptance)).thenReturn(command);

        var thrown = assertThrows(CommonException.class, () -> factory.create(TENANT, prepared));

        assertEquals(PAYMENT_EXPIRED, thrown.getResultCode());
        verify(inputs, never()).openForAttempt(any(), any(), any());
        verifyNoInteractions(converter);
    }

    @Test
    void rejectsInvocationInsideTransactionBeforeReadingOrOpeningInput() {
        var prepared = prepared(submittingAttempt().attempt());
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            var thrown = assertThrows(CommonException.class, () -> factory.create(TENANT, prepared));
            assertEquals(INVALID_STATE, thrown.getResultCode());
        } finally {
            TransactionSynchronizationManager.clear();
        }
        verifyNoInteractions(payments, attempts, codec, inputs, converter, transactions);
    }

    private void stubRead(StoredPaymentAcceptance acceptance, StoredPaymentAttempt storedAttempt,
            PaymentExecuteCommand command, NoInteractionDTO plaintext, ChannelPaymentInput channelInput,
            ChannelPaymentRequest expected) {
        stubRecords(acceptance, storedAttempt, command);
        when(transactions.read(eq(TENANT), any())).thenAnswer(invocation -> invocation.<Supplier<ChannelPaymentRequest>>getArgument(1).get());
        when(inputs.openForAttempt(TENANT, expected.payment().identity().paymentId(), expected.payment().identity().attemptId()))
                .thenReturn(plaintext);
        when(converter.toInput(plaintext)).thenReturn(channelInput);
        when(converter.toRequest(expected.payment(), command, channelInput)).thenReturn(expected);
    }

    private void stubRecords(StoredPaymentAcceptance acceptance, StoredPaymentAttempt storedAttempt,
            PaymentExecuteCommand command) {
        when(payments.find(TENANT, "payment")).thenReturn(Optional.of(acceptance));
        when(attempts.find(TENANT, "attempt")).thenReturn(Optional.of(storedAttempt));
        when(codec.decode(acceptance)).thenReturn(command);
    }

    private static StoredPaymentAttempt submittingAttempt() {
        return AttemptFixtures.prepared().beginSubmission(1, HASH, RiskDecision.notConnected(), AttemptFixtures.NOW);
    }

    private static PreparedChannelPayment prepared(PaymentAttempt attempt) {
        return new PreparedChannelPayment(attempt.identity(), attempt.amount(), attempt.method(), attempt.executionEpoch(),
                AttemptFixtures.prepared().preparedInputRef());
    }

    private static StoredPaymentAttempt copy(StoredPaymentAttempt source, PaymentAttempt attempt, String caller,
            String paymentRequestDigest, String preparedInputRef) {
        return new StoredPaymentAttempt(attempt, source.limitOperationId(), caller, source.requestKey(), source.requestDigest(),
                paymentRequestDigest, preparedInputRef, source.riskDecision(), source.executionPermitHash(),
                source.preparedAt(), source.submissionStartedAt(), source.rowVersion());
    }

    private static PaymentExecuteCommand withDeadline(PaymentExecuteCommand command, OffsetDateTime deadline) {
        return new PaymentExecuteCommand(command.paymentId(), command.orderId(), command.allocationId(), command.admissionId(),
                command.payeeMerchantId(), command.amountMinor(), command.currency(), command.requestDigest(), command.choice(),
                command.preparedInputRef(), command.admittedTerms(), command.subject(), deadline);
    }
}
