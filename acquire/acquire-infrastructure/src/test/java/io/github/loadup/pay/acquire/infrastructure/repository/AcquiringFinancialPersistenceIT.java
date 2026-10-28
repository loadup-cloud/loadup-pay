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
package io.github.loadup.pay.acquire.infrastructure.repository;

import com.mybatisflex.core.query.QueryWrapper;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.delayedtask.mapper.DelayedTaskDOMapper;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.components.testcontainers.annotation.ContainerType;
import io.github.loadup.components.testcontainers.annotation.EnableTestContainers;
import io.github.loadup.pay.acquire.app.converter.AcquireOrderConverter;
import io.github.loadup.pay.acquire.app.converter.OrderSnapshotConverter;
import io.github.loadup.pay.acquire.app.port.OrderCreationCodec;
import io.github.loadup.pay.acquire.app.port.OrderSnapshotReader;
import io.github.loadup.pay.acquire.app.port.PaymentAcceptanceClient;
import io.github.loadup.pay.acquire.app.port.PaymentOutcomeClient;
import io.github.loadup.pay.acquire.app.security.AcquireActor;
import io.github.loadup.pay.acquire.app.security.AcquireActorProvider;
import io.github.loadup.pay.acquire.app.service.AllocationRecoveryQueue;
import io.github.loadup.pay.acquire.app.service.MerchantNotificationEvents;
import io.github.loadup.pay.acquire.app.service.OrderCreationTransactions;
import io.github.loadup.pay.acquire.app.service.OrderSnapshotEvents;
import io.github.loadup.pay.acquire.app.service.PaymentInitiationOperations;
import io.github.loadup.pay.acquire.app.service.PaymentInitiationService;
import io.github.loadup.pay.acquire.app.service.PaymentOutcomeOperations;
import io.github.loadup.pay.acquire.app.service.PaymentProgressService;
import io.github.loadup.pay.acquire.client.command.PaymentProgressRefreshCommand;
import io.github.loadup.pay.acquire.client.command.PaymentStartCommand;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.gateway.PaymentOutcomeGateway;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.AllocationState;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.acquire.infrastructure.codec.OrderCreationCodecImpl;
import io.github.loadup.pay.acquire.infrastructure.codec.PaymentInitiationCodecImpl;
import io.github.loadup.pay.acquire.infrastructure.converter.OrderCreationStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.mapper.AcquireOrderDOMapper;
import io.github.loadup.pay.acquire.infrastructure.mapper.AllocationRecoveryCursorDOMapper;
import io.github.loadup.pay.acquire.infrastructure.mapper.FeeAdjustmentDOMapper;
import io.github.loadup.pay.acquire.infrastructure.mapper.OrderFeeDOMapper;
import io.github.loadup.pay.acquire.infrastructure.mapper.OrderPaymentAllocationDOMapper;
import io.github.loadup.pay.acquire.infrastructure.mapper.OrderPaymentOutcomeDOMapper;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.dto.NoInteractionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentOutcomeDTO;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.testify.starter.scenario.TestScenario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.ALLOCATION_RECOVERY_CURSOR_DO;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.FEE_ADJUSTMENT_DO;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.ORDER_FEE_DO;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.ORDER_PAYMENT_ALLOCATION_DO;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.ORDER_PAYMENT_OUTCOME_DO;
import static io.github.loadup.pay.acquire.infrastructure.repository.AcquiringPersistenceFixtures.AT;
import static io.github.loadup.pay.acquire.infrastructure.repository.AcquiringPersistenceFixtures.OWNER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Real A2/A3, native tasks, fee and Outbox persistence. Only the cross-service payment response is simulated.
 */
