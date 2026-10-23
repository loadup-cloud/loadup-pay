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
import io.github.loadup.pay.common.Channel;
import io.github.loadup.pay.common.DomainViolation;
import io.github.loadup.pay.common.PaymentMethod;
import io.github.loadup.pay.payment.app.converter.PaymentInputConverter;
import io.github.loadup.pay.payment.app.port.PaymentInputCipher;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand;
import io.github.loadup.pay.payment.client.dto.ChannelIdentityInteractionDTO;
import io.github.loadup.pay.payment.client.dto.NoInteractionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.pay.payment.client.dto.PaymentCodeInteractionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInteractionDTO;
import io.github.loadup.pay.payment.domain.gateway.PaymentInputGateway;
import io.github.loadup.pay.payment.domain.model.EncryptedPaymentInput;
import io.github.loadup.pay.payment.domain.model.PaymentInput;
import io.github.loadup.pay.payment.domain.model.PaymentInputState;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentInputServiceTest {
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(PaymentInputConverter.class.getPackageName());
    final PaymentInputGateway store = mock(PaymentInputGateway.class);
    final PaymentInputCipher cipher = mock(PaymentInputCipher.class);
    final AtomicReference<PaymentInput> persisted = new AtomicReference<>();
    final AtomicReference<PaymentActor> actor = new AtomicReference<>(new PaymentActor(
            "tenant", "acquire", Set.of("pay:payment:input:prepare")));
    final PaymentAcceptanceServiceTest.UnitTransactions manager = new PaymentAcceptanceServiceTest.UnitTransactions();
    final Clock clock = Clock.fixed(Instant.parse("2026-10-22T04:00:00.123456Z"), BusinessTime.ZONE);
    final PaymentInputPrepareCommand command = command(PaymentMethod.PAYMENT_CODE,
            new PaymentCodeInteractionDTO("synthetic-payer-code"));
    final PaymentInputService service = new PaymentInputService(actor::get, store, cipher,
            context.getBean(PaymentInputConverter.class), new PaymentAcceptanceTransactions(manager),
            validation.getValidator(), clock, "acquire");

    PaymentInputPrepareCommand command(PaymentMethod method, PaymentInteractionDTO input) {
        return new PaymentInputPrepareCommand("payment", "order", "allocation", "merchant", 100, "CNY", "action",
                new PaymentChoiceDTO(Channel.WECHAT, method), input);
    }

    @BeforeEach
    void setup() {
        when(store.findByPayment("tenant", "payment")).thenAnswer(call -> Optional.ofNullable(persisted.get()));
        when(cipher.newReference()).thenReturn("opaque-reference");
        when(cipher.activeFingerprintKeyId()).thenReturn("v1");
        when(cipher.lifetime()).thenReturn(Duration.ofSeconds(120));
        when(cipher.fingerprint(eq("tenant"), eq("acquire"), any(), eq("v1"))).thenReturn("a".repeat(64));
        when(cipher.encrypt(eq("tenant"), eq("acquire"), eq("opaque-reference"), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new EncryptedPaymentInput("payment-input", 2, "vault:v2:c3ludGhldGlj");
        });
        doAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            persisted.set(call.getArgument(1));
            return null;
        }).when(store).insert(eq("tenant"), any());
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    @Test
    void preparesOnlyCiphertextOutsideLocksThenCommitsOneFixedScope() {
        var result = service.prepare(command);
        assertEquals("opaque-reference", result.inputRef());
        assertEquals(LocalDateTime.now(clock).plusSeconds(120), persisted.get().expiresAt());
        assertEquals("vault:v2:c3ludGhldGlj", persisted.get().ciphertext());
        assertEquals(PaymentInputState.INIT, persisted.get().state());
        assertFalse(persisted.get().toString().contains("synthetic-payer-code"));
        assertFalse(persisted.get().toString().contains("vault:v2:"));
        assertEquals(1, manager.commits);
    }

    @Test
    void replayKeepsOriginalReferenceAndExpiryWithoutKmsOrNewTransaction() {
        var first = service.prepare(command);
        clearInvocations(cipher, store);
        assertEquals(first, service.prepare(command));
        verify(cipher, never()).encrypt(anyString(), anyString(), anyString(), any());
        verify(store, never()).insert(anyString(), any());
        assertEquals(1, manager.commits);
    }

    @Test
    void fingerprintRotationReplaysUsingOriginalKeyAndDoesNotRenewExpiredInput() {
        var first = service.prepare(command);
        var later = new PaymentInputService(actor::get, store, cipher, context.getBean(PaymentInputConverter.class),
                new PaymentAcceptanceTransactions(manager), validation.getValidator(),
                Clock.offset(clock, Duration.ofDays(1)), "acquire");
        when(cipher.activeFingerprintKeyId()).thenReturn("v2");
        assertEquals(first, later.prepare(command));
        verify(cipher, times(2)).fingerprint("tenant", "acquire", command, "v1");
        verify(cipher, never()).fingerprint(anyString(), anyString(), any(), eq("v2"));
    }

    @Test
    void changedSensitiveValueWithTheSamePaymentCannotReplaceTheOriginal() {
        service.prepare(command);
        var changed = command(PaymentMethod.PAYMENT_CODE, new PaymentCodeInteractionDTO("another-synthetic-code"));
        when(cipher.fingerprint("tenant", "acquire", changed, "v1")).thenReturn("b".repeat(64));
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service.prepare(changed)).getResultCode());
        verify(store, times(1)).insert(anyString(), any());
    }

    @Test
    void concurrentWinnerIsReplayedOnlyAfterTheLosingInsertRollsBack() {
        doAnswer(call -> {
            persisted.set(call.getArgument(1));
            throw new DuplicateKeyException("Synthetic input race");
        }).when(store).insert(eq("tenant"), any());
        assertEquals("opaque-reference", service.prepare(command).inputRef());
        assertEquals(1, manager.rollbacks);
        assertEquals(1, manager.commits);
        verify(cipher, times(1)).encrypt(anyString(), anyString(), anyString(), any());
    }

    @Test
    void jsapiRequiresMatchingChannelWhileH5DoesNotUseInputPreparation() {
        var invalid = command(PaymentMethod.JSAPI,
                new ChannelIdentityInteractionDTO(Channel.ALIPAY, "synthetic-ticket", "application"));
        assertSame(INVALID_ARGUMENT, assertThrows(CommonException.class, () -> service.prepare(invalid)).getResultCode());
        assertSame(INVALID_ARGUMENT, assertThrows(CommonException.class,
                () -> service.prepare(command(PaymentMethod.H5, new NoInteractionDTO()))).getResultCode());
        verifyNoInteractions(cipher, store);
    }

    @Test
    void otherServiceOrMissingPermissionIsRejectedBeforeStorage() {
        actor.set(new PaymentActor("tenant", "unrelated", Set.of("pay:payment:input:prepare")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.prepare(command)).getResultCode());
        actor.set(new PaymentActor("tenant", "acquire", Set.of()));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.prepare(command)).getResultCode());
        verifyNoInteractions(cipher, store);
    }

    @Test
    void rejectsOuterTransactionBeforeEncryptingOrReadingDatabase() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> service.prepare(command)).getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(cipher, store);
    }

    @Test
    void boundInputCannotBeReusedByAnotherAttemptOrAfterExpiry() {
        service.prepare(command);
        var original = persisted.get();
        var bound = original.bind("attempt", LocalDateTime.now(clock));
        assertSame(bound, bound.bind("attempt", LocalDateTime.now(clock)));
        assertThrows(DomainViolation.class, () -> bound.bind("other", LocalDateTime.now(clock)));
        assertThrows(DomainViolation.class, () -> original.bind("attempt", original.expiresAt()));
        assertEquals(1, bound.rowVersion());
    }
}
