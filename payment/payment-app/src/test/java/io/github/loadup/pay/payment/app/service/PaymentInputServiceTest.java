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
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.channel.api.model.PayerIdentityType;
import io.github.loadup.pay.payment.app.converter.PaymentChannelInputConverter;
import io.github.loadup.pay.payment.app.converter.PaymentInputConverter;
import io.github.loadup.pay.payment.app.port.PaymentInputCipher;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand;
import io.github.loadup.pay.payment.client.dto.ChannelIdentityInteractionDTO;
import io.github.loadup.pay.payment.client.dto.H5SceneInteractionDTO;
import io.github.loadup.pay.payment.client.dto.NoInteractionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.pay.payment.client.dto.PaymentCodeInteractionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentInteractionDTO;
import io.github.loadup.pay.payment.client.query.PaymentInputLookupQuery;
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
import static org.junit.jupiter.api.Assertions.assertNull;
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
    final PaymentInputPublication publication = mock(PaymentInputPublication.class);
    final PaymentInputCipher cipher = mock(PaymentInputCipher.class);
    final AtomicReference<PaymentInput> persisted = new AtomicReference<>();
    final AtomicReference<PaymentActor> actor = new AtomicReference<>(new PaymentActor(
            "tenant", "acquire", Set.of("pay:payment:input:prepare")));
    final PaymentAcceptanceServiceTest.UnitTransactions manager = new PaymentAcceptanceServiceTest.UnitTransactions();
    final Clock clock = Clock.fixed(Instant.parse("2026-10-22T04:00:00.123456Z"), BusinessTime.zone());
    final PaymentInputPrepareCommand command = command(PaymentMethod.PAYMENT_CODE,
            new PaymentCodeInteractionDTO("synthetic-payer-code"));
    final PaymentInputService service = new PaymentInputService(actor::get, store, publication, cipher,
            context.getBean(PaymentInputConverter.class), context.getBean(PaymentChannelInputConverter.class),
            new PaymentAcceptanceTransactions(manager),
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
    void aCloseCommittedWhileKmsWasInFlightPreventsPublishingTheLateCiphertext() {
        org.mockito.Mockito.doThrow(new CommonException(io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.EXECUTION_FENCED))
                .when(publication).requireNew(any(), any(), any());
        assertEquals(io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.EXECUTION_FENCED,
                assertThrows(CommonException.class, () -> service.prepare(command)).getResultCode());
        assertNull(persisted.get());
        verify(store, never()).insert(anyString(), any());
        verify(cipher).encrypt("tenant", "acquire", "opaque-reference", command);
    }

    @Test
    void anExistingExactInputReplaysEvenAfterPublicationHasBeenFenced() {
        var first = service.prepare(command);
        clearInvocations(publication, cipher);
        org.mockito.Mockito.doThrow(new CommonException(io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.EXECUTION_FENCED))
                .when(publication).requireNew(any(), any(), any());
        assertEquals(first, service.prepare(command));
        verifyNoInteractions(publication);
        verify(cipher, never()).encrypt(anyString(), anyString(), anyString(), any());
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
        var later = new PaymentInputService(actor::get, store, publication, cipher, context.getBean(PaymentInputConverter.class),
                context.getBean(PaymentChannelInputConverter.class),
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
    void jsapiRequiresMatchingChannelAndH5NoneCannotPrepareAnInput() {
        var invalid = command(PaymentMethod.JSAPI,
                new ChannelIdentityInteractionDTO(Channel.ALIPAY, "synthetic-open-id", "application", PayerIdentityType.OPEN_ID));
        assertSame(INVALID_ARGUMENT, assertThrows(CommonException.class, () -> service.prepare(invalid)).getResultCode());
        assertSame(INVALID_ARGUMENT, assertThrows(CommonException.class,
                () -> service.prepare(command(PaymentMethod.H5, new NoInteractionDTO()))).getResultCode());
        verifyNoInteractions(cipher, store);
    }

    @Test
    void preparesH5SceneInputThroughTheSameEncryptedLifecycle() {
        var h5 = command(PaymentMethod.H5, new H5SceneInteractionDTO("203.0.113.10",
                io.github.loadup.pay.channel.api.model.H5SceneType.WAP));

        var result = service.prepare(h5);

        assertEquals("opaque-reference", result.inputRef());
        assertEquals(LocalDateTime.now(clock).plusSeconds(120), persisted.get().expiresAt());
        assertEquals(PaymentInputState.INIT, persisted.get().state());
        assertEquals(1, manager.commits);
        verify(cipher).encrypt(eq("tenant"), eq("acquire"), eq("opaque-reference"), eq(h5));
    }

    @Test
    void changedH5SceneOrIpConflictsForTheSamePayment() {
        var original = command(PaymentMethod.H5, new H5SceneInteractionDTO("203.0.113.10",
                io.github.loadup.pay.channel.api.model.H5SceneType.WAP));
        service.prepare(original);

        var changedIp = command(PaymentMethod.H5, new H5SceneInteractionDTO("203.0.113.11",
                io.github.loadup.pay.channel.api.model.H5SceneType.WAP));
        when(cipher.fingerprint("tenant", "acquire", changedIp, "v1")).thenReturn("b".repeat(64));
        assertSame(IDEMPOTENCY_CONFLICT,
                assertThrows(CommonException.class, () -> service.prepare(changedIp)).getResultCode());

        var changedScene = command(PaymentMethod.H5, new H5SceneInteractionDTO("203.0.113.10",
                io.github.loadup.pay.channel.api.model.H5SceneType.ANDROID));
        when(cipher.fingerprint("tenant", "acquire", changedScene, "v1")).thenReturn("c".repeat(64));
        assertSame(IDEMPOTENCY_CONFLICT,
                assertThrows(CommonException.class, () -> service.prepare(changedScene)).getResultCode());
        verify(store, times(1)).insert(anyString(), any());
    }

    @Test
    void invalidH5IpLiteralsAreRejectedBeforeCipherOrStorage() {
        for (var invalidIp : new String[]{"payer.example", "203.0.113.10, 203.0.113.11", "fe80::1%en0",
                "[2001:db8::1]", "0.0.0.0", "127.0.0.1", "224.0.0.1"}) {
            var invalid = command(PaymentMethod.H5, new H5SceneInteractionDTO(invalidIp,
                    io.github.loadup.pay.channel.api.model.H5SceneType.WAP));
            assertSame(INVALID_ARGUMENT,
                    assertThrows(CommonException.class, () -> service.prepare(invalid)).getResultCode(), invalidIp);
        }
        verifyNoInteractions(cipher, store);
    }

    @Test
    void h5SceneRequiresH5AndH5RequiresSceneInput() {
        assertSame(INVALID_ARGUMENT, assertThrows(CommonException.class,
                () -> service.prepare(command(PaymentMethod.H5, new NoInteractionDTO()))).getResultCode());
        var scene = new H5SceneInteractionDTO("203.0.113.10", io.github.loadup.pay.channel.api.model.H5SceneType.WAP);
        assertSame(INVALID_ARGUMENT, assertThrows(CommonException.class,
                () -> service.prepare(command(PaymentMethod.JSAPI, scene))).getResultCode());
        assertSame(INVALID_ARGUMENT, assertThrows(CommonException.class,
                () -> service.prepare(command(PaymentMethod.PAYMENT_CODE, scene))).getResultCode());
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

    PaymentInputLookupQuery lookupQuery() {
        return new PaymentInputLookupQuery(command.paymentId(), command.orderId(), command.allocationId(), command.payeeMerchantId(),
                command.amountMinor(), command.currency(), command.inputKey(), command.choice());
    }

    @Test
    void lookupReturnsOnlyOriginalMetadataWithoutCipherOrAnotherWrite() {
        var prepared = service.prepare(command);
        actor.set(new PaymentActor("tenant", "acquire", Set.of("pay:payment:input:read")));
        clearInvocations(cipher, store);
        var result = service.lookup(lookupQuery());
        assertTrue(result.found());
        assertEquals(prepared, result.input());
        assertFalse(result.toString().contains("synthetic-payer-code"));
        assertFalse(result.toString().contains("opaque-reference"));
        verifyNoInteractions(cipher);
        verify(store, never()).insert(anyString(), any());
        verify(store, never()).bind(anyString(), any(), org.mockito.ArgumentMatchers.anyLong());
        assertEquals(1, manager.commits);
    }

    @Test
    void missingInputIsExplicitAndBoundExpiredInputRetainsItsOriginalReferenceAndDeadline() {
        actor.set(new PaymentActor("tenant", "acquire", Set.of("pay:payment:input:read", "pay:payment:input:prepare")));
        var missing = service.lookup(lookupQuery());
        assertFalse(missing.found());
        assertNull(missing.input());
        var prepared = service.prepare(command);
        persisted.set(persisted.get().bind("attempt", LocalDateTime.now(clock)));
        var original = persisted.get();
        var later = new PaymentInputService(actor::get, store, publication, cipher, context.getBean(PaymentInputConverter.class),
                context.getBean(PaymentChannelInputConverter.class), new PaymentAcceptanceTransactions(manager),
                validation.getValidator(), Clock.offset(clock, Duration.ofDays(1)), "acquire");
        clearInvocations(cipher, store);
        assertEquals(prepared, later.lookup(lookupQuery()).input());
        assertSame(original, persisted.get());
        verifyNoInteractions(cipher);
        verify(store, never()).insert(anyString(), any());
        verify(store, never()).bind(anyString(), any(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void lookupRejectsWrongOriginalScopeRatherThanReturningAnotherInput() {
        service.prepare(command);
        actor.set(new PaymentActor("tenant", "acquire", Set.of("pay:payment:input:read")));
        var q = lookupQuery();
        for (var changed : java.util.List.of(
                new PaymentInputLookupQuery(q.paymentId(), "other-order", q.allocationId(), q.payeeMerchantId(), q.amountMinor(), q.currency(), q.inputKey(), q.choice()),
                new PaymentInputLookupQuery(q.paymentId(), q.orderId(), "other-allocation", q.payeeMerchantId(), q.amountMinor(), q.currency(), q.inputKey(), q.choice()),
                new PaymentInputLookupQuery(q.paymentId(), q.orderId(), q.allocationId(), "other-merchant", q.amountMinor(), q.currency(), q.inputKey(), q.choice()),
                new PaymentInputLookupQuery(q.paymentId(), q.orderId(), q.allocationId(), q.payeeMerchantId(), q.amountMinor() + 1, q.currency(), q.inputKey(), q.choice()),
                new PaymentInputLookupQuery(q.paymentId(), q.orderId(), q.allocationId(), q.payeeMerchantId(), q.amountMinor(), q.currency(), "other-action", q.choice()),
                new PaymentInputLookupQuery(q.paymentId(), q.orderId(), q.allocationId(), q.payeeMerchantId(), q.amountMinor(), q.currency(), q.inputKey(),
                        new PaymentChoiceDTO(Channel.ALIPAY, PaymentMethod.PAYMENT_CODE)))) {
            assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service.lookup(changed)).getResultCode());
        }
    }

    @Test
    void lookupNeedsItsOwnPermissionAndRejectsWrongServiceAndOuterTransactions() {
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.lookup(lookupQuery())).getResultCode());
        actor.set(new PaymentActor("tenant", "unrelated", Set.of("pay:payment:input:read")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.lookup(lookupQuery())).getResultCode());
        actor.set(new PaymentActor("tenant", "acquire", Set.of("pay:payment:input:read")));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> service.lookup(lookupQuery())).getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(cipher, store);
    }

    @Test
    void lookupRejectsAnInputStoredForAnotherCallerService() {
        var now = LocalDateTime.now(clock);
        persisted.set(context.getBean(PaymentInputConverter.class).toInput(command, "opaque-reference", "other-service", "v1", "a".repeat(64),
                new EncryptedPaymentInput("key", 1, "ciphertext"), now, now.plusMinutes(2)));
        actor.set(new PaymentActor("tenant", "acquire", Set.of("pay:payment:input:read")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.lookup(lookupQuery())).getResultCode());
        verifyNoInteractions(cipher);
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
