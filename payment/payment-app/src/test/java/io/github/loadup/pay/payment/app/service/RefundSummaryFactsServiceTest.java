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
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.payment.app.converter.RefundProjectionConverter;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.RefundSummaryGateway;
import io.github.loadup.pay.payment.domain.model.OrderRefundSummary;
import io.github.loadup.pay.payment.domain.model.StoredPaymentGate;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.NOT_FOUND;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RefundSummaryFactsServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(RefundProjectionConverter.class.getPackageName());
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final PaymentAcceptanceGateway payments = mock(PaymentAcceptanceGateway.class);
    final RefundSummaryGateway summaries = mock(RefundSummaryGateway.class);
    final AtomicReference<PaymentActor> actor = new AtomicReference<>(new PaymentActor("tenant", "acquire", Set.of("pay:payment:refund-summary:read")));
    final AtomicReference<StoredPaymentGate> parent = new AtomicReference<>(AttemptFixtures.gate(true));
    final OrderRefundSummary summary = new OrderRefundSummary("order", "merchant", DomainChecks.cnyAmount(10000, "CNY"), 1000, 500, 3);
    final RefundSummaryFactsService service = new RefundSummaryFactsService(actor::get, payments, summaries,
            context.getBean(RefundProjectionConverter.class), new PaymentAcceptanceTransactions(new PaymentAcceptanceServiceTest.UnitTransactions()),
            validation.getValidator(), "acquire");

    @BeforeEach
    void setup() {
        when(payments.lockParent("tenant", "order")).thenAnswer(c -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return Optional.of(parent.get());
        });
        when(summaries.lock("tenant", "order")).thenReturn(Optional.of(summary));
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    @Test
    void originalAcquireReadsBoundedAuthoritativeMoneyUnderTheParentLock() {
        var result = service.refundSummary(new IdQuery("order"));
        assertEquals(1000, result.refundedMinor());
        assertEquals(500, result.refundReservedMinor());
        assertEquals(3, result.refundSummaryVersion());
        var order = inOrder(payments, summaries);
        order.verify(payments).lockParent("tenant", "order");
        order.verify(summaries).lock("tenant", "order");
    }

    @Test
    void missingSummaryIsNotAZeroRefundProof() {
        when(summaries.lock("tenant", "order")).thenReturn(Optional.empty());
        assertSame(NOT_FOUND, assertThrows(CommonException.class, () -> service.refundSummary(new IdQuery("order"))).getResultCode());
    }

    @Test
    void originalServiceOwnershipCannotBeReplacedByAValidScope() {
        var old = parent.get();
        parent.set(new StoredPaymentGate(old.id(), old.payeeMerchantId(), "other-acquire", old.gate(), old.rowVersion()));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.refundSummary(new IdQuery("order"))).getResultCode());
        verifyNoInteractions(summaries);
    }

    @Test
    void configuredAcquireAndDedicatedPermissionAreBothRequired() {
        actor.set(new PaymentActor("tenant", "other", Set.of("pay:payment:refund-summary:read")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.refundSummary(new IdQuery("order"))).getResultCode());
        actor.set(new PaymentActor("tenant", "acquire", Set.of("pay:payment:acquire-facts")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.refundSummary(new IdQuery("order"))).getResultCode());
        verifyNoInteractions(payments, summaries);
    }

    @Test
    void summaryMustMatchTheOriginalCollectingMerchant() {
        when(summaries.lock("tenant", "order")).thenReturn(Optional.of(
                new OrderRefundSummary("order", "another-merchant", DomainChecks.cnyAmount(10000, "CNY"), 1000, 500, 3)));
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> service.refundSummary(new IdQuery("order"))).getResultCode());
    }
}
