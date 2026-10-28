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
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.pay.payment.app.security.PaymentActor;
import io.github.loadup.pay.payment.client.command.PaymentInputPrepareCommand;
import io.github.loadup.pay.payment.client.dto.PaymentChoiceDTO;
import io.github.loadup.pay.payment.client.dto.PaymentCodeInteractionDTO;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.model.GateState;
import io.github.loadup.pay.payment.domain.model.PaymentExecutionGate;
import io.github.loadup.pay.payment.domain.model.StoredPaymentGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.ACTION_FORBIDDEN;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.EXECUTION_FENCED;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.INVALID_STATE;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.SNAPSHOT_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentInputPublicationTest {
    final PaymentAcceptanceGateway payments = mock(PaymentAcceptanceGateway.class);
    final PaymentInputPublication publication = new PaymentInputPublication(payments, () -> "parent");
    final PaymentAcceptanceTransactions transactions = new PaymentAcceptanceTransactions(new PaymentAcceptanceServiceTest.UnitTransactions());
    final PaymentActor actor = new PaymentActor("tenant", "acquire", Set.of("pay:payment:input:prepare"));
    final PaymentInputPrepareCommand command = new PaymentInputPrepareCommand("payment", "order", "allocation", "merchant", 100L,
            "CNY", "input", new PaymentChoiceDTO(Channel.WECHAT, PaymentMethod.PAYMENT_CODE), new PaymentCodeInteractionDTO("synthetic-code"));

    @BeforeEach
    void setup() {
        when(payments.lockParent("tenant", "order")).thenReturn(Optional.empty());
        when(payments.lockPayment("tenant", "payment")).thenReturn(Optional.empty());
        when(payments.find("tenant", "payment")).thenReturn(Optional.empty());
    }

    @Test
    void firstPublicationCreatesOnlyAnOpenParentAndUsesTheAcceptanceLockOrder() {
        publish();
        var order = inOrder(payments);
        order.verify(payments).lockParent("tenant", "order");
        order.verify(payments).insertParent(eq("tenant"), argThat(value -> value.gate().paymentId() == null && value.gate().state() == GateState.OPEN));
        order.verify(payments).lockPayment("tenant", "payment");
        order.verify(payments).find("tenant", "payment");
        verify(payments, never()).insertPaymentGate(anyString(), any());
    }

    @Test
    void aFencedParentCanBeLockedForReplayButRejectsANewPublication() {
        var fenced = gate(null, true);
        when(payments.lockParent("tenant", "order")).thenReturn(Optional.of(fenced));
        assertEquals(fenced, transactions.writeLocal("tenant", () -> publication.lockParent(actor, command)));
        assertEquals(EXECUTION_FENCED, assertThrows(CommonException.class, this::publish).getResultCode());
        verify(payments, never()).lockPayment(anyString(), anyString());
        verify(payments, never()).insertParent(anyString(), any());
    }

    @Test
    void originalChildTombstoneStopsALateCiphertextFromBeingPublished() {
        when(payments.lockParent("tenant", "order")).thenReturn(Optional.of(gate(null, false)));
        when(payments.lockPayment("tenant", "payment")).thenReturn(Optional.of(gate("payment", true)));
        assertEquals(EXECUTION_FENCED, assertThrows(CommonException.class, this::publish).getResultCode());
        verify(payments, never()).find(anyString(), anyString());
        verify(payments, never()).insertPaymentGate(anyString(), any());
    }

    @Test
    void anAcceptedInputlessPaymentOrAnOrphanChildCannotReceiveANewInput() {
        when(payments.lockParent("tenant", "order")).thenReturn(Optional.of(gate(null, false)));
        when(payments.lockPayment("tenant", "payment")).thenReturn(Optional.of(gate("payment", false)));
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class, this::publish).getResultCode());
        when(payments.find("tenant", "payment")).thenReturn(Optional.of(AttemptFixtures.accepted(false)));
        assertEquals(INVALID_STATE, assertThrows(CommonException.class, this::publish).getResultCode());
    }

    @Test
    void noTransactionAndWrongOriginalOwnerFailWithoutOpeningTheGate() {
        assertEquals(INVALID_STATE, assertThrows(CommonException.class, () -> io.github.loadup.commons.util.TenantUtil.callWithTenant("tenant", () -> publication.lockParent(actor, command))).getResultCode());
        var other = new StoredPaymentGate("parent", "merchant", "other-service", PaymentExecutionGate.init("order", null).open(), 0L);
        when(payments.lockParent("tenant", "order")).thenReturn(Optional.of(other));
        assertEquals(ACTION_FORBIDDEN, assertThrows(CommonException.class, this::publish).getResultCode());
        verify(payments, never()).insertParent(anyString(), any());
    }

    private boolean publish() {
        return transactions.writeLocal("tenant", () -> {
            var parent = publication.lockParent(actor, command);
            publication.requireNew(actor, command, parent);
            return true;
        });
    }

    private StoredPaymentGate gate(String payment, boolean fenced) {
        var state = PaymentExecutionGate.init("order", payment).open();
        return new StoredPaymentGate(payment == null ? "parent" : "child", "merchant", "acquire", fenced ? state.fence(1L) : state, 0L);
    }
}
