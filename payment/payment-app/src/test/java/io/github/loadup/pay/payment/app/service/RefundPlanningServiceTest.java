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
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.channel.api.model.ChannelPaymentOutcome;
import io.github.loadup.pay.payment.app.converter.RefundPlanningConverter;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.app.security.RefundActor;
import io.github.loadup.pay.payment.client.command.PaymentExecuteCommand;
import io.github.loadup.pay.payment.client.command.RefundCreateCommand;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentAttemptGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentResultGateway;
import io.github.loadup.pay.payment.domain.gateway.RefundGateway;
import io.github.loadup.pay.payment.domain.model.ChannelTransactionClaim;
import io.github.loadup.pay.payment.domain.model.PaymentAttempt;
import io.github.loadup.pay.payment.domain.model.PaymentOrder;
import io.github.loadup.pay.payment.domain.model.PaymentRefundCapacity;
import io.github.loadup.pay.payment.domain.model.RefundCapacityReservation;
import io.github.loadup.pay.payment.domain.model.RefundRequestReference;
import io.github.loadup.pay.payment.domain.model.RiskDecision;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAcceptance;
import io.github.loadup.pay.payment.domain.model.StoredPaymentAttempt;
import io.github.loadup.pay.payment.domain.model.StoredPaymentGate;
import io.github.loadup.pay.payment.domain.model.StoredRefundRequest;
import io.github.loadup.pay.product.client.dto.AdmittedOrderTermsDTO;
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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.CONCURRENT_UPDATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.REFUND_AMOUNT_EXCEEDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
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

class RefundPlanningServiceTest {
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(RefundPlanningConverter.class.getPackageName());
    final RefundGateway refunds = mock(RefundGateway.class);
    final RefundProjectionEvents projections = mock(RefundProjectionEvents.class);
    final RefundRecoveryService recovery = mock(RefundRecoveryService.class);
    final PaymentAcceptanceGateway payments = mock(PaymentAcceptanceGateway.class);
    final PaymentResultGateway results = mock(PaymentResultGateway.class);
    final PaymentAttemptGateway attempts = mock(PaymentAttemptGateway.class);
    final PaymentAcceptanceCodec codec = mock(PaymentAcceptanceCodec.class);
    final PaymentAcceptanceServiceTest.UnitTransactions manager = new PaymentAcceptanceServiceTest.UnitTransactions();
    final AtomicInteger ids = new AtomicInteger();
    final AtomicReference<RefundActor> actor = new AtomicReference<>(new RefundActor("tenant", "merchant", "a".repeat(64),
            Set.of("pay:payment:refund:create", "pay:payment:refund:read")));
    final Map<String, PaymentRefundCapacity> capacities = new TreeMap<>();
    final Map<String, StoredRefundRequest> saved = new HashMap<>();
    final Map<String, PaymentExecuteCommand> commands = new HashMap<>();
    AdmittedOrderTermsDTO admitted = PaymentFixtures.command().admittedTerms();

    RefundPlanningService service(LocalDateTime now) {
        return new RefundPlanningService(actor::get, refunds, payments, results, attempts, codec, context.getBean(RefundPlanningConverter.class),
                new PaymentAcceptanceTransactions(manager), () -> "refund-" + ids.incrementAndGet(), validation.getValidator(),
                Clock.fixed(now.atZone(BusinessTime.zone()).toInstant(), BusinessTime.zone()), projections, recovery);
    }

    RefundCreateCommand command(long amount) {
        return new RefundCreateCommand("order", "merchant", "R001", amount, "CUSTOMER_REQUEST", List.of());
    }

