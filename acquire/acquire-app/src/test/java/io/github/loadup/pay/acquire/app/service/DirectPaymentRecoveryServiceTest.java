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
import io.github.loadup.pay.acquire.app.converter.*;
import io.github.loadup.pay.acquire.app.port.OrderCreationCodec;
import io.github.loadup.pay.acquire.app.security.AcquireActor;
import io.github.loadup.pay.acquire.client.command.*;
import io.github.loadup.pay.acquire.client.dto.*;
import io.github.loadup.pay.acquire.client.enums.*;
import io.github.loadup.pay.acquire.client.facade.*;
import io.github.loadup.pay.acquire.domain.gateway.OrderCreationGateway;
import io.github.loadup.pay.acquire.domain.model.*;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import static io.github.loadup.pay.acquire.app.service.CheckoutFixtures.*;
import static io.github.loadup.pay.acquire.client.error.AcquireResultCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DirectPaymentRecoveryServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(DirectPaymentConverter.class.getPackageName());
    final jakarta.validation.ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final OrderCreationGateway store = mock(OrderCreationGateway.class);
    final OrderCreationCodec codec = mock(OrderCreationCodec.class);
    final AcquireOrderCreationFacade orders = mock(AcquireOrderCreationFacade.class);
    final PaymentRecoveryFacade payments = mock(PaymentRecoveryFacade.class);
    final AtomicReference<AcquireActor> actor = new AtomicReference<>(new AcquireActor("tenant", OWNER, null,
            Set.of("pay:acquire:direct-pay:recover", "pay:acquire:create", "pay:acquire:read", "pay:acquire:payment:recover")));
    final DirectPayRecoverCommand command = new DirectPayRecoverCommand("merchant", "M001", "scope", 10000, "CNY", "subject", null,
            "action", 4000, new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5), PaymentInteractionKind.H5_SCENE);
    final OrderCreateIntent init = new OrderCreateIntent("intent", "order", "merchant", "M001", "a".repeat(64), OWNER,
            "DIRECT_PAY", null, "metadata", "INIT", null, null, null, 0L);
    final AtomicReference<OrderCreateIntent> intent = new AtomicReference<>(init);
    final AcquireOrderDTO order = context.getBean(AcquireOrderConverter.class).toDTO(
            order(AcquireOrderState.INIT, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10)), NOW);
    final DirectPaymentRecoveryService service = new DirectPaymentRecoveryService(actor::get, store, codec, orders, payments,
            context.getBean(DirectPaymentConverter.class), new OrderCreationTransactions(new UnitTransactions()), validation.getValidator());

    @BeforeEach void setup() {
        when(store.findIntent("tenant", "merchant", "M001")).thenAnswer(call -> Optional.ofNullable(intent.get()));
        when(codec.directPayDigest("tenant", OWNER, command)).thenReturn("a".repeat(64));
        when(codec.decodeDirectPay(any())).thenReturn(command);
        when(orders.resumeDirectPay(command)).thenReturn(order);
        when(payments.recover(any())).thenReturn(new PaymentRecoveryDTO("order", "action", PaymentRecoveryState.NOT_OBSERVED, null, null, null));
    }
    @AfterEach void close() { context.close(); validation.close(); }

    @Test void aMissingOriginalIntentDoesNotCreateAnOrderOrPayment() {
        intent.set(null);
        var result = service.recover(command);
        assertEquals(OrderCreationRecoveryState.NOT_OBSERVED, result.creationState());
        assertNull(result.orderId()); assertNull(result.order()); assertNull(result.payment());
        verifyNoInteractions(orders, payments, codec);
        verify(store, never()).insertIntent(anyString(), any());
    }

    @Test void anExistingA0ResumesOnlyItsReservedOrderAndReportsMissingA2WithoutStartingIt() {
        var result = service.recover(command);
        assertEquals(OrderCreationRecoveryState.COMPLETED, result.creationState());
        assertEquals("order", result.orderId());
        assertEquals(PaymentRecoveryState.NOT_OBSERVED, result.payment().state());
        assertNull(result.payment().accepted());
        verify(orders).resumeDirectPay(command);
        verify(orders, never()).createDirectPay(any());
        verify(orders, never()).create(any());
        verify(payments).recover(new PaymentRecoverCommand("order", "action", 4000L, command.choice()));
    }

    @Test void stableCreationRejectionAndExpiryHaveNoPaymentResult() {
        intent.set(init.finish("REJECTED", "admission", null, CONTRACT_NOT_ADMITTING.getCode()));
        var rejected = service.recover(command);
        assertEquals(OrderCreationRecoveryState.REJECTED, rejected.creationState());
        assertEquals(CONTRACT_NOT_ADMITTING.getCode(), rejected.resultCode());
        assertNull(rejected.order()); assertNull(rejected.payment());
        intent.set(init.finish("EXPIRED", "admission", NOW, ADMISSION_EXPIRED.getCode()));
        assertEquals(OrderCreationRecoveryState.EXPIRED, service.recover(command).creationState());
        verifyNoInteractions(orders, payments);
    }

    @Test void aRejectionCommittedDuringResumeIsReturnedAsItsOriginalCreationOutcome() {
        when(orders.resumeDirectPay(command)).thenAnswer(call -> {
            intent.set(init.finish("REJECTED", "admission", null, CONTRACT_NOT_ADMITTING.getCode()));
            throw new CommonException(CONTRACT_NOT_ADMITTING);
        });
        assertEquals(OrderCreationRecoveryState.REJECTED, service.recover(command).creationState());
        verifyNoInteractions(payments);
    }

    @Test void ownerEntryAndChangedMetadataAreRejectedBeforeResuming() {
        actor.set(new AcquireActor("tenant", "b".repeat(64), null, actor.get().permissions()));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.recover(command)).getResultCode());
        actor.set(new AcquireActor("tenant", OWNER, null, actor.get().permissions()));
        intent.set(new OrderCreateIntent("intent", "order", "merchant", "M001", "a".repeat(64), OWNER, "ORDER", null, "metadata", "INIT", null, null, null, 0L));
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service.recover(command)).getResultCode());
        intent.set(init);
        var changed = new DirectPayRecoverCommand("merchant", "M001", "scope", 10000, "CNY", "subject", null,
                "other-action", 4000, command.choice(), command.interactionKind());
        when(codec.directPayDigest("tenant", OWNER, changed)).thenReturn("a".repeat(64));
        assertSame(IDEMPOTENCY_CONFLICT, assertThrows(CommonException.class, () -> service.recover(changed)).getResultCode());
        verifyNoInteractions(orders, payments);
    }

    @Test void missingRecoveryPermissionAndUnknownFailureNeverFabricateSuccess() {
        actor.set(new AcquireActor("tenant", OWNER, null, Set.of("pay:acquire:create", "pay:acquire:read", "pay:acquire:payment:recover")));
        assertSame(ACTION_FORBIDDEN, assertThrows(CommonException.class, () -> service.recover(command)).getResultCode());
        actor.set(new AcquireActor("tenant", OWNER, null,
                Set.of("pay:acquire:direct-pay:recover", "pay:acquire:create", "pay:acquire:read", "pay:acquire:payment:recover")));
        var failure = new CommonException(DEPENDENCY_UNAVAILABLE);
        when(orders.resumeDirectPay(command)).thenThrow(failure);
        assertSame(failure, assertThrows(CommonException.class, () -> service.recover(command)));
        verifyNoInteractions(payments);
    }
}
