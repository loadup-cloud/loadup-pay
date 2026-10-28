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
import io.github.loadup.pay.payment.app.converter.PaymentOutcomeDeliveryConverter;
import io.github.loadup.pay.payment.app.converter.PaymentSubmissionConverter;
import io.github.loadup.pay.payment.app.model.PaymentDeliverySnapshot;
import io.github.loadup.pay.payment.domain.gateway.RiskOutcomeGateway;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import io.github.loadup.pay.payment.domain.model.RiskDecisionType;
import io.github.loadup.pay.payment.domain.model.RiskOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentRiskOutcomeDeliveryTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(PaymentOutcomeDeliveryConverter.class.getPackageName());
    final PaymentOutcomeDeliverySupport support = mock(PaymentOutcomeDeliverySupport.class);
    final RiskOutcomeGateway risk = mock(RiskOutcomeGateway.class);
    final PaymentRiskOutcomeDelivery delivery = new PaymentRiskOutcomeDelivery(support, context.getBean(PaymentOutcomeDeliveryConverter.class),
            context.getBean(PaymentSubmissionConverter.class), risk);
    final OutboxEvent event = new OutboxEvent("queue-event", new OutboxMessage("tenant", PaymentEvents.RISK, 1, "payment", "{}"),
            AttemptFixtures.NOW.atZone(BusinessTime.zone()).toInstant(), null);

    @AfterEach
    void close() {
        context.close();
    }

    void source(boolean succeeded, boolean consulted) {
        when(support.load(event, PaymentEvents.RISK)).thenReturn(OutcomeDeliveryFixtures.snapshot(succeeded, consulted, PaymentEvents.RISK));
    }

    @Test
    void sendsOriginalConsultationDecisionAndTerminalSourceIdentityOutsideTransaction() {
        source(true, true);
        doAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(risk).confirm(any());
        delivery.deliver(event);
        verify(risk).confirm(argThat(outcome -> outcome.tenantId().equals("tenant") && outcome.businessEventId().equals("evidence")
                && outcome.evidenceId().equals("evidence") && outcome.paymentState() == PaymentState.SUCCEEDED
                && outcome.consultation().attemptId().equals("attempt") && outcome.consultation().amount().getCent() == 4000
                && outcome.decision().decision() == RiskDecisionType.DEFAULT_ALLOW && outcome.sourceVersion() > 0));
    }

    @Test
    void riskDenialUsesTheActuallyPersistedDeniedConsultation() {
        source(false, true);
        delivery.deliver(event);
        verify(risk).confirm(argThat(value -> value.decision().decision() == RiskDecisionType.DENY
                && value.decision().consultationRef().equals("consultation") && value.paymentState() == PaymentState.FAILED));
    }

    @Test
    void localPreConsultationCloseDoesNotInventARiskDecision() {
        source(false, false);
        delivery.deliver(event);
        verifyNoInteractions(risk);
    }

    @Test
    void localCloseWithoutAnAttemptHasNoConsultationToConfirm() {
        var original = OutcomeDeliveryFixtures.snapshot(false, false, PaymentEvents.RISK);
        when(support.load(event, PaymentEvents.RISK)).thenReturn(new PaymentDeliverySnapshot(original.tenantId(), original.envelope(),
                original.payment(), null, null));
        delivery.deliver(event);
        verifyNoInteractions(risk);
    }

    @Test
    void retriesKeepTheSameTenantBusinessEventAndOriginalDecision() {
        source(true, true);
        delivery.deliver(event);
        delivery.deliver(event);
        var capture = org.mockito.ArgumentCaptor.forClass(RiskOutcome.class);
        verify(risk, times(2)).confirm(capture.capture());
        assertEquals(capture.getAllValues().getFirst(), capture.getAllValues().getLast());
    }

    @Test
    void configuredProviderFailureCannotBecomeDefaultAllowOrACompletedNotification() {
        source(true, true);
        doThrow(new CommonException(DEPENDENCY_UNAVAILABLE)).when(risk).confirm(any());
        assertThrows(CommonException.class, () -> delivery.deliver(event));
    }
}
