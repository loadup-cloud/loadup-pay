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
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.app.port.PaymentInputCipher;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.dto.H5SceneInteractionDTO;
import io.github.loadup.pay.payment.client.dto.NoInteractionDTO;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentInputGateway;
import io.github.loadup.pay.payment.domain.model.PaymentInput;
import io.github.loadup.pay.payment.domain.model.PaymentInputState;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INPUT_ALREADY_BOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INPUT_EXPIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentInputAccessTest {
    private static final String TENANT = "tenant";
    private static final String ATTEMPT = "attempt";
    private static final String INPUT_REF = "prepared-input";
    private static final LocalDateTime NOW = AttemptFixtures.NOW;

    private final PaymentInputGateway inputs = mock(PaymentInputGateway.class);
    private final PaymentAcceptanceGateway payments = mock(PaymentAcceptanceGateway.class);
    private final PaymentAcceptanceCodec codec = mock(PaymentAcceptanceCodec.class);
    private final PaymentInputCipher cipher = mock(PaymentInputCipher.class);
    private final PaymentAcceptanceServiceTest.UnitTransactions manager = new PaymentAcceptanceServiceTest.UnitTransactions();
    private final PaymentAcceptanceTransactions transactions = new PaymentAcceptanceTransactions(manager);
    private final Clock clock = Clock.fixed(NOW.atZone(BusinessTime.zone()).toInstant(), BusinessTime.zone());
    private final PaymentInputAccess access = new PaymentInputAccess(inputs, payments, codec, cipher, transactions, clock);
    private final PaymentExecuteCommand command = command(INPUT_REF);
    private final StoredPaymentAcceptance accepted = AttemptFixtures.accepted(true);

    private static PaymentExecuteCommand command(String inputRef) {
        var base = PaymentFixtures.command();
        return new PaymentExecuteCommand(base.paymentId(), base.orderId(), base.allocationId(), base.admissionId(),
                base.payeeMerchantId(), base.amountMinor(), base.currency(), base.requestDigest(), base.choice(),
                inputRef, base.admittedTerms(), base.subject(), base.payableUntil());
    }

    private PaymentInput input(PaymentInputState state, String boundAttemptId, LocalDateTime expiresAt) {
        return new PaymentInput(INPUT_REF, command.paymentId(), command.orderId(), command.allocationId(),
                command.payeeMerchantId(), DomainChecks.cnyAmount(command.amountMinor(), command.currency()),
                "action", Channel.WECHAT, PaymentMethod.H5, accepted.callerServiceId(), "v1", "a".repeat(64),
                "vault-key", 2, "vault:v2:ciphertext", NOW.minusMinutes(2), expiresAt, state,
                boundAttemptId, state == PaymentInputState.BOUND ? 1 : 0);
    }

    @Test
    void bindsTheOriginalH5InputInTheAcceptanceTransactionThenOpensOnlyThatAttemptInput() {
        var initInput = input(PaymentInputState.INIT, null, NOW.plusMinutes(2));
        var boundInput = new AtomicReference<PaymentInput>();
        when(codec.decode(accepted)).thenReturn(command);
        when(inputs.lock(TENANT, INPUT_REF)).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return Optional.of(boundInput.get() == null ? initInput : boundInput.get());
        });
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            boundInput.set(call.getArgument(1));
            return null;
        }).when(inputs).bind(eq(TENANT), any(PaymentInput.class), eq(0L));
        when(payments.find(TENANT, command.paymentId())).thenReturn(Optional.of(accepted));
        when(inputs.find(TENANT, INPUT_REF)).thenAnswer(call -> Optional.ofNullable(boundInput.get()));
        var interaction = new H5SceneInteractionDTO("203.0.113.10", io.github.loadup.pay.channel.api.model.H5SceneType.WAP);
        when(cipher.decrypt(TENANT, initInput.bind(ATTEMPT, NOW))).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return interaction;
        });

        transactions.writeLocal(TENANT, () -> {
            access.bindToAttempt(TENANT, accepted, ATTEMPT, NOW);
            return Boolean.TRUE;
        });
        assertEquals(ATTEMPT, boundInput.get().boundAttemptId());
        assertEquals(PaymentInputState.BOUND, boundInput.get().state());
        assertEquals(1, boundInput.get().rowVersion());

        assertSame(interaction, access.openForAttempt(TENANT, command.paymentId(), ATTEMPT));
        verify(inputs).lock(TENANT, INPUT_REF);
        verify(inputs).bind(TENANT, boundInput.get(), 0L);
        verify(cipher).decrypt(TENANT, boundInput.get());
    }

    @Test
    void rejectsExpiredOrAnotherAttemptsBoundInputWithoutDecrypting() {
        when(codec.decode(accepted)).thenReturn(command);
        when(payments.find(TENANT, command.paymentId())).thenReturn(Optional.of(accepted));

        var expired = input(PaymentInputState.BOUND, ATTEMPT, NOW.minusNanos(1));
        when(inputs.find(TENANT, INPUT_REF)).thenReturn(Optional.of(expired));
        assertSame(INPUT_EXPIRED, assertThrows(CommonException.class,
                () -> access.openForAttempt(TENANT, command.paymentId(), ATTEMPT)).getResultCode());

        var foreign = input(PaymentInputState.BOUND, "other-attempt", NOW.plusMinutes(2));
        when(inputs.find(TENANT, INPUT_REF)).thenReturn(Optional.of(foreign));
        assertSame(INPUT_ALREADY_BOUND, assertThrows(CommonException.class,
                () -> access.openForAttempt(TENANT, command.paymentId(), ATTEMPT)).getResultCode());
        verifyNoInteractions(cipher);
    }

    @Test
    void h5WithoutPreparedReferenceReturnsNoInteractionWithoutLookingUpOrDecryptingInput() {
        var noInputCommand = command(null);
        when(codec.decode(accepted)).thenReturn(noInputCommand);
        when(payments.find(TENANT, noInputCommand.paymentId())).thenReturn(Optional.of(accepted));

        assertInstanceOf(NoInteractionDTO.class, access.openForAttempt(TENANT, noInputCommand.paymentId(), ATTEMPT));

        verifyNoInteractions(inputs, cipher);
    }

    @Test
    void newAcceptanceLocksTheMatchingInitInputAndRejectsAnExpiredReference() {
        when(inputs.lock(TENANT, INPUT_REF)).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return Optional.of(input(PaymentInputState.INIT, null, NOW.plusMinutes(2)));
        });
        transactions.writeLocal(TENANT, () -> {
            access.requireNewAcceptance(TENANT, "acquire", command, NOW);
            return Boolean.TRUE;
        });
        verify(inputs).lock(TENANT, INPUT_REF);

        when(inputs.lock(TENANT, INPUT_REF)).thenReturn(Optional.of(input(PaymentInputState.INIT, null, NOW)));
        var failure = assertThrows(CommonException.class, () -> transactions.writeLocal(TENANT, () -> {
            access.requireNewAcceptance(TENANT, "acquire", command, NOW);
            return Boolean.TRUE;
        }));
        assertEquals(INPUT_EXPIRED, failure.getResultCode());
        verify(inputs, never()).bind(any(), any(PaymentInput.class), anyLong());
    }
}
