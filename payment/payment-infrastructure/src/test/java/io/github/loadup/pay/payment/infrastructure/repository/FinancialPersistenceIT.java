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
package io.github.loadup.pay.payment.infrastructure.repository;

import com.mybatisflex.core.query.QueryWrapper;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.money.Money;
import io.github.loadup.components.testcontainers.annotation.*;
import io.github.loadup.pay.channel.api.model.*;
import io.github.loadup.pay.limitcenter.client.command.*;
import io.github.loadup.pay.limitcenter.client.dto.*;
import io.github.loadup.pay.limitcenter.client.enums.LimitReservationState;
import io.github.loadup.pay.payment.app.converter.PaymentSubmissionConverter;
import io.github.loadup.pay.payment.app.port.*;
import io.github.loadup.pay.payment.app.service.*;
import io.github.loadup.pay.payment.domain.gateway.*;
import io.github.loadup.pay.payment.domain.model.*;
import io.github.loadup.pay.payment.domain.policy.FifoRefundPolicy;
import io.github.loadup.pay.payment.infrastructure.codec.ChannelBindingCodecImpl;
import io.github.loadup.pay.payment.infrastructure.codec.PaymentAcceptanceCodecImpl;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentRecoveryStorageConverter;
import io.github.loadup.pay.payment.infrastructure.converter.PaymentResultStorageConverter;
import io.github.loadup.pay.payment.infrastructure.mapper.*;
import io.github.loadup.testify.starter.scenario.TestScenario;
import org.junit.jupiter.api.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import static io.github.loadup.pay.payment.infrastructure.repository.FinancialPersistenceFixtures.*;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;
import static io.github.loadup.pay.payment.infrastructure.dataobject.table.Tables.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real local Y2/plan/capacity boundaries. Risk, limitcenter, channel IO and unused terminal events are controlled test doubles. */
@SpringBootTest(classes = FinancialPersistenceIT.Application.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnableTestContainers(value = ContainerType.MYSQL, reuse = false)
class FinancialPersistenceIT {
    private final PaymentAcceptanceTransactions transactions;
    private final PaymentSubmissionPreparation preparation;
    private final PaymentAcceptanceGateway payments;
    private final PaymentAttemptGateway attempts;
    private final PaymentExecutionLocks locks;
    private final ChannelBindingManagementGateway bindings;
    private final PaymentAcceptanceCodec codec;
    private final ChannelBindingCodec bindingCodec;
    private final RefundGateway refunds;
    private final PaymentRefundCapacityDOMapper capacities;
    private final PaymentResultStorageConverter capacityConverter;
    private final PaymentAttemptDOMapper attemptRows;
    private final RefundRequestDOMapper requestRows;
    private final RefundOrderDOMapper childRows;
    private final RefundAllocationPlanDOMapper planRows;
    private final JsonMapper json;
    private final TestScenario scenario;
    private final RemotePorts remote;

    @Autowired
    FinancialPersistenceIT(PaymentAcceptanceTransactions transactions, PaymentSubmissionPreparation preparation,
            PaymentAcceptanceGateway payments, PaymentAttemptGateway attempts, PaymentExecutionLocks locks,
            ChannelBindingManagementGateway bindings, PaymentAcceptanceCodec codec, ChannelBindingCodec bindingCodec,
            RefundGateway refunds, PaymentRefundCapacityDOMapper capacities, PaymentResultStorageConverter capacityConverter,
            PaymentAttemptDOMapper attemptRows, RefundRequestDOMapper requestRows, RefundOrderDOMapper childRows,
            RefundAllocationPlanDOMapper planRows, JsonMapper json, TestScenario scenario, RemotePorts remote) {
        this.transactions = transactions; this.preparation = preparation; this.payments = payments; this.attempts = attempts;
        this.locks = locks; this.bindings = bindings; this.codec = codec; this.bindingCodec = bindingCodec; this.refunds = refunds;
        this.capacities = capacities; this.capacityConverter = capacityConverter; this.attemptRows = attemptRows;
        this.requestRows = requestRows; this.childRows = childRows; this.planRows = planRows; this.json = json;
        this.scenario = scenario; this.remote = remote;
    }

    @BeforeEach
    void resetRemoteObservations() {
        remote.barrier.set(null);
        clearInvocations(remote.risk, remote.limits, remote.channels, remote.cipher, remote.events);
    }

    @Test
    void twoRealY2ContendersProduceOneSubmissionTicketAndOnePersistedEpoch() throws Exception {
        var fixture = seed();
        remote.barrier.set(new CyclicBarrier(2));
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = workers.submit(() -> prepare(fixture));
            var second = workers.submit(() -> prepare(fixture));
            assertEquals(1L, Stream.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))
                    .filter(Optional::isPresent).count());
        } finally { remote.barrier.set(null); }
        transactions.readSnapshot(fixture.tenant(), () -> {
            var persisted = attempts.find(fixture.tenant(), fixture.attempt().attempt().id()).orElseThrow();
            assertEquals(PaymentAttemptState.SUBMITTING, persisted.attempt().state());
            assertEquals(1L, persisted.attempt().executionEpoch());
            assertEquals(1L, persisted.rowVersion());
            assertEquals(fixture.attempt().attempt().channelRequestNo(), persisted.attempt().channelRequestNo());
            assertEquals(codec.checksum(RemotePorts.PERMIT), persisted.executionPermitHash());
            assertEquals(1L, attemptRows.selectCountByQuery(QueryWrapper.create().where(PAYMENT_ATTEMPT_DO.TENANT_ID.eq(fixture.tenant()))
                    .and(PAYMENT_ATTEMPT_DO.PAYMENT_ID.eq(fixture.command().paymentId()))));
            return true;
        });
        verifyNoInteractions(remote.cipher, remote.events);
        verify(remote.channels, never()).submit(any());
    }

    @Test
    void aCommittedY2WithLostTicketCannotReceiveANewTicketAfterReload() {
        var fixture = seed();
        assertTrue(prepare(fixture).isPresent());
        clearInvocations(remote.risk, remote.limits, remote.channels, remote.cipher, remote.events);
        assertTrue(prepare(fixture).isEmpty());
        verifyNoInteractions(remote.risk, remote.limits, remote.channels, remote.cipher, remote.events);
        transactions.readSnapshot(fixture.tenant(), () -> {
            var persisted = attempts.find(fixture.tenant(), fixture.attempt().attempt().id()).orElseThrow();
            assertEquals(fixture.attempt().attempt().channelRequestNo(), persisted.attempt().channelRequestNo());
            assertEquals(1L, persisted.rowVersion());
            assertEquals(PaymentAttemptState.SUBMITTING, persisted.attempt().state());
            return true;
        });
    }

    @Test
    void anAttemptTransitionThatRollsBackDoesNotLeaveAPartialY2Claim() {
        var fixture = seed();
        assertThrows(IllegalStateException.class, () -> transactions.writeLocal(fixture.tenant(), () -> {
            locks.lock(fixture.tenant(), fixture.command().paymentId(), "acquire");
            var before = attempts.lock(fixture.tenant(), fixture.attempt().attempt().id()).orElseThrow();
            var after = before.beginSubmission(1L, codec.checksum(RemotePorts.PERMIT), RiskDecision.notConnected(), AT);
            attempts.update(fixture.tenant(), after, before.rowVersion());
            throw new IllegalStateException("Injected after actual attempt update");
        }));
        transactions.readSnapshot(fixture.tenant(), () -> {
            var persisted = attempts.find(fixture.tenant(), fixture.attempt().attempt().id()).orElseThrow();
            assertEquals(PaymentAttemptState.INIT, persisted.attempt().state());
            assertEquals(0L, persisted.rowVersion());
            assertEquals(0L, persisted.attempt().executionEpoch());
            assertNull(persisted.executionPermitHash());
            return true;
        });
        assertTrue(prepare(fixture).isPresent());
    }

    @Test
    void competingRefundPlansCannotReserveMoreThanTheRealSucceededCapacity() throws Exception {
        var fixture = seedCapacity();
        String firstId = id("refund-request"), secondId = id("refund-request");
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            java.util.function.Function<String, Callable<Boolean>> contender = request -> () -> {
                ready.countDown(); await(start);
                try {
                    transactions.writeLocal(fixture.tenant(), () -> { insertPlan(fixture, request, 7000L); return true; });
                    return true;
                } catch (CommonException exhausted) {
                    assertEquals(INVALID_ARGUMENT, exhausted.getResultCode());
                    return false;
                }
            };
            var first = workers.submit(contender.apply(firstId));
            var second = workers.submit(contender.apply(secondId));
            await(ready); start.countDown();
            assertEquals(1L, Stream.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))
                    .filter(Boolean::booleanValue).count());
        } finally { start.countDown(); }
        transactions.readSnapshot(fixture.tenant(), () -> {
            var after = capacity(fixture);
            assertEquals(7000L, after.reservedMinor());
            assertEquals(3000L, after.availableMinor());
            assertEquals(1L, after.version());
            assertPlanCounts(fixture, 1L);
            boolean firstExists = refunds.reference(fixture.tenant(), firstId).isPresent();
            boolean secondExists = refunds.reference(fixture.tenant(), secondId).isPresent();
            assertTrue(firstExists ^ secondExists);
            var persisted = refunds.load(fixture.tenant(), firstExists ? firstId : secondId);
            assertEquals(7000L, persisted.request().amount().getCent());
            assertEquals(RefundRequestState.INIT, persisted.request().state());
            return true;
        });
        scenario.assertDb("payment_refund_capacity").where("tenant_id", fixture.tenant())
                .where("payment_id", fixture.command().paymentId()).exists();
    }

    @Test
    void refundHeaderPlanChildrenAndCapacityAllRollBackOnAFailureAfterInsertPlan() {
        var fixture = seedCapacity();
        String request = id("refund-request");
        assertThrows(IllegalStateException.class, () -> transactions.writeLocal(fixture.tenant(), () -> {
            insertPlan(fixture, request, 7000L);
            throw new IllegalStateException("Injected after request, child, plan and capacity writes");
        }));
        transactions.readSnapshot(fixture.tenant(), () -> {
            assertTrue(refunds.reference(fixture.tenant(), request).isEmpty());
            assertPlanCounts(fixture, 0L);
            assertEquals(0L, capacity(fixture).reservedMinor());
            assertEquals(0L, capacity(fixture).version());
            return true;
        });
    }

    private FinancialPersistenceFixtures.Fixture seed() {
        var fixture = FinancialPersistenceFixtures.prepared(codec, bindingCodec);
        transactions.writeLocal(fixture.tenant(), () -> {
            bindings.insertHead(fixture.tenant(), fixture.head());
            bindings.insertRevision(fixture.tenant(), fixture.revision());
            payments.insertParent(fixture.tenant(), new StoredPaymentGate(id("parent"), fixture.command().payeeMerchantId(), "acquire",
                    PaymentExecutionGate.init(fixture.command().orderId(), null).open(), 0L));
            payments.insertPaymentGate(fixture.tenant(), new StoredPaymentGate(id("gate"), fixture.command().payeeMerchantId(), "acquire",
                    PaymentExecutionGate.init(fixture.command().orderId(), fixture.command().paymentId()).open(), 0L));
            payments.insert(fixture.tenant(), fixture.payment());
            attempts.insert(fixture.tenant(), fixture.attempt());
            return true;
        });
        return fixture;
    }

    private FinancialPersistenceFixtures.Fixture seedCapacity() {
        var fixture = seed();
        var capacity = new PaymentRefundCapacity(fixture.command().paymentId(), fixture.command().orderId(), fixture.command().payeeMerchantId(),
                AT.minusHours(1), Money.ofMinor(10000L, "CNY"), 0L, 0L, 0L);
        transactions.writeLocal(fixture.tenant(), () -> {
            assertEquals(1, capacities.insert(capacityConverter.toCapacityDO(fixture.tenant(), id("capacity"), capacity)));
            return true;
        });
        return fixture;
    }

    private Optional<io.github.loadup.pay.payment.app.model.PaymentSubmissionTicket> prepare(FinancialPersistenceFixtures.Fixture fixture) {
        return preparation.prepare(fixture.tenant(), fixture.command().paymentId(), fixture.attempt().attempt().id());
    }

    private void insertPlan(FinancialPersistenceFixtures.Fixture fixture, String requestId, long minor) {
        payments.lockParent(fixture.tenant(), fixture.command().orderId()).orElseThrow();
        var candidates = refunds.lockCapacities(fixture.tenant(), fixture.command().orderId());
        var items = new FifoRefundPolicy().plan(fixture.command().orderId(), fixture.command().payeeMerchantId(), Money.ofMinor(minor, "CNY"), true, candidates);
        assertEquals(1, items.size());
        var item = items.getFirst();
        var before = candidates.getFirst();
        var child = RefundOrder.init(id("refund"), requestId, item.paymentId(), item.amount(), UUID.randomUUID().toString().replace("-", ""));
        var storedChild = new StoredRefundChild(child, item.sequenceNo(), fixture.attempt().attempt().identity(), "synthetic-original-transaction", before.succeededAt());
        var request = RefundRequest.init(requestId, fixture.command().orderId(), fixture.command().payeeMerchantId(), requestId,
                Money.ofMinor(minor, "CNY"), List.of(child));
        String payload = json.writeValueAsString(fixture.command().admittedTerms());
        var stored = new StoredRefundRequest(request, "CUSTOMER_REQUEST", codec.checksum(requestId), "d".repeat(64), fixture.command().admissionId(),
                payload, codec.checksum(payload), AT, List.of(storedChild));
        refunds.insertPlan(fixture.tenant(), stored, List.of(new RefundCapacityReservation(before, before.reserve(item.amount().getCent()))));
    }

    private PaymentRefundCapacity capacity(FinancialPersistenceFixtures.Fixture fixture) {
        return capacityConverter.toCapacity(capacities.selectOneByQuery(QueryWrapper.create()
                .where(PAYMENT_REFUND_CAPACITY_DO.TENANT_ID.eq(fixture.tenant()))
                .and(PAYMENT_REFUND_CAPACITY_DO.PAYMENT_ID.eq(fixture.command().paymentId()))));
    }
    private void assertPlanCounts(FinancialPersistenceFixtures.Fixture fixture, long expected) {
        assertEquals(expected, requestRows.selectCountByQuery(QueryWrapper.create().where(REFUND_REQUEST_DO.TENANT_ID.eq(fixture.tenant()))
                .and(REFUND_REQUEST_DO.ORDER_ID.eq(fixture.command().orderId()))));
        assertEquals(expected, childRows.selectCountByQuery(QueryWrapper.create().where(REFUND_ORDER_DO.TENANT_ID.eq(fixture.tenant()))
                .and(REFUND_ORDER_DO.PAYMENT_ID.eq(fixture.command().paymentId()))));
        assertEquals(expected, planRows.selectCountByQuery(QueryWrapper.create().where(REFUND_ALLOCATION_PLAN_DO.TENANT_ID.eq(fixture.tenant()))
                .and(REFUND_ALLOCATION_PLAN_DO.PAYMENT_ID.eq(fixture.command().paymentId()))));
    }
    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(10, TimeUnit.SECONDS)); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }

    static final class RemotePorts {
        static final String PERMIT = "e".repeat(64);
        final AtomicReference<CyclicBarrier> barrier = new AtomicReference<>();
        final Map<String, String> operationPayments = new ConcurrentHashMap<>();
        final PaymentLimitClient limits = mock(PaymentLimitClient.class);
        final RiskConsultGateway risk = mock(RiskConsultGateway.class);
        final PaymentChannelGateway channels = mock(PaymentChannelGateway.class);
        final PaymentInputCipher cipher = mock(PaymentInputCipher.class);
        final PaymentResultEvents events = mock(PaymentResultEvents.class);
        RemotePorts() {
            when(limits.supportsTenant(anyString())).thenReturn(true);
            when(limits.reserve(anyString(), any())).thenAnswer(c -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                LimitReserveCommand command = c.getArgument(1);
                operationPayments.put(command.operationId(), command.paymentId());
                return new LimitReservationDTO("res-" + command.paymentId(), command.operationId(), command.paymentId(), LimitReservationState.RESERVED,
                        command.policyId(), command.policyVersion(), command.initiatedAt(), 1L);
            });
            when(limits.begin(anyString(), any())).thenAnswer(c -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                LimitBeginExecutionCommand command = c.getArgument(1);
                return new LimitExecutionPermitDTO(command.operationId(), operationPayments.get(command.operationId()), command.attemptId(), 1L, PERMIT);
            });
            when(limits.verify(anyString(), any())).thenAnswer(c -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                var gate = barrier.get();
                if (gate != null) gate.await(10, TimeUnit.SECONDS);
                return new LimitExecutionVerificationDTO(true, "VALID");
            });
            when(risk.consult(any())).thenAnswer(c -> {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                return RiskDecision.notConnected();
            });
            doAnswer(c -> { assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return null; }).when(channels).validate(any());
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan(basePackageClasses = PaymentAttemptDOMapper.class)
    @ComponentScan(basePackageClasses = {PaymentRecoveryStorageConverter.class, PaymentSubmissionConverter.class})
    @Import({PaymentAcceptanceGatewayImpl.class, PaymentControlGatewayImpl.class, PaymentAttemptGatewayImpl.class, PaymentInputGatewayImpl.class,
            ChannelBindingGatewayImpl.class, RefundGatewayImpl.class, PaymentAcceptanceCodecImpl.class, ChannelBindingCodecImpl.class,
            PaymentExecutionLocks.class, PaymentInputAccess.class, PaymentSubmissionPreparation.class, PaymentAcceptanceTransactions.class})
    static class Application {
        @Bean("paymentClock") Clock paymentClock() { return Clock.fixed(AT.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()); }
        @Bean RemotePorts remotePorts() { return new RemotePorts(); }
        @Bean PaymentLimitClient limits(RemotePorts ports) { return ports.limits; }
        @Bean RiskConsultGateway risk(RemotePorts ports) { return ports.risk; }
        @Bean PaymentChannelGateway channels(RemotePorts ports) { return ports.channels; }
        @Bean PaymentInputCipher cipher(RemotePorts ports) { return ports.cipher; }
        @Bean PaymentResultEvents events(RemotePorts ports) { return ports.events; }
    }
}
