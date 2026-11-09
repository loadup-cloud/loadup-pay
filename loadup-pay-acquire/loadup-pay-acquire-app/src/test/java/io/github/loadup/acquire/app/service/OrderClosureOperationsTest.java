/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.acquire.app.service;

import static io.github.loadup.acquire.app.service.CheckoutFixtures.NOW;
import static io.github.loadup.acquire.app.service.CheckoutFixtures.OWNER;
import static io.github.loadup.acquire.app.service.CheckoutFixtures.UnitTransactions;
import static io.github.loadup.acquire.app.service.CheckoutFixtures.clock;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.loadup.acquire.app.converter.AcquireOrderConverter;
import io.github.loadup.acquire.app.converter.OrderClosureConverter;
import io.github.loadup.acquire.app.model.payment.dto.ParentFenceDTO;
import io.github.loadup.acquire.app.port.OrderCreationCodec;
import io.github.loadup.acquire.app.port.PaymentClosureClient;
import io.github.loadup.acquire.client.command.AcquireOrderCloseCommand;
import io.github.loadup.acquire.client.enums.UnpaidOrderAction;
import io.github.loadup.acquire.domain.gateway.OrderClosureGateway;
import io.github.loadup.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.acquire.domain.model.AcquireOrder;
import io.github.loadup.acquire.domain.model.OrderCloseIntent;
import io.github.loadup.acquire.domain.model.PaymentIntent;
import io.github.loadup.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.util.DigestUtils;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.components.delayedtask.DelayedTaskTemplate;
import io.github.loadup.contracts.event.acquire.AcquireOrderState;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class OrderClosureOperationsTest {
    final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(AcquireOrderConverter.class.getPackageName());
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final OrderCreationGateway orders = mock(OrderCreationGateway.class);
    final OrderClosureGateway store = mock(OrderClosureGateway.class);
    final PaymentClosureClient payment = mock(PaymentClosureClient.class);
    final PaymentOutcomeOperations outcomes = mock(PaymentOutcomeOperations.class);
    final OrderCreationCodec codec = mock(OrderCreationCodec.class);
    final OrderSnapshotEvents snapshots = mock(OrderSnapshotEvents.class);
    final DelayedTaskTemplate nativeTasks = mock(DelayedTaskTemplate.class);
    final OrderClosureTasks tasks = new OrderClosureTasks(nativeTasks, 30);
    final UnitTransactions manager = new UnitTransactions();
    final OrderCreationTransactions transactions = new OrderCreationTransactions(manager);
    final AtomicReference<StoredAcquireOrder> order = new AtomicReference<>(
            CheckoutFixtures.order(AcquireOrderState.INIT, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10)));
    final AtomicReference<OrderCloseIntent> intent = new AtomicReference<>();
    final AtomicInteger ids = new AtomicInteger();
    final OrderClosureOperations operations = new OrderClosureOperations(
            orders,
            store,
            payment,
            outcomes,
            context.getBean(OrderClosureConverter.class),
            context.getBean(AcquireOrderConverter.class),
            codec,
            transactions,
            () -> "close-" + ids.incrementAndGet(),
            validation.getValidator(),
            clock(NOW),
            snapshots,
            tasks);
    final OrderPaymentScope scope = new OrderPaymentScope("tenant", OWNER);
    final AcquireOrderCloseCommand command = new AcquireOrderCloseCommand("order", "original-key", "MERCHANT_CLOSED");

    static StoredAcquireOrder withOrder(StoredAcquireOrder original, AcquireOrder changed) {
        return new StoredAcquireOrder(
                changed,
                original.merchantOrderNo(),
                original.subject(),
                original.admission(),
                original.admittedAt(),
                original.callerScopeHash(),
                original.snapshotId(),
                original.admissionPayload(),
                original.payloadHash());
    }

    @BeforeEach
    void setup() {
        when(codec.checksum(anyString())).thenAnswer(c -> DigestUtils.sha256((String) c.getArgument(0)));
        when(orders.lockOrder("order")).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return order.get();
        });
        when(orders.findOrder("order")).thenAnswer(c -> Optional.of(order.get()));
        when(store.findIntent("order")).thenAnswer(c -> Optional.ofNullable(intent.get()));
        when(store.heldAfter(eq("order"), anyString(), eq(16))).thenReturn(List.of());
        doAnswer(c -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    assertEquals(order.get().order().version(), (long) c.getArgument(2));
                    order.set(withOrder(order.get(), c.getArgument(0)));
                    intent.set(c.getArgument(1));
                    return null;
                })
                .when(store)
                .start(any(), any(), anyLong());
        doAnswer(c -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    assertEquals(intent.get().rowVersion(), (long) c.getArgument(1));
                    intent.set(c.getArgument(0));
                    return null;
                })
                .when(store)
                .advanceCursor(any(), anyLong());
        doAnswer(c -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    order.set(withOrder(order.get(), c.getArgument(0)));
                    return null;
                })
                .when(store)
                .finish(any(), anyLong(), any());
        doAnswer(c -> {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                    return null;
                })
                .when(nativeTasks)
                .register(anyString(), anyString(), any());
        when(payment.fenceParent(any())).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertTrue(manager.commits > 0);
            return new ParentFenceDTO("order", intent.get().closeEpoch(), true, false, 1);
        });
        when(payment.parentFenceStatus(any())).thenAnswer(c -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new ParentFenceDTO("order", intent.get().closeEpoch(), true, false, 1);
        });
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    @Test
    void c0AndEveryCursorAdvanceRegisterANativeContinuationInTheirTransaction() {
        var first = operations.close(scope, command);
        var second = operations.close(scope, command);
        assertEquals(AcquireOrderState.CLOSING, first.state());
        assertEquals(first.orderId(), second.orderId());
        assertEquals(1, ids.get());
        assertEquals(2L, intent.get().rowVersion());
        verify(store, times(1)).start(any(), any(), anyLong());
        verify(nativeTasks).register(OrderClosureTasks.TYPE, "order.0", NOW);
        verify(nativeTasks).register(OrderClosureTasks.TYPE, "order.1", NOW.plusSeconds(30));
        verify(nativeTasks).register(OrderClosureTasks.TYPE, "order.2", NOW.plusSeconds(30));
        assertEquals(command.requestKey(), intent.get().requestKey());
        assertEquals(command.reasonCode(), intent.get().reasonCode());
    }

    @Test
    void failureToEnqueueC0RollsBackBeforeAnyFinancialRpc() {
        var failure = new CommonException(DEPENDENCY_UNAVAILABLE);
        doThrow(failure).when(nativeTasks).register(eq(OrderClosureTasks.TYPE), eq("order.0"), any());
        assertSame(failure, assertThrows(CommonException.class, () -> operations.close(scope, command)));
        assertEquals(1, manager.rollbacks);
        assertEquals(0, manager.commits);
        verify(payment, never()).fenceParent(any());
        verifyNoInteractions(snapshots, outcomes);
    }

    @Test
    void aParentFenceWithoutConvergenceNeverFinishesTheOrder() {
        operations.close(scope, command);
        assertEquals(AcquireOrderState.CLOSING, order.get().order().state());
        verify(store, never()).finish(any(), anyLong(), any());
    }

    @Test
    void authoritativeParentConvergenceAndSettledAllocationsCanFinishClosure() {
        doReturn(new ParentFenceDTO("order", 1L, true, true, 0)).when(payment).parentFenceStatus(any());
        when(store.allAllocationsSettled("order")).thenReturn(true);
        var result = operations.close(scope, command);
        assertEquals(AcquireOrderState.CLOSED, result.state());
        assertEquals(AcquireOrderState.CLOSED, order.get().order().state());
        TenantUtil.runWithTenant(
                "tenant",
                () -> transactions.writeLocal("tenant", () -> {
                    assertFalse(operations.isCurrent(
                            scope, order.get(), intent.get(), intent.get().rowVersion()));
                    return true;
                }));
    }

    @Test
    void generationAndOwnershipAreCheckedWithoutIssuingAnotherCloseIntent() {
        operations.close(scope, command);
        transactions.writeLocal("tenant", () -> {
            assertFalse(operations.isCurrent(scope, order.get(), intent.get(), 0L));
            assertTrue(operations.isCurrent(scope, order.get(), intent.get(), 1L));
            assertSame(
                    SNAPSHOT_INVALID,
                    assertThrows(
                                    CommonException.class,
                                    () -> operations.isCurrent(scope, order.get(), intent.get(), 2L))
                            .getResultCode());
            assertSame(
                    ACTION_FORBIDDEN,
                    assertThrows(
                                    CommonException.class,
                                    () -> operations.isCurrent(
                                            new OrderPaymentScope("tenant", "b".repeat(64)),
                                            order.get(),
                                            intent.get(),
                                            1L))
                            .getResultCode());
            return true;
        });
        assertThrows(
                CommonException.class,
                () -> operations.close(
                        scope, new AcquireOrderCloseCommand("order", "another-key", command.reasonCode())));
        assertEquals(1, ids.get());
    }

    @Test
    void nativeClaimDoesNotReplaceMissingLocalAllocationConvergence() {
        var original = order.get();
        var change = original.order()
                .reservePayment(
                        new PaymentIntent(
                                "allocation",
                                "payment",
                                "merchant/" + OWNER,
                                "action",
                                "e".repeat(64),
                                Money.ofMinor(1000L, "CNY")),
                        NOW);
        order.set(withOrder(original, change.order()));
        doReturn(new ParentFenceDTO("order", 1L, true, true, 0)).when(payment).parentFenceStatus(any());
        when(store.allAllocationsSettled("order")).thenReturn(true);
        operations.close(scope, command);
        assertEquals(1000L, order.get().order().paymentReservedMinor());
        verify(store, never()).finish(any(), anyLong(), any());
    }
}
