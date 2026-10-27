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
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.payment.app.converter.PaymentAcceptanceConverter;
import io.github.loadup.pay.payment.app.converter.PaymentAttemptConverter;
import io.github.loadup.pay.payment.app.converter.PaymentControlConverter;
import io.github.loadup.pay.payment.app.model.LockedPayment;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.app.port.PaymentLimitClient;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.client.command.PaymentAttemptStartCommand;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentControlGateway;
import io.github.loadup.pay.payment.domain.model.PaymentAttemptState;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.OUTCOME_UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentAttemptServiceTest {
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(PaymentAttemptConverter.class.getPackageName());
    final PaymentExecutionLocks locks = mock(PaymentExecutionLocks.class);
    final PaymentAttemptGateway attempts = mock(PaymentAttemptGateway.class);
    final PaymentControlGateway payments = mock(PaymentControlGateway.class);
    final PaymentAcceptanceCodec codec = mock(PaymentAcceptanceCodec.class);
    final PaymentRouteSelector routes = mock(PaymentRouteSelector.class);
    final PaymentInputAccess inputs = mock(PaymentInputAccess.class);
    final PaymentLimitClient limits = mock(PaymentLimitClient.class);
    final AtomicReference<StoredPaymentAcceptance> accepted = new AtomicReference<>(AttemptFixtures.accepted(false));
    final AtomicReference<StoredPaymentAttempt> stored = new AtomicReference<>();
    final PaymentAcceptanceServiceTest.UnitTransactions manager = new PaymentAcceptanceServiceTest.UnitTransactions();
    final Clock clock = Clock.fixed(AttemptFixtures.NOW.atZone(BusinessTime.zone()).toInstant(), BusinessTime.zone());
    final PaymentInitialAttemptService initial = new PaymentInitialAttemptService(
            routes,
            inputs,
            attempts,
            payments,
            codec,
            context.getBean(PaymentAttemptConverter.class),
            context.getBean(PaymentControlConverter.class),
            () -> "attempt",
            clock);
    final PaymentAttemptService service = new PaymentAttemptService(
            () -> new PaymentActor("tenant", "acquire", Set.of("pay:payment:attempt:start")),
            locks,
            attempts,
            codec,
            limits,
            context.getBean(PaymentAcceptanceConverter.class),
            new PaymentAcceptanceTransactions(manager),
            validation.getValidator(),
            "acquire",
            initial);

    PaymentAttemptStartCommand command(String key) {
        var original = AttemptFixtures.command();
        return new PaymentAttemptStartCommand(original.paymentId(), key, original.choice(), original.preparedInputRef(), null);
    }

    PaymentExecutionDTO start(String key) {
        return TenantUtil.callWithTenant("tenant", () -> service.startAttempt(command(key)));
    }

    @BeforeEach
    void setup() {
        when(limits.supportsTenant("tenant")).thenReturn(true);
        when(locks.lock(eq("tenant"), anyString(), eq("acquire"))).thenAnswer(call -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return PaymentExecutionBindings.locked(accepted.get(), AttemptFixtures.gate(true), AttemptFixtures.gate(false));
        });
        when(codec.decode(any())).thenReturn(AttemptFixtures.command());
        when(codec.checksum(anyString())).thenAnswer(call -> HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(((String) call.getArgument(0)).getBytes(StandardCharsets.UTF_8))));
        when(attempts.findRequest(eq("tenant"), anyString(), anyString())).thenAnswer(call ->
                stored.get() != null && stored.get().requestKey().equals(call.getArgument(2)) ? Optional.of(stored.get()) : Optional.empty());
        when(routes.select(eq("tenant"), any())).thenReturn(AttemptFixtures.binding());
        doAnswer(call -> {
            stored.set(call.getArgument(1));
            return null;
        }).when(attempts).insert(eq("tenant"), any());
        doAnswer(call -> {
            accepted.set(call.getArgument(1));
            return null;
        }).when(payments).updatePayment(eq("tenant"), any(), anyLong());
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    @Test
    void freezesRouteAndThirtyTwoCharacterRequestBeforeAnyRemoteExecution() {
        var result = start("initial");
        assertEquals("PROCESSING", result.state().name());
        assertEquals("attempt", result.activeAttemptId());
        assertEquals(PaymentAttemptState.INIT, stored.get().attempt().state());
        assertTrue(stored.get().attempt().channelRequestNo().matches("[0-9a-f]{32}"));
        assertEquals("binding-revision", stored.get().attempt().binding().id());
        assertNull(stored.get().riskDecision());
        assertEquals(1, manager.commits);
        verify(limits, never()).reserve(anyString(), any());
        verify(limits, never()).begin(anyString(), any());
    }

    @Test
    void replayDoesNotSelectAnotherRouteOrRequestNumber() {
        var first = start("initial");
        var original = stored.get();
        clearInvocations(routes, attempts, payments);
        assertEquals(first, start("initial"));
        assertSame(original, stored.get());
        verifyNoInteractions(routes);
        verify(attempts, never()).insert(anyString(), any());
        verify(payments, never()).updatePayment(anyString(), any(), anyLong());
    }

    @Test
    void anotherKeyCannotCreateABlindReplacementAttempt() {
        start("initial");
        assertSame(OUTCOME_UNKNOWN, assertThrows(CommonException.class, () -> start("other")).getResultCode());
        verify(attempts, times(1)).insert(anyString(), any());
    }

    @Test
    void failureRollsBackBothAttemptAndPaymentUpdateBoundary() {
        doThrow(new CommonException(CONCURRENT_UPDATE)).when(payments).updatePayment(eq("tenant"), any(), anyLong());
        assertSame(CONCURRENT_UPDATE, assertThrows(CommonException.class, () -> start("initial")).getResultCode());
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
    }
}
