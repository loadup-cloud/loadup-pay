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
package io.github.loadup.pay.payment.infrastructure.channel;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.pay.channel.api.facade.ChannelPaymentFacade;
import io.github.loadup.pay.channel.api.model.*;
import io.github.loadup.pay.payment.app.service.PaymentChannelRequestFactory;
import io.github.loadup.pay.payment.domain.gateway.ChannelBindingGateway;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.Set;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentChannelGatewayImplTest {
    private static final Money AMOUNT = Money.ofMinor(2500, "CNY");
    private static final String HASH = "a".repeat(64);

    private final ChannelPaymentFacade channels = mock(ChannelPaymentFacade.class);
    private final ChannelBindingGateway bindings = mock(ChannelBindingGateway.class);
    private final PaymentChannelRequestFactory requests = mock(PaymentChannelRequestFactory.class);
    private final PaymentChannelGatewayImpl gateway = new PaymentChannelGatewayImpl(channels, bindings, requests);
    private final ChannelPaymentIdentity identity = new ChannelPaymentIdentity(
            "payment-1", "attempt-1", Channel.ALIPAY, "frozen-revision", HASH, "request-1");
    private final ChannelPaymentTarget target = new ChannelPaymentTarget(identity, AMOUNT);
    private final ChannelBindingRevision frozenRevision = new ChannelBindingRevision("frozen-revision", "merchant-1",
            Channel.ALIPAY, AccessMode.DIRECT, HASH, "credential-ref", "7",
            new AlipayAccountIdentity("app", "seller", null, null, null), Set.of(PaymentMethod.PAYMENT_CODE));
    private final ChannelExecutionContext expectedContext = new ChannelExecutionContext("tenant", frozenRevision);

    @Test
    void resolvesFrozenRevisionAndForwardsContextForPaymentRecoveryAndRefundQueries() {
        var payment = new PreparedChannelPayment(identity, AMOUNT, PaymentMethod.PAYMENT_CODE, 1, "input-ref");
        var paymentRequest = new ChannelPaymentRequest(payment, "subject", java.time.OffsetDateTime.parse("2026-10-23T12:00:00Z"),
                new ChannelPaymentCodeInput("123456"));
        var refund = new PreparedChannelRefund(identity, "refund-1", "channel-refund-1", Money.ofMinor(500, "CNY"),
                AMOUNT, "channel-payment-1");
        var paymentResult = paymentSubmission(identity);
        var queryResult = paymentObservation(identity);
        var recovery = new ChannelPaymentRecovery(identity, AMOUNT, PaymentMethod.PAYMENT_CODE, 1,
                OffsetDateTime.parse("2026-10-24T12:00:00Z"));
        var recoveryResult = paymentSubmission(identity);
        var refundResult = refundObservation(refund);
        when(bindings.findRevision("tenant", "frozen-revision")).thenReturn(Optional.of(frozenRevision));
        when(requests.create("tenant", payment)).thenReturn(paymentRequest);
        when(channels.submit(expectedContext, paymentRequest)).thenReturn(paymentResult);
        when(channels.query(expectedContext, target)).thenReturn(queryResult);
        when(channels.recover(expectedContext, recovery)).thenReturn(recoveryResult);
        when(channels.close(expectedContext, target)).thenReturn(queryResult);
        when(channels.submitRefund(expectedContext, refund)).thenReturn(refundResult);
        when(channels.queryRefund(expectedContext, refund)).thenReturn(refundResult);
        when(channels.recoverRefund(expectedContext, refund)).thenReturn(refundResult);

        TenantUtil.runWithTenant("tenant", () -> {
            assertSame(paymentResult, gateway.submit(payment));
            assertSame(queryResult, gateway.query(target));
            assertSame(recoveryResult, gateway.recover(recovery));
            assertSame(queryResult, gateway.close(target));
            assertSame(refundResult, gateway.submitRefund(refund));
            assertSame(refundResult, gateway.queryRefund(refund));
            assertSame(refundResult, gateway.recoverRefund(refund));
        });

        verify(bindings, times(7)).findRevision("tenant", "frozen-revision");
        verify(bindings, never()).lockActive(anyString(), anyString(), any(), anySet(), any());
        verify(requests).create("tenant", payment);
        verify(channels).submit(expectedContext, paymentRequest);
        verify(channels).query(expectedContext, target);
        verify(channels).recover(expectedContext, recovery);
        verify(channels).close(expectedContext, target);
        verify(channels).submitRefund(expectedContext, refund);
        verify(channels).queryRefund(expectedContext, refund);
        verify(channels).recoverRefund(expectedContext, refund);
        verifyNoMoreInteractions(requests);
    }

    @Test
    void recoveryUsesFrozenContextWithoutCreatingARequestOrConsultingActiveBinding() {
        var recovery = new ChannelPaymentRecovery(identity, AMOUNT, PaymentMethod.PAYMENT_CODE, 1,
                OffsetDateTime.parse("2026-10-24T12:00:00Z"));
        var result = paymentSubmission(identity);
        when(bindings.findRevision("tenant", "frozen-revision")).thenReturn(Optional.of(frozenRevision));
        when(channels.recover(expectedContext, recovery)).thenReturn(result);

        TenantUtil.runWithTenant("tenant", () -> assertSame(result, gateway.recover(recovery)));

        verify(bindings).findRevision("tenant", "frozen-revision");
        verify(bindings, never()).lockActive(anyString(), anyString(), any(), anySet(), any());
        verify(channels).recover(expectedContext, recovery);
        verifyNoInteractions(requests);
        verifyNoMoreInteractions(channels);
    }

    @Test
    void capabilityLookupNeverResolvesBindingsAndRemainsLocalInsideTransaction() {
        var refund = new PreparedChannelRefund(identity, "refund-1", "channel-refund-1", Money.ofMinor(500, "CNY"),
                AMOUNT, "channel-payment-1");
        when(channels.canRefund(refund)).thenReturn(true);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertTrue(gateway.canRefund(refund));
        } finally {
            TransactionSynchronizationManager.clear();
        }

        verify(channels).canRefund(refund);
        verifyNoInteractions(bindings);
        verifyNoInteractions(requests);
        verifyNoMoreInteractions(channels);
    }

    @Test
    void missingTenantAndActiveTransactionStopBeforeBindingOrFacadeIo() {
        var payment = new PreparedChannelPayment(identity, AMOUNT, PaymentMethod.PAYMENT_CODE, 1, "input-ref");
        var recovery = new ChannelPaymentRecovery(identity, AMOUNT, PaymentMethod.PAYMENT_CODE, 1,
                OffsetDateTime.parse("2026-10-24T12:00:00Z"));
        assertThrows(CommonException.class, () -> gateway.submit(payment));
        verifyNoInteractions(bindings, channels, requests);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(CommonException.class, () -> gateway.query(target));
            assertThrows(CommonException.class, () -> gateway.recover(recovery));
            assertThrows(CommonException.class, () -> gateway.close(target));
        } finally {
            TransactionSynchronizationManager.clear();
        }
        verifyNoInteractions(bindings, channels);
    }

    @Test
    void missingFrozenRevisionPreventsProviderInvocation() {
        when(bindings.findRevision("tenant", "frozen-revision")).thenReturn(Optional.empty());
        TenantUtil.runWithTenant("tenant", () -> assertThrows(CommonException.class, () -> gateway.query(target)));
        verify(bindings).findRevision("tenant", "frozen-revision");
        verifyNoInteractions(channels, requests);
    }

    private static ChannelPaymentSubmission paymentSubmission(ChannelPaymentIdentity identity) {
        return new ChannelPaymentSubmission(identity, AMOUNT, paymentObservation(identity), null);
    }

    private static PaymentObservation paymentObservation(ChannelPaymentIdentity identity) {
        return new PaymentObservation(identity, ChannelPaymentOutcome.Kind.ACCEPTED, AMOUNT, null, null,
                PaymentObservation.Source.QUERY_RESPONSE, "receipt-1", "b".repeat(64));
    }

    private static RefundObservation refundObservation(PreparedChannelRefund refund) {
        return new RefundObservation(refund.refundId(), refund.original(), refund.channelRefundNo(),
                refund.originalChannelTransactionNo(), ChannelRefundOutcome.Kind.ACCEPTED, refund.amount(), null,
                null, RefundObservation.Source.QUERY_RESPONSE, "receipt-2", "c".repeat(64), null);
    }
}
