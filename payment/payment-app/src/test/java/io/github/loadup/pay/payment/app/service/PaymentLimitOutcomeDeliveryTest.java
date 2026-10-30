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
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.components.outbox.OutboxEvent;
import io.github.loadup.components.outbox.OutboxMessage;
import io.github.loadup.pay.limitcenter.client.dto.LimitReleaseDTO;
import io.github.loadup.pay.limitcenter.client.dto.LimitReservationDTO;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservationState;
import io.github.loadup.pay.payment.app.converter.PaymentOutcomeDeliveryConverter;
import io.github.loadup.pay.payment.app.model.PaymentDeliverySnapshot;
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.app.port.PaymentLimitClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentLimitOutcomeDeliveryTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(PaymentOutcomeDeliveryConverter.class.getPackageName());
    final PaymentOutcomeDeliverySupport support = mock(PaymentOutcomeDeliverySupport.class);
    final PaymentLimitClient limits = mock(PaymentLimitClient.class);
    final PaymentAcceptanceCodec intents = mock(PaymentAcceptanceCodec.class);
    final PaymentLimitOutcomeDelivery delivery = new PaymentLimitOutcomeDelivery(support, limits, intents,
            context.getBean(PaymentOutcomeDeliveryConverter.class));
    final OutboxEvent event = new OutboxEvent("queue-event", new OutboxMessage(PaymentEvents.LIMIT, 1, "payment", "{}"),
            AttemptFixtures.NOW.atZone(BusinessTime.zone()).toInstant(), null);
    PaymentDeliverySnapshot snapshot;

    LimitReservationDTO reservation(LimitReservationState state) {
        var policy = AttemptFixtures.command().admittedTerms().terms().limits();
        return new LimitReservationDTO("reservation", "operation", "payment", state, policy.policyId(), policy.policyVersion(),
                BusinessTime.external(AttemptFixtures.NOW), 2);
    }

    void source(boolean succeeded) {
        snapshot = OutcomeDeliveryFixtures.snapshot(succeeded, true, PaymentEvents.LIMIT);
        when(support.load(event, PaymentEvents.LIMIT)).thenReturn(snapshot);
    }

    @BeforeEach
    void setup() {
        source(true);
        when(limits.supportsTenant("tenant")).thenReturn(true);
        when(intents.decode(any())).thenReturn(AttemptFixtures.command());
        when(limits.confirm(eq("tenant"), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return reservation(LimitReservationState.CONFIRMED);
        });
    }

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void confirmedQuotaUsesOriginalSuccessTimeEvidenceVersionAndAmount() {
        delivery.deliver(event);
        verify(limits).confirm(eq("tenant"), argThat(c -> c.operationId().equals("operation") && c.paymentId().equals("payment")
                && c.businessEventId().equals("evidence") && c.successEvidenceId().equals("evidence")
                && c.paymentVersion() == snapshot.payment().payment().version() && c.amountMinor() == 4000
                && c.currency().equals("CNY") && c.succeededAt().equals(BusinessTime.external(AttemptFixtures.NOW))));
        verify(limits, never()).release(anyString(), any());
    }

    @Test
    void duplicateDeliveryKeepsTheIdenticalIdempotentCommand() {
        delivery.deliver(event);
        delivery.deliver(event);
        var expected = context.getBean(PaymentOutcomeDeliveryConverter.class).toConfirm(snapshot.payment(), "evidence", AttemptFixtures.NOW);
        verify(limits, times(2)).confirm("tenant", expected);
    }

    @Test
    void releaseBeforeReserveAcceptsNullableReservationAndRequiresFencedEvidence() {
        source(false);
        when(limits.release(eq("tenant"), any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new LimitReleaseDTO("operation", "payment", true, null, snapshot.payment().payment().version(), "evidence");
        });
        delivery.deliver(event);
        verify(limits).release(eq("tenant"), argThat(c -> c.reasonCode().equals("PAYMENT_FAILED")
                && c.businessEventId().equals("evidence") && c.noCollectionEvidenceId().equals("evidence")));
        verify(limits, never()).confirm(anyString(), any());
    }

    @Test
    void previouslyExpiredReservationIsAValidNoCollectionReleaseReceipt() {
        source(false);
        when(limits.release(eq("tenant"), any())).thenReturn(new LimitReleaseDTO("operation", "payment", true,
                reservation(LimitReservationState.EXPIRED), snapshot.payment().payment().version(), "evidence"));
        assertDoesNotThrow(() -> delivery.deliver(event));
    }

    @Test
    void confirmAckCannotClaimAnotherOperationOrWrongState() {
        when(limits.confirm(eq("tenant"), any())).thenReturn(reservation(LimitReservationState.RELEASED));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, () -> delivery.deliver(event)).getResultCode());
        when(limits.confirm(eq("tenant"), any())).thenReturn(new LimitReservationDTO("reservation", "other-operation", "payment",
                LimitReservationState.CONFIRMED, "policy", 1, BusinessTime.external(AttemptFixtures.NOW), 2));
        assertThrows(CommonException.class, () -> delivery.deliver(event));
    }

    @Test
    void releaseAckMustBindTheExactPositivePaymentVersionAndProof() {
        source(false);
        when(limits.release(eq("tenant"), any())).thenReturn(new LimitReleaseDTO("operation", "payment", true,
                null, snapshot.payment().payment().version() + 1, "evidence"));
        assertSame(DEPENDENCY_UNAVAILABLE, assertThrows(CommonException.class, () -> delivery.deliver(event)).getResultCode());
    }

    @Test
    void receiverFailurePropagatesSoDispatcherCanRetryWithoutPretendingDeliverySucceeded() {
        doThrow(new CommonException(DEPENDENCY_UNAVAILABLE)).when(limits).confirm(eq("tenant"), any());
        assertThrows(CommonException.class, () -> delivery.deliver(event));
    }

    @Test
    void tenantWithoutConfiguredOutboundIdentityNeverCallsSettlement() {
        when(limits.supportsTenant("tenant")).thenReturn(false);
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> delivery.deliver(event)).getResultCode());
        verify(limits, never()).confirm(anyString(), any());
        verify(limits, never()).release(anyString(), any());
    }
}
