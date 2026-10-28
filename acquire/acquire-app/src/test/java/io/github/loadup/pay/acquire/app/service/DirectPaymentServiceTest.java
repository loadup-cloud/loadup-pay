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
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.pay.acquire.app.converter.AcquireOrderConverter;
import io.github.loadup.pay.acquire.app.converter.DirectPaymentConverter;
import io.github.loadup.pay.acquire.app.security.AcquireActor;
import io.github.loadup.pay.acquire.client.command.DirectPayCommand;
import io.github.loadup.pay.acquire.client.command.DirectPayRecoverCommand;
import io.github.loadup.pay.acquire.client.command.PaymentStartCommand;
import io.github.loadup.pay.acquire.client.dto.AcquireOrderDTO;
import io.github.loadup.pay.acquire.client.dto.PaymentProgressDTO;
import io.github.loadup.pay.acquire.client.dto.PaymentRecoveryDTO;
import io.github.loadup.pay.acquire.client.dto.PaymentStartDTO;
import io.github.loadup.pay.acquire.domain.model.PaymentInteractionKind;
import io.github.loadup.pay.acquire.domain.model.PaymentRecoveryState;
import io.github.loadup.pay.acquire.client.facade.AcquireOrderCreationFacade;
import io.github.loadup.pay.acquire.client.facade.PaymentInitiationFacade;
import io.github.loadup.pay.acquire.client.facade.PaymentRecoveryFacade;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.UnpaidOrderAction;
import io.github.loadup.pay.channel.api.model.H5SceneType;
import io.github.loadup.pay.payment.client.dto.H5SceneInteractionDTO;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.pay.payment.client.dto.PaymentExecutionDTO;
import io.github.loadup.pay.payment.domain.model.PaymentState;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.NOW;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.OWNER;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.order;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.IDEMPOTENCY_CONFLICT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.INVALID_ARGUMENT;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DirectPaymentServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(DirectPaymentConverter.class.getPackageName());
    final jakarta.validation.ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final AcquireOrderCreationFacade orders = mock(AcquireOrderCreationFacade.class);
    final PaymentInitiationFacade payments = mock(PaymentInitiationFacade.class);
    final PaymentRecoveryFacade recovery = mock(PaymentRecoveryFacade.class);
    final AtomicReference<AcquireActor> actor = new AtomicReference<>(new AcquireActor("tenant", OWNER, null,
            Set.of("pay:acquire:create", "pay:acquire:read", "pay:acquire:payment:start", "pay:acquire:payment:recover")));
    final DirectPaymentService service = new DirectPaymentService(actor::get, orders, payments, recovery,
            context.getBean(DirectPaymentConverter.class), validation.getValidator());
    final PaymentChoiceDTO choice = new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5);
    final DirectPayCommand command = new DirectPayCommand("merchant", "M001", "scope", 10000, "CNY", "subject", null,
            "action", 4000, choice, new H5SceneInteractionDTO("203.0.113.10", H5SceneType.WAP));
    final AcquireOrderDTO order = context.getBean(AcquireOrderConverter.class).toDTO(
            order(AcquireOrderState.PENDING, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10)), NOW);
    final PaymentProgressDTO progress = new PaymentProgressDTO(new PaymentExecutionDTO("payment", "order", "allocation", 4000, "CNY",
            PaymentState.INIT, null, null, null, 0L, BusinessTime.external(NOW)), order);
    final PaymentRecoveryDTO resolved = new PaymentRecoveryDTO("order", "action", PaymentRecoveryState.RESOLVED, "payment", true, progress);

    @BeforeEach
    void setup() {
        when(orders.createDirectPay(any())).thenReturn(order);
        when(payments.startPayment(any())).thenReturn(new PaymentStartDTO("order", "payment", "allocation", PaymentState.INIT, null, 0L));
    }

    @AfterEach
    void close() {
        context.close();
        validation.close();
    }

    @Test
    void newDirectPayFreezesNonSensitiveMetadataThenStartsOnlyTheMissingOriginalAction() {
        when(recovery.recover(any())).thenReturn(new PaymentRecoveryDTO("order", "action", PaymentRecoveryState.NOT_OBSERVED, null, null, null), resolved);
        var result = service.directPay(command);
        assertSame(resolved, result.payment());
        assertEquals(order, result.order());
        var metadata = ArgumentCaptor.forClass(DirectPayRecoverCommand.class);
        verify(orders).createDirectPay(metadata.capture());
        assertEquals(PaymentInteractionKind.H5_SCENE, metadata.getValue().interactionKind());
        assertEquals("action", metadata.getValue().actionKey());
        assertEquals(4000, metadata.getValue().firstPaymentAmountMinor());
        assertFalse(JsonUtil.toJson(metadata.getValue()).contains("203.0.113.10"));
        assertFalse(JsonUtil.toJson(metadata.getValue()).contains("payerClientIp"));
        var start = ArgumentCaptor.forClass(PaymentStartCommand.class);
        verify(payments).startPayment(start.capture());
        assertEquals("order", start.getValue().orderId());
        assertEquals(command.interaction(), start.getValue().interaction());
        assertEquals("action", start.getValue().actionKey());
        verify(orders, never()).create(any());
    }

    @Test
    void aLostPreviousReplyIsResolvedWithoutResubmittingRawInput() {
        when(recovery.recover(any())).thenReturn(resolved);
        assertSame(resolved, service.directPay(command).payment());
        verifyNoInteractions(payments);
    }

    @Test
    void aNeverAcceptedExpiredActionRemainsAnExplicitResultWithoutInventingAPayment() {
        var absent = new PaymentRecoveryDTO("order", "action", PaymentRecoveryState.RESOLVED, "payment", false, new PaymentProgressDTO(null, order));
        when(recovery.recover(any())).thenReturn(absent);
        var result = service.directPay(command);
        assertEquals(Boolean.FALSE, result.payment().accepted());
        assertNull(result.payment().progress().payment());
        verifyNoInteractions(payments);
    }

    @Test
    void firstActionOrEntryConflictsStopBeforeAnyPaymentWork() {
        when(orders.createDirectPay(any())).thenThrow(new CommonException(IDEMPOTENCY_CONFLICT));
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service.directPay(command)).getResultCode());
        verifyNoInteractions(payments, recovery);
    }

    @Test
    void incompleteOrUnboundRecoveryCannotTriggerAnotherPayment() {
        when(recovery.recover(any())).thenReturn(new PaymentRecoveryDTO("other-order", "action", PaymentRecoveryState.NOT_OBSERVED, null, null, null));
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, () -> service.directPay(command)).getResultCode());
        verifyNoInteractions(payments);
    }

    @Test
    void permissionsAndFirstAmountAreCheckedBeforeCreation() {
        actor.set(new AcquireActor("tenant", OWNER, null, Set.of("pay:acquire:create", "pay:acquire:read", "pay:acquire:payment:start")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.directPay(command)).getResultCode());
        var excessive = new DirectPayCommand("merchant", "M001", "scope", 10000, "CNY", "subject", null, "action", 10001, choice, command.interaction());
        assertSame(INVALID_ARGUMENT, assertThrows(CommonException.class, () -> service.directPay(excessive)).getResultCode());
        verifyNoInteractions(orders, payments, recovery);
    }
}