@SpringBootTest(classes = AcquiringFinancialPersistenceIT.Application.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnableTestContainers(value = ContainerType.POSTGRESQL, reuse = false)
class AcquiringFinancialPersistenceIT {
    private final OrderCreationGateway orders;
    private final PaymentOutcomeGateway outcomes;
    private final OrderCreationCodec codec;
    private final AcquireOrderConverter converter;
    private final OrderCreationTransactions transactions;
    private final PaymentInitiationService starts;
    private final PaymentProgressService progress;
    private final OrderPaymentAllocationDOMapper allocations;
    private final OrderPaymentOutcomeDOMapper receipts;
    private final AllocationRecoveryCursorDOMapper cursors;
    private final DelayedTaskDOMapper nativeTasks;
    private final OrderFeeDOMapper fees;
    private final FeeAdjustmentDOMapper adjustments;
    private final JdbcTemplate jdbc;
    private final TestScenario scenario;
    private final RemotePayment remote;

    @Autowired
    AcquiringFinancialPersistenceIT(OrderCreationGateway orders, PaymentOutcomeGateway outcomes, OrderCreationCodec codec,
                                    AcquireOrderConverter converter, OrderCreationTransactions transactions, PaymentInitiationService starts,
                                    PaymentProgressService progress, OrderPaymentAllocationDOMapper allocations, OrderPaymentOutcomeDOMapper receipts,
                                    AllocationRecoveryCursorDOMapper cursors, DelayedTaskDOMapper nativeTasks, OrderFeeDOMapper fees,
                                    FeeAdjustmentDOMapper adjustments, JdbcTemplate jdbc, TestScenario scenario, RemotePayment remote) {
        this.orders = orders;
        this.outcomes = outcomes;
        this.codec = codec;
        this.converter = converter;
        this.transactions = transactions;
        this.starts = starts;
        this.progress = progress;
        this.allocations = allocations;
        this.receipts = receipts;
        this.cursors = cursors;
        this.nativeTasks = nativeTasks;
        this.fees = fees;
        this.adjustments = adjustments;
        this.jdbc = jdbc;
        this.scenario = scenario;
        this.remote = remote;
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    @BeforeEach
    void resetFaults() {
        remote.failSnapshot.set(false);
        clearInvocations(remote.acceptance, remote.outcomes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ORDER", "DIRECT_PAY"})
    void originalCreationModesRebuildFromTheirOwnPayloadAndDigest(String mode) {
        var fixture = seed(mode);
        transactions.readSnapshot(fixture.tenant(), () -> {
            var rebuilt = orders.findOrder(fixture.tenant(), fixture.stored().order().id()).orElseThrow();
            assertEquals(fixture.stored(), rebuilt);
            return true;
        });
    }

    @Test
    void concurrentSameActionCreatesOneHoldOneAllocationOneWakeupAndOneOutboxSnapshot() throws Exception {
        var fixture = seed("ORDER");
        var command = startCommand(fixture, 2000L);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<io.github.loadup.pay.acquire.client.dto.PaymentStartDTO> call = () -> {
                ready.countDown();
                await(start);
                return starts.startPayment(command);
            };
            var first = workers.submit(call);
            var second = workers.submit(call);
            await(ready);
            start.countDown();
            assertEquals(first.get(15, TimeUnit.SECONDS).paymentId(), second.get(15, TimeUnit.SECONDS).paymentId());
        } finally {
            start.countDown();
        }
        transactions.readSnapshot(fixture.tenant(), () -> {
            var order = current(fixture).order();
            assertEquals(2000L, order.paymentReservedMinor());
            assertEquals(1, order.reservedPaymentCount());
            assertEquals(1L, order.version());
            assertEquals(0L, order.paidAmountMinor());
            assertEquals(1L, allocationCount(fixture));
            assertEquals(1L, cursorCount(fixture));
            assertEquals(1L, nativeCount(fixture));
            assertEquals(1L, outboxCount(fixture));
            return true;
        });
    }

    @Test
    void a2FaultAfterRealOutboxPublishRollsBackHoldAllocationWakeupCursorAndOutbox() {
        var fixture = seed("ORDER");
        remote.failSnapshot.set(true);
        assertThrows(IllegalStateException.class, () -> starts.startPayment(startCommand(fixture, 2000L)));
        transactions.readSnapshot(fixture.tenant(), () -> {
            assertEquals(AcquireOrderState.INIT, current(fixture).order().state());
            assertEquals(0L, current(fixture).order().paymentReservedMinor());
            assertEquals(0L, allocationCount(fixture));
            assertEquals(0L, cursorCount(fixture));
            assertEquals(0L, nativeCount(fixture));
            assertEquals(0L, outboxCount(fixture));
            return true;
        });
        verify(remote.acceptance, never()).accept(anyString(), any());
    }

    @Test
    void a3OutboxFaultRollsBackMoneyReceiptFeeAndOutboxButKeepsRemoteSuccessRecoverable() {
        var fixture = seed("ORDER");
        String payment = starts.startPayment(startCommand(fixture, 10000L)).paymentId();
        remote.successful.add(payment);
        remote.failSnapshot.set(true);
        assertThrows(IllegalStateException.class, () -> progress.refresh(new PaymentProgressRefreshCommand(fixture.stored().order().id(), payment)));
        transactions.readSnapshot(fixture.tenant(), () -> {
            var order = current(fixture).order();
            assertEquals(AcquireOrderState.PENDING, order.state());
            assertEquals(0L, order.paidAmountMinor());
            assertEquals(10000L, order.paymentReservedMinor());
            assertEquals(AllocationState.HELD, outcomes.findPayment(fixture.tenant(), order.id(), payment).orElseThrow().allocation().state());
            assertEquals(0L, receiptCount(fixture));
            assertEquals("INIT", feeState(fixture));
            assertEquals(0L, adjustmentCount(fixture));
            assertEquals(1L, outboxCount(fixture));
            return true;
        });
        progress.refresh(new PaymentProgressRefreshCommand(fixture.stored().order().id(), payment));
        progress.refresh(new PaymentProgressRefreshCommand(fixture.stored().order().id(), payment));
        assertPaidOnce(fixture, payment);
    }

    @Test
    void concurrentSameTerminalFactAppliesMoneyReceiptFeeAndSnapshotOnlyOnce() throws Exception {
        var fixture = seed("ORDER");
        String payment = starts.startPayment(startCommand(fixture, 10000L)).paymentId();
        remote.successful.add(payment);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var query = new PaymentProgressRefreshCommand(fixture.stored().order().id(), payment);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<Boolean> call = () -> {
                ready.countDown();
                await(start);
                progress.refresh(query);
                return true;
            };
            var first = workers.submit(call);
            var second = workers.submit(call);
            await(ready);
            start.countDown();
            assertTrue(first.get(15, TimeUnit.SECONDS));
            assertTrue(second.get(15, TimeUnit.SECONDS));
        } finally {
            start.countDown();
        }
        assertPaidOnce(fixture, payment);
    }

    @Test
    void conflictingEvidenceAfterACommittedReceiptCannotChangeMoneyOrPublishAgain() {
        var fixture = seed("ORDER");
        String payment = starts.startPayment(startCommand(fixture, 10000L)).paymentId();
        remote.successful.add(payment);
        var query = new PaymentProgressRefreshCommand(fixture.stored().order().id(), payment);
        progress.refresh(query);
        remote.conflictingEvidence.add(payment);
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> progress.refresh(query)).getResultCode());
        assertPaidOnce(fixture, payment);
    }

    private AcquiringPersistenceFixtures.Fixture seed(String mode) {
        var fixture = AcquiringPersistenceFixtures.order(codec, converter, mode);
        transactions.writeLocal(fixture.tenant(), () -> {
            orders.insertIntent(fixture.tenant(), fixture.intent());
            orders.insertOrder(fixture.tenant(), fixture.stored(), fixture.fee(), fixture.feePayload());
            orders.updateIntent(fixture.tenant(), fixture.intent().finish("COMPLETED", fixture.admission().admissionId(),
                    BusinessTime.local(fixture.admission().materializeBefore()), "SUCCESS"), 0L);
            return true;
        });
        remote.actor.set(new AcquireActor(fixture.tenant(), OWNER, null, Set.of("pay:acquire:payment:start", "pay:acquire:payment:refresh")));
        return fixture;
    }

    private PaymentStartCommand startCommand(AcquiringPersistenceFixtures.Fixture fixture, long minor) {
        return new PaymentStartCommand(fixture.stored().order().id(), "original-action", minor,
                new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5), new NoInteractionDTO());
    }

    private StoredAcquireOrder current(AcquiringPersistenceFixtures.Fixture fixture) {
        return orders.findOrder(fixture.tenant(), fixture.stored().order().id()).orElseThrow();
    }

    private void assertPaidOnce(AcquiringPersistenceFixtures.Fixture fixture, String payment) {
        transactions.readSnapshot(fixture.tenant(), () -> {
            var order = current(fixture).order();
            assertEquals(AcquireOrderState.PAID, order.state());
            assertEquals(10000L, order.paidAmountMinor());
            assertEquals(0L, order.paymentReservedMinor());
            assertEquals(1, order.successfulPaymentCount());
            assertEquals(0, order.reservedPaymentCount());
            assertEquals(2L, order.version());
            assertEquals(AllocationState.CONSUMED, outcomes.findPayment(fixture.tenant(), order.id(), payment).orElseThrow().allocation().state());
            assertEquals(1L, receiptCount(fixture));
            assertEquals("CONFIRMED", feeState(fixture));
            assertEquals(1L, adjustmentCount(fixture));
            assertEquals(2L, outboxCount(fixture));
            return true;
        });
        scenario.assertDb("acquire_order").where("tenant_id", fixture.tenant()).where("id", fixture.stored().order().id())
                .has("paid_amount_minor", 10000L).has("payment_reserved_minor", 0L).has("state", "PAID");
    }

    private long allocationCount(AcquiringPersistenceFixtures.Fixture f) {
        return allocations.selectCountByQuery(QueryWrapper.create().where(ORDER_PAYMENT_ALLOCATION_DO.TENANT_ID.eq(f.tenant()))
                .and(ORDER_PAYMENT_ALLOCATION_DO.ORDER_ID.eq(f.stored().order().id())));
    }

    private long receiptCount(AcquiringPersistenceFixtures.Fixture f) {
        return receipts.selectCountByQuery(QueryWrapper.create().where(ORDER_PAYMENT_OUTCOME_DO.TENANT_ID.eq(f.tenant()))
                .and(ORDER_PAYMENT_OUTCOME_DO.ORDER_ID.eq(f.stored().order().id())));
    }

    private long cursorCount(AcquiringPersistenceFixtures.Fixture f) {
        return cursors.selectCountByQuery(QueryWrapper.create().where(ALLOCATION_RECOVERY_CURSOR_DO.TENANT_ID.eq(f.tenant()))
                .and(ALLOCATION_RECOVERY_CURSOR_DO.ORDER_ID.eq(f.stored().order().id())));
    }

    private long nativeCount(AcquiringPersistenceFixtures.Fixture f) {
        return nativeTasks.selectCountByQuery(QueryWrapper.create().where(
                io.github.loadup.components.delayedtask.dataobject.table.Tables.DELAYED_TASK_DO.TENANT_ID.eq(f.tenant())));
    }

    private long outboxCount(AcquiringPersistenceFixtures.Fixture f) {
        return Objects.requireNonNull(jdbc.queryForObject("SELECT COUNT(*) FROM loadup_outbox_event WHERE tenant_id = ? AND business_id = ? AND event_type = ?",
                Long.class, f.tenant(), f.stored().order().id(), OrderSnapshotEvents.TYPE));
    }

    private String feeState(AcquiringPersistenceFixtures.Fixture f) {
        return fees.selectOneByQuery(QueryWrapper.create().where(ORDER_FEE_DO.TENANT_ID.eq(f.tenant()))
                .and(ORDER_FEE_DO.ORDER_ID.eq(f.stored().order().id()))).getState();
    }

    private long adjustmentCount(AcquiringPersistenceFixtures.Fixture f) {
        return adjustments.selectCountByQuery(QueryWrapper.create().where(FEE_ADJUSTMENT_DO.TENANT_ID.eq(f.tenant()))
                .and(FEE_ADJUSTMENT_DO.ORDER_ID.eq(f.stored().order().id())));
    }

    static final class RemotePayment {
        final AtomicReference<AcquireActor> actor = new AtomicReference<>();
        final AtomicBoolean failSnapshot = new AtomicBoolean();
        final Map<String, PaymentExecuteCommand> commands = new ConcurrentHashMap<>();
        final Set<String> successful = ConcurrentHashMap.newKeySet();
        final Set<String> conflictingEvidence = ConcurrentHashMap.newKeySet();
        final PaymentAcceptanceClient acceptance = mock(PaymentAcceptanceClient.class);
        final io.github.loadup.pay.acquire.app.port.PaymentClosureClient closure = mock(io.github.loadup.pay.acquire.app.port.PaymentClosureClient.class);
        final PaymentOutcomeClient outcomes = mock(PaymentOutcomeClient.class);

        RemotePayment() {
            when(acceptance.supportsTenant(anyString())).thenReturn(true);
            when(closure.supportsTenant(anyString())).thenReturn(true);
            when(outcomes.supportsTenant(anyString())).thenReturn(true);
            when(acceptance.accept(anyString(), any())).thenAnswer(c -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                PaymentExecuteCommand command = c.getArgument(1);
                String key = c.getArgument(0) + "/" + command.paymentId();
                commands.compute(key, (ignored, old) -> {
                    if (old != null) assertEquals(old, command);
                    return command;
                });
                return response(command, false);
            });
            when(outcomes.outcome(anyString(), anyString())).thenAnswer(c -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                PaymentExecuteCommand command = commands.get(c.getArgument(0) + "/" + c.getArgument(1));
                assertNotNull(command);
                return new PaymentOutcomeDTO(response(command, successful.contains(command.paymentId())), false);
            });
        }

        PaymentExecutionDTO response(PaymentExecuteCommand c, boolean succeeded) {
            String evidence = succeeded ? (conflictingEvidence.contains(c.paymentId()) ? "conflicting-proof" : "original-proof") : null;
            return new PaymentExecutionDTO(c.paymentId(), c.orderId(), c.allocationId(), c.amountMinor(), c.currency(),
                    succeeded ? PaymentState.SUCCEEDED : PaymentState.INIT, succeeded ? "remote-attempt" : null, null, evidence,
                    succeeded ? 3L : 0L, BusinessTime.external(AT));
        }
    }

    static final class FaultingSnapshotEvents extends OrderSnapshotEvents {
        private final RemotePayment remote;

        FaultingSnapshotEvents(OrderSnapshotReader reader, OrderSnapshotConverter converter, OutboxTemplate outbox, IdGenerator ids,
                               MerchantNotificationEvents notifications, RemotePayment remote) {
            super(reader, converter, outbox, ids, notifications);
            this.remote = remote;
        }

        @Override
        public void publish(String tenant, String orderId, long expectedVersion) {
            super.publish(tenant, orderId, expectedVersion);
            if (remote.failSnapshot.getAndSet(false))
                throw new IllegalStateException("Injected after real snapshot Outbox publication");
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = AcquireOrderDOMapper.class)
    @ComponentScan(basePackageClasses = {OrderCreationStorageConverter.class, AcquireOrderConverter.class})
    @Import({OrderCreationGatewayImpl.class, PaymentInitiationGatewayImpl.class, PaymentOutcomeGatewayImpl.class,
            AllocationRecoveryGatewayImpl.class, OrderSnapshotReaderImpl.class, OrderFeeGatewayImpl.class, MerchantNotificationGatewayImpl.class,
            OrderCreationCodecImpl.class, PaymentInitiationCodecImpl.class, OrderCreationTransactions.class, AllocationRecoveryQueue.class,
            PaymentInitiationOperations.class, PaymentInitiationService.class, PaymentOutcomeOperations.class, PaymentProgressService.class,
            MerchantNotificationEvents.class})
    static class Application {
        @Bean("acquireClock")
        Clock acquireClock() {
            return Clock.fixed(AT.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        }

        @Bean
        RemotePayment remotePayment() {
            return new RemotePayment();
        }

        @Bean
        AcquireActorProvider actorProvider(RemotePayment remote) {
            return remote.actor::get;
        }

        @Bean
        PaymentAcceptanceClient acceptance(RemotePayment remote) {
            return remote.acceptance;
        }

        @Bean
        io.github.loadup.pay.acquire.app.port.PaymentClosureClient closure(RemotePayment remote) {
            return remote.closure;
        }

        @Bean
        PaymentOutcomeClient outcomes(RemotePayment remote) {
            return remote.outcomes;
        }

        @Bean
        OrderSnapshotEvents snapshotEvents(OrderSnapshotReader reader, OrderSnapshotConverter converter, OutboxTemplate outbox,
                                           IdGenerator ids, MerchantNotificationEvents notifications, RemotePayment remote) {
            return new FaultingSnapshotEvents(reader, converter, outbox, ids, notifications, remote);
        }
    }
}
