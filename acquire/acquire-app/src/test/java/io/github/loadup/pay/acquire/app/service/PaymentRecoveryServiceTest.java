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
package io.github.loadup.pay.acquire.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.acquire.app.converter.AcquireOrderConverter;
import io.github.loadup.pay.acquire.app.converter.PaymentRecoveryConverter;
import io.github.loadup.pay.acquire.app.security.AcquireActor;
import io.github.loadup.pay.acquire.client.command.PaymentProgressRefreshCommand;
import io.github.loadup.pay.acquire.client.command.PaymentRecoverCommand;
import io.github.loadup.pay.acquire.client.dto.PaymentProgressDTO;
import io.github.loadup.pay.acquire.client.enums.PaymentRecoveryState;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;
import io.github.loadup.pay.acquire.domain.model.UnpaidOrderAction;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.pay.payment.client.dto.PaymentCloseDTO;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.client.enums.PaymentState;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.NOW;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.OWNER;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.order;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.DEPENDENCY_UNAVAILABLE;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentRecoveryServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(PaymentRecoveryConverter.class.getPackageName());
    final jakarta.validation.ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final PaymentInitiationOperations initiation = mock(PaymentInitiationOperations.class);
    final PaymentOutcomeOperations outcomes = mock(PaymentOutcomeOperations.class);
    final AtomicReference<AcquireActor> actor = new AtomicReference<>(new AcquireActor("tenant", OWNER, null, Set.of("pay:acquire:payment:recover")));
    final PaymentRecoveryService service = new PaymentRecoveryService(actor::get, initiation, outcomes,
            context.getBean(PaymentRecoveryConverter.class), validation.getValidator());
    final PaymentChoiceDTO choice = new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5);
    final PaymentRecoverCommand command = new PaymentRecoverCommand("order", "action", 1000L, choice);
    final StoredAcquireOrder original = order(AcquireOrderState.PENDING, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10));
    final PaymentExecutionDTO payment = new PaymentExecutionDTO("payment", "order", "allocation", 1000L, "CNY", PaymentState.INIT,
            null, null, null, 0L, BusinessTime.external(NOW));
    final PaymentProgressDTO progress = new PaymentProgressDTO(payment, context.getBean(AcquireOrderConverter.class).toDTO(original, NOW));

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    @Test
    void recoveryUsesMerchantOwnershipAndTheExistingConvergencePath() {
        when(initiation.recover(any(), eq("order"), eq("action"), eq(1000L), eq(choice)))
                .thenReturn(new PaymentIntentRecovery(PaymentIntentRecovery.State.RESOLVED, "payment", null));
        when(outcomes.refresh(any(), any())).thenReturn(progress);
        var result = service.recover(command);
        assertEquals(PaymentRecoveryState.RESOLVED, result.state());
        assertEquals("payment", result.paymentId());
        assertEquals(Boolean.TRUE, result.accepted());
        assertSame(progress, result.progress());
        var principal = ArgumentCaptor.forClass(PaymentInitiationPrincipal.class);
        verify(initiation).recover(principal.capture(), eq("order"), eq("action"), eq(1000L), eq(choice));
        assertEquals("merchant/" + OWNER, principal.getValue().actionScope());
        assertEquals("tenant", principal.getValue().tenantId());
        principal.getValue().authorize(original, false);
        assertThrows(CommonException.class, () -> principal.getValue().authorize(original, true));
        var other = new StoredAcquireOrder(original.order(), original.merchantOrderNo(), original.subject(), original.admission(), original.admittedAt(),
                "b".repeat(64), original.snapshotId(), original.admissionPayload(), original.payloadHash());
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> principal.getValue().authorize(other, false)).getResultCode());
        verify(outcomes).refresh(new OrderPaymentScope("tenant", OWNER), new PaymentProgressRefreshCommand("order", "payment"));
        verify(initiation, never()).start(any(), any());
    }

    @Test
    void unresolvedActionsNeverReportAcceptedFalseOrCallFinancialConvergence() {
        for (var state : java.util.List.of(PaymentIntentRecovery.State.NOT_OBSERVED, PaymentIntentRecovery.State.INPUT_REQUIRED)) {
            when(initiation.recover(any(), anyString(), anyString(), anyLong(), any())).thenReturn(new PaymentIntentRecovery(state, null, null));
            var result = service.recover(command);
            assertEquals(state.name(), result.state().name());
            assertNull(result.paymentId());
            assertNull(result.accepted());
            assertNull(result.progress());
            assertTrue(io.github.loadup.commons.json.JsonUtil.toJson(result).contains("\"accepted\":null"));
        }
        verifyNoInteractions(outcomes);
    }

    @Test
    void anExpiredNeverAcceptedProofMustConvergeBeforeReportingNonAcceptance() {
        var proof = new PaymentCloseDTO("payment", "order", 1L, true, null, "never-accepted");
        when(initiation.recover(any(), anyString(), anyString(), anyLong(), any()))
                .thenReturn(new PaymentIntentRecovery(PaymentIntentRecovery.State.RESOLVED, "payment", proof));
        var absent = new PaymentProgressDTO(null, progress.order());
        when(outcomes.applyInputExpiry(any(), anyString(), anyString(), any())).thenReturn(absent);
        var result = service.recover(command);
        assertEquals(Boolean.FALSE, result.accepted());
        assertTrue(io.github.loadup.commons.json.JsonUtil.toJson(result).contains("\"accepted\":false"));
        assertSame(absent, result.progress());
        verify(outcomes).applyInputExpiry(new OrderPaymentScope("tenant", OWNER), "order", "payment", proof);
        verify(outcomes, never()).refresh(any(), any());
    }

    @Test
    void startingPermissionAloneDoesNotGrantRecoveryAndOuterTransactionsAreRejected() {
        actor.set(new AcquireActor("tenant", OWNER, null, Set.of("pay:acquire:payment:start")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.recover(command)).getResultCode());
        actor.set(new AcquireActor("tenant", OWNER, null, Set.of("pay:acquire:payment:recover")));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertSame(INVALID_STATE, assertThrows(CommonException.class, () -> service.recover(command)).getResultCode());
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        verifyNoInteractions(initiation, outcomes);
    }

    @Test
    void invalidMetadataAndDependencyErrorsCannotInventARecoveryResult() {
        assertSame(INVALID_ARGUMENT, assertThrows(CommonException.class,
                () -> service.recover(new PaymentRecoverCommand("order", "action", 0L, choice))).getResultCode());
        var failure = new CommonException(DEPENDENCY_UNAVAILABLE);
        when(initiation.recover(any(), anyString(), anyString(), anyLong(), any())).thenThrow(failure);
        assertSame(failure, assertThrows(CommonException.class, () -> service.recover(command)));
        verifyNoInteractions(outcomes);
    }
}
