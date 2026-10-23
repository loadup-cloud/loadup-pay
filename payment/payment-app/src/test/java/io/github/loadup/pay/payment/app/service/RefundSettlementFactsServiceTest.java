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

import io.github.loadup.pay.channel.api.model.ChannelRefundOutcome;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.pay.payment.app.converter.RefundExecutionConverter;
import io.github.loadup.pay.payment.app.converter.RefundSettlementConverter;
import io.github.loadup.pay.payment.app.model.LockedRefund;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.RefundResultGateway;
import io.github.loadup.pay.payment.domain.model.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefundSettlementFactsServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(RefundSettlementConverter.class.getPackageName());
    final jakarta.validation.ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final RefundExecutionLocks locks = mock(RefundExecutionLocks.class);
    final RefundResultGateway results = mock(RefundResultGateway.class);
    final PaymentAcceptanceGateway payments = mock(PaymentAcceptanceGateway.class);
    final AtomicReference<PaymentActor> actor = new AtomicReference<>(new PaymentActor("tenant", "limitcenter", Set.of("pay:payment:refund-facts")));
    final RefundExecutionConverter edits = context.getBean(RefundExecutionConverter.class);
    final StoredRefundRequest initial = RefundResultFixtures.initial();
    final StoredRefundChild first = initial.allocations().getFirst();
    final RefundOrder successful = first.refund().beginSubmission().recordOutcome(new ChannelRefundOutcome(
            "refund", ChannelRefundOutcome.Kind.SUCCEEDED, first.refund().amount(), "refund-proof"));
    final StoredRefundChild child = edits.withRefund(first, successful);
    final StoredRefundRequest request = edits.withRequest(initial, initial.request().apply(successful),
            List.of(child, initial.allocations().get(1)));
    final PaymentRefundCapacity capacity = new PaymentRefundCapacity("payment", "order", "merchant", first.originalSucceededAt(),
            RefundResultFixtures.capacity().succeededAmount(), 2000, 500, 5);
    final RefundTransactionClaim claim = new RefundTransactionClaim("claim", "tenant", "refund", first.original(),
            successful.channelRefundNo(), first.channelTransactionNo(), successful.amount(), "channel-refund", RefundResultFixtures.NOW, "refund-proof");
    final AtomicReference<LockedRefund> locked = new AtomicReference<>(new LockedRefund(request,
            new RefundExecutionFacts(child, RefundResultFixtures.NOW.minusSeconds(10), "channel-refund", RefundResultFixtures.NOW), capacity));
    final RefundSettlementFactsService service = new RefundSettlementFactsService(actor::get,
            new PaymentAcceptanceTransactions(new PaymentAcceptanceServiceTest.UnitTransactions()), locks, results, payments,
            context.getBean(RefundSettlementConverter.class), validation.getValidator(), "limitcenter");

    @BeforeEach
    void setup() {
        when(results.requestId("tenant", "refund")).thenReturn(Optional.of("request"));
        when(locks.lock("tenant", "request", "refund")).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return locked.get();
        });
        when(results.claim("tenant", "refund")).thenReturn(Optional.of(claim));
        when(results.transaction(claim.original().channel(), claim.original().accountScopeHash(), claim.channelRefundTransactionNo()))
                .thenReturn(Optional.of(claim));
        var payment = new PaymentOrder("payment", "order", "allocation", "merchant", capacity.succeededAmount(), "operation",
                first.originalSucceededAt().minusMinutes(1), PaymentState.SUCCEEDED, first.original().attemptId(), first.original().attemptId(), 3);
        when(payments.find("tenant", "payment")).thenReturn(Optional.of(new StoredPaymentAcceptance(payment, "admission",
                "a".repeat(64), "acquire", "{}", "b".repeat(64), "payment-proof")));
    }

    @AfterEach
    void close() { context.close(); validation.close(); }

    @Test
    void successfulChildAndLatestPaymentCumulativeAreReadUnderExistingFinancialLocks() {
        var facts = service.refundFacts(new IdQuery("refund"));
        assertEquals("refund-proof", facts.refund().evidenceId());
        assertEquals("operation", facts.refund().operationId());
        assertEquals(1000, facts.refund().amountMinor());
        assertEquals(2000, facts.refund().cumulativePaymentRefundedMinor());
        assertEquals(5, facts.paymentRefundVersion());
        assertEquals(3, facts.paymentVersion());
        assertEquals("payment-proof", facts.paymentEvidenceId());
    }

    @Test
    void configuredLimitServiceAndItsDedicatedScopeAreRequired() {
        actor.set(new PaymentActor("tenant", "acquire", Set.of("pay:payment:refund-facts")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.refundFacts(new IdQuery("refund"))).getResultCode());
        actor.set(new PaymentActor("tenant", "limitcenter", Set.of("pay:payment:limit-facts")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.refundFacts(new IdQuery("refund"))).getResultCode());
        verifyNoInteractions(results, locks, payments);
    }

    @Test
    void missingGlobalClaimCannotBePresentedAsAnAuthoritativeRefund() {
        when(results.transaction(claim.original().channel(), claim.original().accountScopeHash(), claim.channelRefundTransactionNo()))
                .thenReturn(Optional.empty());
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> service.refundFacts(new IdQuery("refund"))).getResultCode());
        verifyNoInteractions(payments);
    }

    @Test
    void unsubmittedRefundIsNotAQuotaRestorationFact() {
        locked.set(new LockedRefund(initial, new RefundExecutionFacts(first, null, null, null), RefundResultFixtures.capacity()));
        assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> service.refundFacts(new IdQuery("refund"))).getResultCode());
        verify(results, never()).claim(anyString(), anyString());
    }

    @Test
    void missingRefundIsNotAZeroRefundProof() {
        when(results.requestId("tenant", "refund")).thenReturn(Optional.empty());
        assertSame(NOT_FOUND, assertThrows(CommonException.class, () -> service.refundFacts(new IdQuery("refund"))).getResultCode());
        verifyNoInteractions(locks);
    }
}
