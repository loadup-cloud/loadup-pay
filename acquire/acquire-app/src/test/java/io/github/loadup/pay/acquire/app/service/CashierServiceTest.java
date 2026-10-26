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
import io.github.loadup.commons.request.query.IdQuery;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.acquire.app.converter.CashierPaymentConverter;
import io.github.loadup.pay.acquire.app.port.CashierInteractionProvider;
import io.github.loadup.pay.acquire.app.security.CashierActor;
import io.github.loadup.pay.acquire.client.command.CashierPaymentStartCommand;
import io.github.loadup.pay.acquire.client.command.CashierPaymentRecoverCommand;
import io.github.loadup.pay.acquire.client.enums.PaymentRecoveryState;
import io.github.loadup.pay.acquire.client.dto.*;
import io.github.loadup.pay.acquire.domain.gateway.*;
import io.github.loadup.pay.acquire.domain.model.*;
import io.github.loadup.pay.payment.client.dto.*;
import io.github.loadup.pay.payment.client.enums.PaymentState;
import io.github.loadup.pay.channel.api.model.H5SceneType;
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

class CashierServiceTest {
    final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(CashierPaymentConverter.class.getPackageName());
    final jakarta.validation.ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final CashierActor actor = new CashierActor("tenant", "session", "order", NOW.plusMinutes(5));
    final CashierAccess access = mock(CashierAccess.class);
    final CashierChoices choices = mock(CashierChoices.class);
    final CashierInteractionProvider inputs = mock(CashierInteractionProvider.class);
    final OrderCreationGateway orders = mock(OrderCreationGateway.class);
    final PaymentOutcomeGateway allocations = mock(PaymentOutcomeGateway.class);
    final PaymentInitiationOperations initiation = mock(PaymentInitiationOperations.class);
    final PaymentOutcomeOperations outcomes = mock(PaymentOutcomeOperations.class);
    final CashierSummaryService summaries = mock(CashierSummaryService.class);
    final CashierService service = new CashierService(() -> actor, access, choices, inputs, orders, allocations, initiation, outcomes, summaries,
            context.getBean(CashierPaymentConverter.class), new OrderCreationTransactions(new UnitTransactions()), validation.getValidator(), clock(NOW));
    final StoredAcquireOrder stored = order(AcquireOrderState.PENDING, UnpaidOrderAction.CLOSE, NOW.plusMinutes(10));
    final PaymentChoiceDTO choice = new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.H5);
    final CashierOrderDTO summary = new CashierOrderDTO("order", null, "subject", "CNY", 10000, 2000, 0, 8000, 1, 100000, true, null,
            io.github.loadup.pay.acquire.client.enums.AcquireOrderState.PENDING, null, Set.of(choice), null);
    @BeforeEach void setup() {
        when(orders.findOrder("tenant", "order")).thenReturn(Optional.of(stored));
        when(choices.choices(eq(stored), any())).thenReturn(Set.of(choice));
        when(inputs.prepare(actor, choice)).thenReturn(new H5SceneInteractionDTO("203.0.113.10", H5SceneType.WAP));
        when(summaries.summary(new IdQuery("order"))).thenReturn(summary);
        when(initiation.start(any(), any())).thenReturn(new PaymentStartDTO("order", "payment", "allocation", PaymentState.INIT, null, 0));
    }
    @AfterEach void close() { validation.close(); context.close(); }
    StoredPaymentAllocation allocation(String scope) {
        return new StoredPaymentAllocation(new OrderPaymentAllocation("allocation", "order", "payment", scope, "action", "a".repeat(64),
                DomainChecks.cnyAmount(1000, "CNY"), AllocationState.HELD, 0), OWNER, "{}", "b".repeat(64), 0);
    }
    @Test void startUsesTheOriginalOwnerAndSessionScopedGuardAndReturnsNoMerchantCredentials() {
        var result = service.startPayment(new CashierPaymentStartCommand("order", "action", 1000, choice));
        assertEquals("payment", result.paymentId());
        assertTrue(result.accepted());
        assertEquals(summary, result.order());
        var principal = org.mockito.ArgumentCaptor.forClass(PaymentInitiationPrincipal.class);
        var command = org.mockito.ArgumentCaptor.forClass(io.github.loadup.pay.acquire.client.command.PaymentStartCommand.class);
        verify(initiation).start(principal.capture(), command.capture());
        assertEquals("checkout/session", principal.getValue().actionScope());
        assertEquals(OWNER, principal.getValue().scopeHash());
        principal.getValue().authorize(stored, true);
        verify(access).authorize(actor, OWNER, stored, true);
        assertInstanceOf(H5SceneInteractionDTO.class, command.getValue().interaction());
        String json = JsonUtil.toJson(result);
        assertFalse(json.contains("terminalEvidenceId"));
        assertFalse(json.contains("allocation"));
        assertFalse(json.contains("203.0.113.10"));
    }
    @Test void anotherSessionOrMerchantPaymentCannotBeQueriedWithThisCapability() {
        when(allocations.findPayment("tenant", "order", "payment")).thenReturn(Optional.of(allocation("checkout/other-session")));
        assertSame(SESSION_UNAUTHORIZED, assertThrows(CommonException.class, () -> service.paymentProgress(new IdQuery("payment"))).getResultCode());
        when(allocations.findPayment("tenant", "order", "payment")).thenReturn(Optional.of(allocation("merchant/" + OWNER)));
        assertThrows(CommonException.class, () -> service.paymentProgress(new IdQuery("payment")));
        verifyNoInteractions(outcomes);
    }
    @Test void provenNonAcceptanceRemainsAbsentRatherThanInventingAPaymentRecord() {
        when(allocations.findPayment("tenant", "order", "payment")).thenReturn(Optional.of(allocation("checkout/session")));
        when(outcomes.refresh(any(), any())).thenReturn(new PaymentProgressDTO(null, null));
        var result = service.paymentProgress(new IdQuery("payment"));
        assertEquals("payment", result.paymentId());
        assertNull(result.state()); assertNull(result.version()); assertNull(result.action());
        assertFalse(result.accepted());
        assertEquals(summary, result.order());
    }
    @Test void recoveryUsesOnlyTheOriginalSessionIntentAndConvergesBeforeReturningPayment() {
        when(initiation.recover(any(), eq("order"), eq("action"), eq(1000L), eq(choice)))
                .thenReturn(new PaymentIntentRecovery(PaymentIntentRecovery.State.RESOLVED, "payment", null));
        when(outcomes.refresh(any(), any())).thenReturn(new PaymentProgressDTO(null, null));
        var result = service.recoverPayment(new CashierPaymentRecoverCommand("order", "action", 1000L, choice));
        assertEquals(PaymentRecoveryState.RESOLVED, result.state());
        assertEquals("action", result.actionKey());
        assertEquals("payment", result.payment().paymentId());
        assertFalse(result.payment().accepted());
        var principal = org.mockito.ArgumentCaptor.forClass(PaymentInitiationPrincipal.class);
        verify(initiation).recover(principal.capture(), eq("order"), eq("action"), eq(1000L), eq(choice));
        assertEquals("checkout/session", principal.getValue().actionScope());
        assertEquals(OWNER, principal.getValue().scopeHash());
        principal.getValue().authorize(stored, false);
        verify(access).authorize(actor, OWNER, stored, false);
        verify(access).read(actor, stored, "PAYMENT_PROGRESS");
        verifyNoInteractions(inputs, choices);
        verify(initiation, never()).start(any(), any());
    }
    @Test void expiryProofMustConvergeBeforeTheCashierCanReportNonAcceptance() {
        var proof = new PaymentCloseDTO("payment", "order", 1L, true, null, "expiry-proof");
        when(initiation.recover(any(), eq("order"), eq("action"), eq(1000L), eq(choice)))
                .thenReturn(new PaymentIntentRecovery(PaymentIntentRecovery.State.RESOLVED, "payment", proof));
        when(outcomes.applyInputExpiry(any(), eq("order"), eq("payment"), eq(proof))).thenReturn(new PaymentProgressDTO(null, null));
        var result = service.recoverPayment(new CashierPaymentRecoverCommand("order", "action", 1000L, choice));
        assertFalse(result.payment().accepted());
        verify(outcomes).applyInputExpiry(new OrderPaymentScope("tenant", OWNER), "order", "payment", proof);
        verify(outcomes, never()).refresh(any(), any());
        verifyNoInteractions(inputs);
    }
    @Test void unresolvedRecoveryCannotInventAPaymentOrCallTheResultService() {
        for (var state : java.util.List.of(PaymentIntentRecovery.State.NOT_OBSERVED, PaymentIntentRecovery.State.INPUT_REQUIRED)) {
            when(initiation.recover(any(), anyString(), anyString(), anyLong(), any())).thenReturn(new PaymentIntentRecovery(state, null, null));
            var result = service.recoverPayment(new CashierPaymentRecoverCommand("order", "action", 1000L, choice));
            assertNull(result.payment());
            assertEquals(state.name(), result.state().name());
        }
        verifyNoInteractions(inputs, choices, outcomes);
        assertSame(SESSION_UNAUTHORIZED, assertThrows(CommonException.class, () -> service.recoverPayment(
                new CashierPaymentRecoverCommand("another-order", "action", 1000L, choice))).getResultCode());
    }
    @Test void differentOrderAndUnavailableInteractionFailBeforeMoneyReservation() {
        assertThrows(CommonException.class, () -> service.startPayment(new CashierPaymentStartCommand("other", "action", 1000, choice)));
        when(inputs.prepare(actor, choice)).thenThrow(new CommonException(UNSUPPORTED_CAPABILITY));
        assertThrows(CommonException.class, () -> service.startPayment(new CashierPaymentStartCommand("order", "action", 1000, choice)));
        verifyNoInteractions(initiation);
    }
}
