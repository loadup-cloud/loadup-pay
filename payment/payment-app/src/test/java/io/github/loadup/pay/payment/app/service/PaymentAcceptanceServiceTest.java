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
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.pay.common.BusinessTime;
import io.github.loadup.pay.payment.app.converter.PaymentAcceptanceConverter;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.app.security.*;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.model.*;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentAcceptanceServiceTest {
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(PaymentAcceptanceConverter.class.getPackageName());
    final PaymentAcceptanceGateway store = mock(PaymentAcceptanceGateway.class);
    final PaymentAcceptanceCodec codec = mock(PaymentAcceptanceCodec.class);
    final AtomicReference<StoredPaymentAcceptance> stored = new AtomicReference<>();
    final AtomicReference<PaymentActor> actor = new AtomicReference<>(
            new PaymentActor("tenant", "acquire", Set.of("pay:payment:accept", "pay:payment:read")));
    final UnitTransactions manager = new UnitTransactions();
    final AtomicInteger sequence = new AtomicInteger();
    final PaymentExecuteCommand command = PaymentFixtures.command();
    final Clock clock = Clock.fixed(Instant.parse("2026-10-22T04:00:00.123456Z"), BusinessTime.ZONE);
    final PaymentAcceptanceService service = new PaymentAcceptanceService(
            actor::get,
            store,
            codec,
            context.getBean(PaymentAcceptanceConverter.class),
            new PaymentAcceptanceTransactions(manager),
            () -> "id-" + sequence.incrementAndGet(),
            validation.getValidator(),
            clock,
            "acquire");

    @BeforeEach
    void setup() {
        when(store.find(eq("tenant"), anyString())).thenAnswer(c -> Optional.ofNullable(stored.get()));
        when(codec.encode(any())).thenReturn("payload");
        when(codec.checksum(anyString())).thenReturn("c".repeat(64));
        when(codec.decode(any())).thenReturn(command);
        doAnswer(c -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    stored.set(c.getArgument(1));
                    return null;
                })
                .when(store)
                .insert(eq("tenant"), any());
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    @Test
    void acceptsOneInitPaymentAndBothOpenGatesAtomically() {
        var result = service.accept(command);
        assertEquals("INIT", result.state().name());
        assertNull(result.activeAttemptId());
        assertNull(result.action());
        assertEquals(
                LocalDateTime.of(2026, 10, 22, 12, 0, 0, 123456000),
                stored.get().payment().initiatedAt());
        assertEquals(1, manager.commits);
        var calls = inOrder(store);
        calls.verify(store).lockParent("tenant", "order");
        calls.verify(store).insertParent(eq("tenant"), argThat(g -> g.gate().state() == GateState.OPEN));
        calls.verify(store).lockPayment("tenant", "payment");
        calls.verify(store)
                .insertPaymentGate(
                        eq("tenant"), argThat(g -> "payment".equals(g.gate().paymentId())));
        calls.verify(store).insert(eq("tenant"), any());
    }

    @Test
    void replayAndDetailRetainTheOriginalOperationAndAcceptanceTimeWithoutGateWrites() {
        var first = service.accept(command);
        var original = stored.get();
        clearInvocations(store);
        assertEquals(first, service.accept(command));
        assertEquals(first, service.detail(new IdQuery("payment")));
        assertSame(original, stored.get());
        verify(store, never()).lockParent(anyString(), anyString());
        verify(store, never()).insert(anyString(), any());
    }

    @Test
    void changedAmountWithTheSameSuppliedDigestCannotReplay() {
        service.accept(command);
        var changed = new PaymentExecuteCommand(
                command.paymentId(),
                command.orderId(),
                command.allocationId(),
                command.admissionId(),
                command.payeeMerchantId(),
                5000,
                command.currency(),
                command.requestDigest(),
                command.choice(),
                command.preparedInputRef(),
                command.admittedTerms());
        assertSame(
                IDEMPOTENCY_CONFLICT,
                assertThrows(CommonException.class, () -> service.accept(changed))
                        .getResultCode());
        verify(store, times(1)).insert(anyString(), any());
    }

    @Test
    void aFencedParentRejectsLateFirstAcceptanceWithoutOpeningAnyGate() {
        when(store.lockParent("tenant", "order"))
                .thenReturn(Optional.of(new StoredPaymentGate(
                        "gate",
                        "merchant",
                        "acquire",
                        PaymentExecutionGate.init("order", null).fence(2),
                        0)));
        assertSame(
                EXECUTION_FENCED,
                assertThrows(CommonException.class, () -> service.accept(command))
                        .getResultCode());
        verify(store, never()).insertParent(anyString(), any());
        verify(store, never()).lockPayment(anyString(), anyString());
        verify(store, never()).insert(anyString(), any());
        assertEquals(1, manager.rollbacks);
    }

    @Test
    void aFencedPaymentRejectsLateAcceptanceWithoutReplacingTheTombstone() {
        when(store.lockPayment("tenant", "payment"))
                .thenReturn(Optional.of(new StoredPaymentGate(
                        "gate",
                        "merchant",
                        "acquire",
                        PaymentExecutionGate.init("order", "payment").fence(1),
                        0)));
        assertSame(
                EXECUTION_FENCED,
                assertThrows(CommonException.class, () -> service.accept(command))
                        .getResultCode());
        verify(store, never()).insertPaymentGate(anyString(), any());
        verify(store, never()).insert(anyString(), any());
    }

    @Test
    void aParentCannotBeReboundToAnotherPayee() {
        when(store.lockParent("tenant", "order"))
                .thenReturn(Optional.of(new StoredPaymentGate(
                        "gate",
                        "other",
                        "acquire",
                        PaymentExecutionGate.init("order", null).open(),
                        0)));
        assertSame(
                IDEMPOTENCY_CONFLICT,
                assertThrows(CommonException.class, () -> service.accept(command))
                        .getResultCode());
        verify(store, never()).insert(anyString(), any());
    }

    @Test
    void anAllocationCannotFundAnotherPayment() {
        when(store.findAllocation("tenant", "allocation"))
                .thenReturn(Optional.of(new StoredPaymentAcceptance(
                        PaymentOrder.init(
                                "other-payment",
                                "order",
                                "allocation",
                                "merchant",
                                io.github.loadup.pay.common.DomainChecks.amount(4000, "CNY"),
                                "operation",
                                LocalDateTime.now(clock)),
                        "admission",
                        "a".repeat(64),
                        "acquire",
                        "payload",
                        "c".repeat(64),
                        null)));
        assertSame(
                IDEMPOTENCY_CONFLICT,
                assertThrows(CommonException.class, () -> service.accept(command))
                        .getResultCode());
        verify(store, never()).insert(anyString(), any());
    }

    @Test
    void anUnrelatedServiceCannotAcceptOrReadEvenWithBothPermissions() {
        actor.set(new PaymentActor("tenant", "other-service", Set.of("pay:payment:accept", "pay:payment:read")));
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> service.accept(command))
                        .getResultCode());
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> service.detail(new IdQuery("payment")))
                        .getResultCode());
        verifyNoInteractions(store);
    }

    @Test
    void missingPermissionAndOuterTransactionFailBeforeAnyWrite() {
        actor.set(new PaymentActor("tenant", "acquire", Set.of()));
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> service.accept(command))
                        .getResultCode());
        actor.set(new PaymentActor("tenant", "acquire", Set.of("pay:payment:accept")));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(
                    INVALID_STATE,
                    assertThrows(CommonException.class, () -> service.accept(command))
                            .getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(store);
    }

    @Test
    void missingOrMismatchedAdmittedFactsAreRejectedBeforeGateCreation() {
        var changed = new PaymentExecuteCommand(
                "payment",
                "other-order",
                "allocation",
                "admission",
                "merchant",
                4000,
                "CNY",
                "a".repeat(64),
                command.choice(),
                null,
                command.admittedTerms());
        assertSame(
                INVALID_ARGUMENT,
                assertThrows(CommonException.class, () -> service.accept(changed))
                        .getResultCode());
        assertSame(
                INVALID_ARGUMENT,
                assertThrows(CommonException.class, () -> service.accept(null)).getResultCode());
        verifyNoInteractions(store);
    }

    @Test
    void aDuplicateGateRaceRetriesOnlyAfterRollbackAndUsesTheWinningPayment() {
        doAnswer(c -> {
                    stored.set(new StoredPaymentAcceptance(
                            PaymentOrder.init(
                                    "payment",
                                    "order",
                                    "allocation",
                                    "merchant",
                                    io.github.loadup.pay.common.DomainChecks.amount(4000, "CNY"),
                                    "winning-operation",
                                    LocalDateTime.now(clock)),
                            "admission",
                            "a".repeat(64),
                            "acquire",
                            "payload",
                            "c".repeat(64),
                            null));
                    throw new DuplicateKeyException("synthetic concurrent winner");
                })
                .when(store)
                .insertParent(eq("tenant"), any());
        assertEquals("payment", service.accept(command).paymentId());
        assertEquals("winning-operation", stored.get().payment().limitOperationId());
        assertEquals(1, manager.rollbacks);
        assertEquals(1, manager.commits);
        verify(store, never()).insert(anyString(), any());
    }

    @Test
    void storageFailureRollsBackTheWholeAcceptanceBoundary() {
        doThrow(new CommonException(CONCURRENT_UPDATE)).when(store).insert(eq("tenant"), any());
        assertSame(
                CONCURRENT_UPDATE,
                assertThrows(CommonException.class, () -> service.accept(command))
                        .getResultCode());
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
        // Mock ports prove the transaction boundary, not actual database row rollback.
    }

    @Test
    void aChoiceOutsideTheOriginalCapabilitiesIsRejectedBeforePersisting() {
        var changed = new PaymentExecuteCommand(
                command.paymentId(),
                command.orderId(),
                command.allocationId(),
                command.admissionId(),
                command.payeeMerchantId(),
                command.amountMinor(),
                command.currency(),
                command.requestDigest(),
                new io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO(
                        io.github.loadup.pay.common.Channel.ALIPAY, io.github.loadup.pay.common.PaymentMethod.QR),
                null,
                command.admittedTerms());
        assertSame(
                UNSUPPORTED_CAPABILITY,
                assertThrows(CommonException.class, () -> service.accept(changed))
                        .getResultCode());
        verifyNoInteractions(store);
    }

    static final class UnitTransactions extends AbstractPlatformTransactionManager {
        int commits, rollbacks;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object value, TransactionDefinition definition) {
            assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED, definition.getIsolationLevel());
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }
}
