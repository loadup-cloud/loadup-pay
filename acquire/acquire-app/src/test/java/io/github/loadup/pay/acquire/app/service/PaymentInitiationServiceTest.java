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
package io.github.loadup.pay.acquire.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.acquire.app.converter.PaymentInitiationConverter;
import io.github.loadup.pay.acquire.app.port.*;
import io.github.loadup.pay.acquire.app.security.AcquireActor;
import io.github.loadup.pay.acquire.client.command.PaymentStartCommand;
import io.github.loadup.pay.acquire.domain.gateway.*;
import io.github.loadup.pay.acquire.domain.model.*;
import io.github.loadup.pay.common.*;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.dto.*;
import io.github.loadup.pay.payment.client.enums.PaymentState;
import jakarta.validation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.*;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentInitiationServiceTest {
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(PaymentInitiationConverter.class.getPackageName());
    final OrderCreationGateway orders = mock(OrderCreationGateway.class);
    final PaymentInitiationGateway allocations = mock(PaymentInitiationGateway.class);
    final OrderCreationCodec hashes = mock(OrderCreationCodec.class);
    final PaymentInitiationCodec codec = mock(PaymentInitiationCodec.class);
    final PaymentAcceptanceClient payment = mock(PaymentAcceptanceClient.class);
    final OrderSnapshotEvents snapshots = mock(OrderSnapshotEvents.class);
    final AtomicReference<AcquireActor> actor =
            new AtomicReference<>(new AcquireActor("tenant", OWNER, null, Set.of("pay:acquire:payment:start")));
    final AtomicReference<StoredAcquireOrder> order =
            new AtomicReference<>(order(AcquireOrderState.INIT, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10)));
    final Map<String, StoredPaymentAllocation> saved = new HashMap<>();
    final Map<String, PaymentExecuteCommand> commands = new HashMap<>();
    final AtomicInteger ids = new AtomicInteger();
    final UnitTransactions tx = new UnitTransactions();

    PaymentInitiationService service(Clock clock) {
        return new PaymentInitiationService(
                actor::get,
                orders,
                allocations,
                hashes,
                codec,
                payment,
                context.getBean(PaymentInitiationConverter.class),
                new OrderCreationTransactions(tx),
                () -> "id-" + ids.incrementAndGet(),
                validation.getValidator(),
                clock, snapshots);
    }

    @BeforeEach
    void setup() {
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(order.get().order().version(), (long) c.getArgument(2));
            return null;
        }).when(snapshots).publish(eq("tenant"), eq("order"), anyLong());
        when(orders.lockOrder("tenant", "order")).thenAnswer(c -> order.get());
        when(hashes.decodeAdmission(anyString(), anyString())).thenReturn(admission());
        when(hashes.checksum(anyString()))
                .thenAnswer(c -> HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256")
                                .digest(((String) c.getArgument(0)).getBytes(StandardCharsets.UTF_8))));
        when(payment.supportsTenant("tenant")).thenReturn(true);
        when(codec.encode(any())).thenAnswer(c -> {
            PaymentExecuteCommand value = c.getArgument(0);
            commands.put(value.paymentId(), value);
            return value.paymentId();
        });
        when(codec.decode(any()))
                .thenAnswer(c -> commands.get(((StoredPaymentAllocation) c.getArgument(0)).intentPayload()));
        when(allocations.findAction(eq("tenant"), eq("order"), anyString(), anyString()))
                .thenAnswer(c -> Optional.ofNullable(saved.get(c.getArgument(3))));
        doAnswer(c -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    AcquireOrder changed = c.getArgument(1);
                    assertEquals(order.get().order().version(), (long) c.getArgument(3));
                    var old = order.get();
                    order.set(new StoredAcquireOrder(
                            changed,
                            old.merchantOrderNo(),
                            old.subject(),
                            old.admission(),
                            old.admittedAt(),
                            old.callerScopeHash(),
                            old.snapshotId(),
                            old.admissionPayload(),
                            old.payloadHash()));
                    StoredPaymentAllocation value = c.getArgument(2);
                    saved.put(value.allocation().actionKey(), value);
                    return null;
                })
                .when(allocations)
                .reserve(eq("tenant"), any(), any(), anyLong());
        when(payment.accept(eq("tenant"), any())).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(tx.commits > 0);
            return response(c.getArgument(1));
        });
    }

    PaymentStartCommand command(String key, long amount) {
        return new PaymentStartCommand(
                "order", key, amount, new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5), null);
    }

    PaymentExecutionDTO response(PaymentExecuteCommand value) {
        return new PaymentExecutionDTO(
                value.paymentId(),
                value.orderId(),
                value.allocationId(),
                value.amountMinor(),
                value.currency(),
                PaymentState.INIT,
                null,
                null,
                null,
                0,
                BusinessTime.external(NOW));
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    @Test
    void commitsAmountHoldBeforeSubmittingTheFrozenOriginalTerms() {
        var result = service(clock(NOW)).startPayment(command("one", 4000));
        assertEquals(4000, order.get().order().paymentReservedMinor());
        assertEquals(0, order.get().order().paidAmountMinor());
        assertEquals(1, order.get().order().reservedPaymentCount());
        assertEquals(AcquireOrderState.PENDING, order.get().order().state());
        var allocation = saved.get("one");
        assertEquals(AllocationState.HELD, allocation.allocation().state());
        assertEquals(allocation.allocation().paymentId(), result.paymentId());
        var frozen = commands.get(result.paymentId());
        assertEquals(admission().terms(), frozen.admittedTerms().terms());
        assertEquals(10000, frozen.admittedTerms().orderAmountMinor());
        assertNull(frozen.preparedInputRef());
        verify(snapshots).publish("tenant", "order", order.get().order().version());
    }

    @Test
    void aLostRemoteReplyRetainsTheHoldAndRetryUsesExactlyTheSamePaymentAndTerms() {
        when(payment.accept(eq("tenant"), any())).thenThrow(new CommonException(DEPENDENCY_UNAVAILABLE));
        assertSame(
                DEPENDENCY_UNAVAILABLE,
                assertThrows(CommonException.class, () -> service(clock(NOW)).startPayment(command("one", 4000)))
                        .getResultCode());
        var original = saved.get("one");
        var frozen = commands.get(original.allocation().paymentId());
        when(payment.accept(eq("tenant"), any())).thenReturn(response(frozen));
        var result = service(clock(NOW.plusHours(1))).startPayment(command("one", 4000));
        assertEquals(frozen.paymentId(), result.paymentId());
        assertSame(original, saved.get("one"));
        assertEquals(4000, order.get().order().paymentReservedMinor());
        verify(allocations, times(1)).reserve(eq("tenant"), any(), any(), anyLong());
        verify(payment, times(2)).accept("tenant", frozen);
        verify(snapshots, times(1)).publish(eq("tenant"), eq("order"), anyLong());
    }

    @Test
    void changedAmountForTheSameActionConflictsBeforeAnySecondSubmission() {
        service(clock(NOW)).startPayment(command("one", 4000));
        clearInvocations(payment);
        assertSame(
                IDEMPOTENCY_CONFLICT,
                assertThrows(CommonException.class, () -> service(clock(NOW)).startPayment(command("one", 5000)))
                        .getResultCode());
        verify(payment, never()).accept(anyString(), any());
        assertEquals(4000, order.get().order().paymentReservedMinor());
    }

    @Test
    void separateActionsShareTheOrderAvailableAmountAndCannotOverReserve() {
        service(clock(NOW)).startPayment(command("one", 4000));
        assertSame(
                AMOUNT_EXCEEDED,
                assertThrows(CommonException.class, () -> service(clock(NOW)).startPayment(command("too-much", 6001)))
                        .getResultCode());
        assertEquals(1, saved.size());
        service(clock(NOW)).startPayment(command("two", 6000));
        assertEquals(10000, order.get().order().paymentReservedMinor());
        assertEquals(2, order.get().order().reservedPaymentCount());
        assertEquals(0, order.get().order().paidAmountMinor());
    }

    @Test
    void malformedRemoteResultDoesNotReleaseTheCommittedHold() {
        when(payment.accept(eq("tenant"), any()))
                .thenReturn(new PaymentExecutionDTO(
                        "foreign",
                        "order",
                        "foreign",
                        4000,
                        "CNY",
                        PaymentState.INIT,
                        null,
                        null,
                        null,
                        0,
                        BusinessTime.external(NOW)));
        assertSame(
                SNAPSHOT_INVALID,
                assertThrows(CommonException.class, () -> service(clock(NOW)).startPayment(command("one", 4000)))
                        .getResultCode());
        assertEquals(4000, order.get().order().paymentReservedMinor());
        assertEquals(1, saved.size());
    }

    @Test
    void deadlinePreventsANewActionButDoesNotPreventExistingActionRecovery() {
        service(clock(NOW)).startPayment(command("one", 4000));
        service(clock(NOW.plusHours(1))).startPayment(command("one", 4000));
        assertSame(
                ORDER_NOT_PAYABLE,
                assertThrows(
                                CommonException.class,
                                () -> service(clock(NOW.plusHours(1))).startPayment(command("new", 1000)))
                        .getResultCode());
        assertEquals(1, saved.size());
    }

    @Test
    void releasedActionOnlyQueriesItsOriginalPaymentAndNeverCreatesAnotherHold() {
        service(clock(NOW)).startPayment(command("one", 4000));
        var held = saved.get("one");
        var value = held.allocation();
        var released = new OrderPaymentAllocation(
                value.id(),
                value.orderId(),
                value.paymentId(),
                value.actorScope(),
                value.actionKey(),
                value.requestDigest(),
                value.amount(),
                AllocationState.RELEASED,
                1);
        saved.put(
                "one",
                new StoredPaymentAllocation(
                        released, held.callerScopeHash(), held.intentPayload(), held.payloadHash(), 1));
        when(payment.detail("tenant", value.paymentId())).thenReturn(response(commands.get(value.paymentId())));
        clearInvocations(payment, allocations);
        assertEquals(
                value.paymentId(),
                service(clock(NOW)).startPayment(command("one", 4000)).paymentId());
        verify(payment).detail("tenant", value.paymentId());
        verify(payment, never()).accept(anyString(), any());
        verify(allocations, never()).reserve(anyString(), any(), any(), anyLong());
    }

    @Test
    void callerWithoutPermissionCannotReadOrReserveAnOrder() {
        actor.set(new AcquireActor("tenant", OWNER, null, Set.of()));
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> service(clock(NOW)).startPayment(command("one", 4000)))
                        .getResultCode());
        verifyNoInteractions(orders, allocations, payment);
    }

    @Test
    void anotherOwnerAndAnUnconfiguredTenantAreRejectedBeforeHoldingMoney() {
        actor.set(new AcquireActor("tenant", "b".repeat(64), null, Set.of("pay:acquire:payment:start")));
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> service(clock(NOW)).startPayment(command("one", 4000)))
                        .getResultCode());
        actor.set(new AcquireActor("other", OWNER, null, Set.of("pay:acquire:payment:start")));
        assertSame(
                ACTION_FORBIDDEN,
                assertThrows(CommonException.class, () -> service(clock(NOW)).startPayment(command("one", 4000)))
                        .getResultCode());
        verifyNoInteractions(allocations);
    }

    @Test
    void channelOutsideTheOriginalCapabilitiesCannotCreateAnAllocation() {
        var unsupported = new PaymentStartCommand(
                "order", "one", 1000, new PaymentChoiceDTO(Channel.ALIPAY, PaymentMethod.H5), null);
        assertSame(
                UNSUPPORTED_CAPABILITY,
                assertThrows(CommonException.class, () -> service(clock(NOW)).startPayment(unsupported))
                        .getResultCode());
        assertTrue(saved.isEmpty());
    }

    @Test
    void anOuterTransactionCannotWrapTheRemoteSubmission() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(
                    INVALID_STATE,
                    assertThrows(
                                    CommonException.class,
                                    () -> service(clock(NOW)).startPayment(command("one", 4000)))
                            .getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(orders, allocations, payment);
    }
}
