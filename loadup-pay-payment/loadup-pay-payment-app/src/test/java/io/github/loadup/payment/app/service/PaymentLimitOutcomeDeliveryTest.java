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
package io.github.loadup.payment.app.service;

import static io.github.loadup.payment.client.error.PaymentResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.components.task.TaskExecution;
import io.github.loadup.components.task.TaskSubmission;
import io.github.loadup.contracts.event.BusinessEventTypes;
import io.github.loadup.payment.app.converter.PaymentOutcomeDeliveryConverter;
import io.github.loadup.payment.app.converter.PaymentResultConverter;
import io.github.loadup.payment.app.dispatch.PaymentDeliveryRoutes;
import io.github.loadup.payment.app.model.PaymentDeliverySnapshot;
import io.github.loadup.payment.app.model.limitcenter.dto.LimitReleaseDTO;
import io.github.loadup.payment.app.model.limitcenter.dto.LimitReservationDTO;
import io.github.loadup.payment.app.model.limitcenter.enums.LimitReservationState;
import io.github.loadup.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.payment.app.port.PaymentLimitSettlement;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PaymentLimitOutcomeDeliveryTest {
    final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(PaymentOutcomeDeliveryConverter.class.getPackageName());
    final PaymentOutcomeDeliverySupport support = mock(PaymentOutcomeDeliverySupport.class);
    final PaymentLimitSettlement limits = mock(PaymentLimitSettlement.class);
    final PaymentAcceptanceCodec intents = mock(PaymentAcceptanceCodec.class);
    final PaymentLimitOutcomeDelivery delivery = new PaymentLimitOutcomeDelivery(
            support, limits, intents, context.getBean(PaymentOutcomeDeliveryConverter.class));
    final TaskExecution event = new TaskExecution(
            "queue-event",
            new TaskSubmission(PaymentDeliveryRoutes.LIMIT, 1, "payment", "{}", LocalDateTime.now(), ""),
            AttemptFixtures.NOW.atZone(BusinessTime.zone()).toInstant(),
            null);
    PaymentDeliverySnapshot snapshot;

    LimitReservationDTO reservation(LimitReservationState state) {
        var policy = AttemptFixtures.command().admittedTerms().terms().limits();
        return new LimitReservationDTO(
                "reservation",
                "operation",
                "payment",
                state,
                policy.policyId(),
                policy.policyVersion(),
                BusinessTime.external(AttemptFixtures.NOW),
                2);
    }

    void source(boolean succeeded) {
        snapshot = OutcomeDeliveryFixtures.snapshot(succeeded, true, PaymentDeliveryRoutes.LIMIT);
        when(support.load(event, PaymentDeliveryRoutes.LIMIT)).thenReturn(snapshot);
    }

    @BeforeEach
    void setup() {
        source(true);
        when(intents.decode(any())).thenReturn(AttemptFixtures.command());
        when(limits.confirm(any())).thenAnswer(call -> {
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
        verify(limits)
                .confirm(argThat(c -> c.operationId().equals("operation")
                        && c.paymentId().equals("payment")
                        && c.businessEventId().equals("evidence")
                        && c.successEvidenceId().equals("evidence")
                        && c.paymentVersion() == snapshot.payment().payment().version()
                        && c.amountMinor() == 4000
                        && c.currency().equals("CNY")
                        && c.succeededAt().equals(BusinessTime.external(AttemptFixtures.NOW))));
        verify(limits, never()).release(any());
    }

    @Test
    void duplicateDeliveryKeepsTheIdenticalIdempotentCommand() {
        delivery.deliver(event);
        delivery.deliver(event);
        var expected = context.getBean(PaymentOutcomeDeliveryConverter.class)
                .toConfirm(snapshot.payment(), "evidence", AttemptFixtures.NOW, null);
        verify(limits, times(2)).confirm(expected);
    }

    @Test
    void releaseBeforeReserveAcceptsNullableReservationAndRequiresFencedEvidence() {
        source(false);
        when(limits.release(any())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new LimitReleaseDTO(
                    "operation",
                    "payment",
                    true,
                    null,
                    snapshot.payment().payment().version(),
                    "evidence");
        });
        delivery.deliver(event);
        verify(limits)
                .release(argThat(c -> c.reasonCode().equals("PAYMENT_FAILED")
                        && c.businessEventId().equals("evidence")
                        && c.noCollectionEvidenceId().equals("evidence")));
        verify(limits, never()).confirm(any());
    }

    @Test
    void previouslyExpiredReservationIsAValidNoCollectionReleaseReceipt() {
        source(false);
        when(limits.release(any()))
                .thenReturn(new LimitReleaseDTO(
                        "operation",
                        "payment",
                        true,
                        reservation(LimitReservationState.EXPIRED),
                        snapshot.payment().payment().version(),
                        "evidence"));
        assertDoesNotThrow(() -> delivery.deliver(event));
    }

    @Test
    void confirmAckCannotClaimAnotherOperationOrWrongState() {
        when(limits.confirm(any())).thenReturn(reservation(LimitReservationState.RELEASED));
        assertSame(
                DEPENDENCY_UNAVAILABLE,
                assertThrows(CommonException.class, () -> delivery.deliver(event))
                        .getResultCode());
        when(limits.confirm(any()))
                .thenReturn(new LimitReservationDTO(
                        "reservation",
                        "other-operation",
                        "payment",
                        LimitReservationState.CONFIRMED,
                        "policy",
                        1,
                        BusinessTime.external(AttemptFixtures.NOW),
                        2));
        assertThrows(CommonException.class, () -> delivery.deliver(event));
    }

    @Test
    void releaseAckMustBindTheExactPositivePaymentVersionAndProof() {
        source(false);
        when(limits.release(any()))
                .thenReturn(new LimitReleaseDTO(
                        "operation",
                        "payment",
                        true,
                        null,
                        snapshot.payment().payment().version() + 1,
                        "evidence"));
        assertSame(
                DEPENDENCY_UNAVAILABLE,
                assertThrows(CommonException.class, () -> delivery.deliver(event))
                        .getResultCode());
    }

    @Test
    void receiverFailurePropagatesSoDispatcherCanRetryWithoutPretendingDeliverySucceeded() {
        doThrow(new CommonException(DEPENDENCY_UNAVAILABLE)).when(limits).confirm(any());
        assertThrows(CommonException.class, () -> delivery.deliver(event));
    }

    void completeSource(boolean succeeded) {
        source(succeeded);
        var legacy = snapshot.envelope();
        var proof = context.getBean(PaymentResultConverter.class)
                .toFinancialEvidence(
                        snapshot.payment(),
                        succeeded ? BusinessTime.external(snapshot.succeededAt()) : null,
                        !succeeded);
        var complete = new EventEnvelope<>(
                legacy.deliveryId(),
                legacy.businessEventId(),
                legacy.producer(),
                BusinessEventTypes.PAYMENT_FINANCIAL,
                1,
                legacy.aggregateId(),
                legacy.aggregateVersion(),
                legacy.occurredAt(),
                legacy.data().withFinancialEvidence(proof));
        snapshot =
                new PaymentDeliverySnapshot(complete, snapshot.payment(), snapshot.attempt(), snapshot.succeededAt());
        when(support.load(event, PaymentDeliveryRoutes.LIMIT)).thenReturn(snapshot);
    }

    @Test
    void completeFrozenEvidenceIsForwardedUnchangedToConfirmAndReleaseCommands() {
        completeSource(true);
        var positive = snapshot.envelope().data().financialEvidence();
        delivery.deliver(event);
        verify(limits).confirm(argThat(c -> positive.equals(c.financialEvidence())));
        completeSource(false);
        var negative = snapshot.envelope().data().financialEvidence();
        when(limits.release(any()))
                .thenReturn(new LimitReleaseDTO(
                        "operation",
                        "payment",
                        true,
                        null,
                        snapshot.payment().payment().version(),
                        "evidence"));
        delivery.deliver(event);
        verify(limits).release(argThat(c -> negative.equals(c.financialEvidence())));
    }
}