    @BeforeEach
    void setup() {
        when(codec.checksum(anyString())).thenAnswer(c -> HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(((String) c.getArgument(0)).getBytes(StandardCharsets.UTF_8))));
        when(codec.decode(any())).thenAnswer(c -> commands.get(((StoredPaymentAcceptance) c.getArgument(0)).payment().id()));
        when(payments.lockParent("tenant", "order")).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("tenant", TenantUtil.getTenantId());
            return Optional.of(AttemptFixtures.gate(true));
        });
        when(refunds.merchantReference(eq("tenant"), eq("merchant"), anyString())).thenAnswer(c -> saved.values().stream()
                .filter(s -> s.request().merchantRefundNo().equals(c.getArgument(2)))
                .map(s -> new RefundRequestReference(s.request().id(), s.request().orderId(), s.request().payeeMerchantId(), s.requestDigest())).findFirst());
        when(refunds.reference(eq("tenant"), anyString())).thenAnswer(c -> Optional.ofNullable(saved.get(c.getArgument(1)))
                .map(s -> new RefundRequestReference(s.request().id(), s.request().orderId(), s.request().payeeMerchantId(), s.requestDigest())));
        when(refunds.load(eq("tenant"), anyString())).thenAnswer(c -> saved.get(c.getArgument(1)));
        when(refunds.lockCapacities("tenant", "order")).thenAnswer(c -> List.copyOf(capacities.values()));
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            StoredRefundRequest value = c.getArgument(1);
            saved.put(value.request().id(), value);
            List<RefundCapacityReservation> changes = c.getArgument(2);
            changes.forEach(r -> capacities.put(r.reserved().paymentId(), r.reserved()));
            return null;
        }).when(refunds).insertPlan(eq("tenant"), any(), anyList());
        doAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals("tenant", TenantUtil.getTenantId());
            return null;
        }).when(recovery).register(anyString(), any());
        addPayment("z-old", 3000, AttemptFixtures.NOW.minusHours(2));
        addPayment("a-new", 7000, AttemptFixtures.NOW.minusHours(1));
    }

    void addPayment(String id, long amount, LocalDateTime at) {
        var attempt = PaymentAttempt.init("attempt-" + id, id, 1, DomainChecks.cnyAmount(amount, "CNY"), AttemptFixtures.binding(), PaymentMethod.H5, "request-" + id);
        var stored = new StoredPaymentAttempt(attempt, "operation-" + id, "acquire", "initial", "b".repeat(64), "a".repeat(64),
                null, null, null, at.minusMinutes(1), null, 0);
        var submitted = stored.beginSubmission(1, "e".repeat(64), RiskDecision.notConnected(), at);
        var success = submitted.applyOutcome(new ChannelPaymentOutcome(ChannelPaymentOutcome.Kind.SUCCEEDED, attempt.identity(),
                attempt.amount(), "proof-" + id), false);
        var payment = PaymentOrder.init(id, "order", "allocation-" + id, "merchant", attempt.amount(), "operation-" + id, at.minusMinutes(1))
                .prepareAttempt(attempt, null).applyAttempt(success.attempt());
        var accepted = new StoredPaymentAcceptance(payment, "admission", "a".repeat(64), "acquire", "payload", "c".repeat(64), "proof-" + id);
        commands.put(id, new PaymentExecuteCommand(id, "order", "allocation-" + id, "admission", "merchant", amount, "CNY", "a".repeat(64),
                PaymentFixtures.command().choice(), null, admitted, PaymentFixtures.SUBJECT, PaymentFixtures.PAYABLE_UNTIL));
        when(payments.find("tenant", id)).thenReturn(Optional.of(accepted));
        when(attempts.find("tenant", attempt.id())).thenReturn(Optional.of(success));
        when(results.attemptClaim("tenant", attempt.id())).thenReturn(Optional.of(new ChannelTransactionClaim("claim-" + id, "tenant",
                attempt.identity(), attempt.amount(), "transaction-" + id, at, "proof-" + id)));
        capacities.put(id, new PaymentRefundCapacity(id, "order", "merchant", at, attempt.amount(), 0, 0, 0));
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    @Test
    void freezesFifoPlanOriginalIdentitiesAndAllReservationsInOneLocalTransaction() {
        var dto = service(AttemptFixtures.NOW).create(command(6000));
        assertEquals(io.github.loadup.pay.payment.domain.model.RefundRequestState.INIT, dto.state());
        assertEquals(2, dto.allocations().size());
        assertEquals("z-old", dto.allocations().get(0).paymentId());
        assertEquals(3000, dto.allocations().get(0).amountMinor());
        assertEquals(3000, dto.allocations().get(1).amountMinor());
        assertEquals(3000, capacities.get("z-old").reservedMinor());
        assertEquals(3000, capacities.get("a-new").reservedMinor());
        assertEquals(1, manager.commits);
        var stored = saved.get(dto.refundRequestId());
        verify(recovery, times(1)).register("tenant", stored);
        assertEquals("transaction-z-old", stored.allocations().get(0).channelTransactionNo());
        assertEquals("binding-revision", stored.allocations().get(0).original().bindingRevisionId());
        assertEquals(32, stored.allocations().get(0).refund().channelRefundNo().length());
        assertEquals(AttemptFixtures.NOW, stored.plannedAt());
        verify(payments, never()).insert(anyString(), any()); // Refunds never change original collection.
    }

    @Test
    void replayAfterRefundWindowExpiresKeepsOriginalPlanAndDoesNotReserveAgain() {
        var first = service(AttemptFixtures.NOW).create(command(6000));
        clearInvocations(refunds, codec, results, attempts);
        clearInvocations(recovery);
        var replay = service(AttemptFixtures.NOW.plusDays(5)).create(command(6000));
        assertEquals(first, replay);
        verify(refunds, never()).lockCapacities(anyString(), anyString());
        verify(refunds, never()).insertPlan(anyString(), any(), anyList());
        verify(codec, never()).decode(any());
        verifyNoInteractions(results, attempts);
        verify(projections, times(1)).planned(anyString(), any(), any());
        verify(recovery, never()).register(anyString(), any());
    }

    @Test
    void changedAmountReasonOrOrderCannotReuseTheMerchantRefundNumber() {
        service(AttemptFixtures.NOW).create(command(6000));
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service(AttemptFixtures.NOW).create(command(5000))).getResultCode());
        assertThrows(CommonException.class, () -> service(AttemptFixtures.NOW).create(new RefundCreateCommand("order", "merchant", "R001", 6000, "OTHER", List.of())));
        assertThrows(CommonException.class, () -> service(AttemptFixtures.NOW).create(new RefundCreateCommand("other-order", "merchant", "R001", 6000, "CUSTOMER_REQUEST", List.of())));
        verify(refunds, times(1)).insertPlan(anyString(), any(), anyList());
    }

    @Test
    void outstandingReservationsRemainUnavailableForAnotherRequest() {
        service(AttemptFixtures.NOW).create(command(6000));
        assertSame(REFUND_AMOUNT_EXCEEDED, assertThrows(CommonException.class, () -> service(AttemptFixtures.NOW).create(
                new RefundCreateCommand("order", "merchant", "R002", 5000, "CUSTOMER_REQUEST", List.of()))).getResultCode());
        verify(refunds, times(1)).insertPlan(anyString(), any(), anyList());
    }

    @Test
    void capacityExpiryUsesEachOriginalSuccessTimeAndExcludesTheExactBoundary() {
        assertSame(REFUND_AMOUNT_EXCEEDED, assertThrows(CommonException.class,
                () -> service(AttemptFixtures.NOW.plusHours(23)).create(command(1))).getResultCode());
        verify(refunds, never()).insertPlan(anyString(), any(), anyList());
    }

    @Test
    void aFencedCollectionGateStillPermitsIndependentRefunds() {
        var original = AttemptFixtures.gate(true);
        when(payments.lockParent("tenant", "order")).thenReturn(Optional.of(new StoredPaymentGate(original.id(), original.payeeMerchantId(),
                original.callerServiceId(), original.gate().fence(1), original.rowVersion() + 1)));
        assertEquals(6000, service(AttemptFixtures.NOW).create(command(6000)).amountMinor());
    }

    @Test
    void failedPlanPersistenceAbortsTheTransactionBoundary() {
        doThrow(new CommonException(CONCURRENT_UPDATE)).when(refunds).insertPlan(anyString(), any(), anyList());
        assertThrows(CommonException.class, () -> service(AttemptFixtures.NOW).create(command(6000)));
        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
        // Real capacity/request rollback remains a database integration acceptance.
    }

    @Test
    void recoveryRegistrationFailurePropagatesAndRollsBackTheOwningTransactionBoundary() {
        doThrow(new CommonException(CONCURRENT_UPDATE)).when(recovery).register(eq("tenant"), any());

        assertThrows(CommonException.class, () -> service(AttemptFixtures.NOW).create(command(6000)));

        assertEquals(0, manager.commits);
        assertEquals(1, manager.rollbacks);
        verify(recovery).register(eq("tenant"), any());
        // The in-memory transaction fixture checks propagation only; real database rollback remains integration acceptance.
    }

    @Test
    void authenticatedMerchantMustMatchTheRequestedPayeeAndStoredRefundOwner() {
        var first = service(AttemptFixtures.NOW).create(command(6000));
        actor.set(new RefundActor("tenant", "other", "a".repeat(64), Set.of("pay:payment:refund:create", "pay:payment:refund:read")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service(AttemptFixtures.NOW).create(command(6000))).getResultCode());
        assertSame(NOT_FOUND, assertThrows(CommonException.class, () -> service(AttemptFixtures.NOW).detail(new IdQuery(first.refundRequestId()))).getResultCode());
    }

    @Test
    void detailReadsTheSameFixedPlanWithNoNewReservations() {
        var first = service(AttemptFixtures.NOW).create(command(6000));
        assertEquals(first, service(AttemptFixtures.NOW).detail(new IdQuery(first.refundRequestId())));
        verify(refunds, times(1)).insertPlan(anyString(), any(), anyList());
    }

    @Test
    void outerTransactionAndExplicitAllocationCannotBypassThePlanningBoundary() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> service(AttemptFixtures.NOW).create(command(1))).getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertThrows(CommonException.class, () -> service(AttemptFixtures.NOW).create(new RefundCreateCommand("order", "merchant", "R001", 1, "CUSTOMER_REQUEST",
                List.of(new io.github.loadup.pay.payment.client.dto.RefundExplicitAllocationDTO("a-new", 1)))));
        verifyNoInteractions(refunds);
    }
}
