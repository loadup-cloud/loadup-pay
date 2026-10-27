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
import io.github.loadup.commons.payment.*;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.components.database.id.IdGenerator;
import io.github.loadup.components.delayedtask.mapper.DelayedTaskDOMapper;
import io.github.loadup.components.outbox.OutboxTemplate;
import io.github.loadup.components.testcontainers.annotation.*;
import io.github.loadup.pay.acquire.app.converter.*;
import io.github.loadup.pay.acquire.app.port.*;
import io.github.loadup.pay.acquire.app.security.*;
import io.github.loadup.pay.acquire.app.service.*;
import io.github.loadup.pay.acquire.client.command.*;
import io.github.loadup.pay.acquire.domain.gateway.*;
import io.github.loadup.pay.acquire.domain.model.*;
import io.github.loadup.pay.acquire.infrastructure.codec.OrderCreationCodecImpl;
import io.github.loadup.pay.acquire.infrastructure.codec.PaymentInitiationCodecImpl;
import io.github.loadup.pay.acquire.infrastructure.converter.OrderCreationStorageConverter;
import io.github.loadup.pay.acquire.infrastructure.mapper.*;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.dto.*;
import io.github.loadup.pay.payment.client.enums.PaymentState;
import io.github.loadup.testify.starter.scenario.TestScenario;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static io.github.loadup.pay.acquire.infrastructure.repository.AcquiringPersistenceFixtures.*;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.*;
import static io.github.loadup.pay.acquire.infrastructure.dataobject.table.Tables.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real A2/A3, native tasks, fee and Outbox persistence. Only the cross-service payment response is simulated. */
@SpringBootTest(classes = AcquiringFinancialPersistenceIT.Application.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnableTestContainers(value = ContainerType.MYSQL, reuse = false)
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
        this.orders = orders; this.outcomes = outcomes; this.codec = codec; this.converter = converter; this.transactions = transactions;
        this.starts = starts; this.progress = progress; this.allocations = allocations; this.receipts = receipts; this.cursors = cursors;
        this.nativeTasks = nativeTasks; this.fees = fees; this.adjustments = adjustments; this.jdbc = jdbc; this.scenario = scenario; this.remote = remote;
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
                ready.countDown(); await(start); return starts.startPayment(command);
            };
            var first = workers.submit(call);
            var second = workers.submit(call);
            await(ready); start.countDown();
            assertEquals(first.get(15, TimeUnit.SECONDS).paymentId(), second.get(15, TimeUnit.SECONDS).paymentId());
        } finally { start.countDown(); }
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
                ready.countDown(); await(start); progress.refresh(query); return true;
            };
            var first = workers.submit(call); var second = workers.submit(call);
            await(ready); start.countDown();
            assertTrue(first.get(15, TimeUnit.SECONDS)); assertTrue(second.get(15, TimeUnit.SECONDS));
        } finally { start.countDown(); }
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
            assertEquals(10000L, order.paidAmountMinor()); assertEquals(0L, order.paymentReservedMinor());
            assertEquals(1, order.successfulPaymentCount()); assertEquals(0, order.reservedPaymentCount());
            assertEquals(2L, order.version());
            assertEquals(AllocationState.CONSUMED, outcomes.findPayment(fixture.tenant(), order.id(), payment).orElseThrow().allocation().state());
            assertEquals(1L, receiptCount(fixture)); assertEquals("CONFIRMED", feeState(fixture));
            assertEquals(1L, adjustmentCount(fixture)); assertEquals(2L, outboxCount(fixture));
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
    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(10, TimeUnit.SECONDS)); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }

    static final class RemotePayment {
        final AtomicReference<AcquireActor> actor = new AtomicReference<>();
        final AtomicBoolean failSnapshot = new AtomicBoolean();
        final Map<String, PaymentExecuteCommand> commands = new ConcurrentHashMap<>();
        final Set<String> successful = ConcurrentHashMap.newKeySet();
        final Set<String> conflictingEvidence = ConcurrentHashMap.newKeySet();
        final PaymentAcceptanceClient acceptance = mock(PaymentAcceptanceClient.class);
        final PaymentOutcomeClient outcomes = mock(PaymentOutcomeClient.class);
        RemotePayment() {
            when(acceptance.supportsTenant(anyString())).thenReturn(true);
            when(outcomes.supportsTenant(anyString())).thenReturn(true);
            when(acceptance.accept(anyString(), any())).thenAnswer(c -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                PaymentExecuteCommand command = c.getArgument(1);
                String key = c.getArgument(0) + "/" + command.paymentId();
                commands.compute(key, (ignored, old) -> { if (old != null) assertEquals(old, command); return command; });
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
            super(reader, converter, outbox, ids, notifications); this.remote = remote;
        }
        @Override public void publish(String tenant, String orderId, long expectedVersion) {
            super.publish(tenant, orderId, expectedVersion);
            if (remote.failSnapshot.getAndSet(false)) throw new IllegalStateException("Injected after real snapshot Outbox publication");
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
        @Bean("acquireClock") Clock acquireClock() { return Clock.fixed(AT.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()); }
        @Bean RemotePayment remotePayment() { return new RemotePayment(); }
        @Bean AcquireActorProvider actorProvider(RemotePayment remote) { return remote.actor::get; }
        @Bean PaymentAcceptanceClient acceptance(RemotePayment remote) { return remote.acceptance; }
        @Bean PaymentOutcomeClient outcomes(RemotePayment remote) { return remote.outcomes; }
        @Bean OrderSnapshotEvents snapshotEvents(OrderSnapshotReader reader, OrderSnapshotConverter converter, OutboxTemplate outbox,
                IdGenerator ids, MerchantNotificationEvents notifications, RemotePayment remote) {
            return new FaultingSnapshotEvents(reader, converter, outbox, ids, notifications, remote);
        }
    }
}
