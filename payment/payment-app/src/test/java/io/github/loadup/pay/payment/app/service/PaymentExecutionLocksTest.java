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
import io.github.loadup.pay.payment.app.port.PaymentAcceptanceCodec;
import io.github.loadup.pay.payment.domain.gateway.PaymentAcceptanceGateway;
import io.github.loadup.pay.payment.domain.gateway.PaymentControlGateway;
import io.github.loadup.pay.payment.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentExecutionLocksTest {
    final PaymentAcceptanceGateway gates = mock(PaymentAcceptanceGateway.class);
    final PaymentControlGateway payments = mock(PaymentControlGateway.class);
    final PaymentAcceptanceCodec codec = mock(PaymentAcceptanceCodec.class);
    final PaymentExecutionLocks locks = new PaymentExecutionLocks(gates, payments, codec);
    final PaymentAcceptanceTransactions transactions = new PaymentAcceptanceTransactions(new PaymentAcceptanceServiceTest.UnitTransactions());
    final StoredPaymentAcceptance accepted = AttemptFixtures.accepted(true);

    @BeforeEach void setup() {
        when(gates.find("tenant", "payment")).thenReturn(Optional.of(accepted));
        when(gates.lockParent("tenant", "order")).thenReturn(Optional.of(AttemptFixtures.gate(true)));
        when(gates.lockPayment("tenant", "payment")).thenReturn(Optional.of(AttemptFixtures.gate(false)));
        when(payments.lockPayment("tenant", "payment")).thenReturn(Optional.of(accepted));
        when(codec.decode(any())).thenReturn(AttemptFixtures.command());
    }

    @Test void originalLockOrderReturnsAValidatedViewWithoutCreatingAnyExecutionRights() {
        transactions.writeLocal("tenant", () -> {
            var locked = locks.lock("tenant", "payment", "acquire");
            assertSame(accepted, locked.payment());
            assertSame(accepted, locked.binding().accepted());
            var context = PaymentExecutionBindings.submittableContext(locked, AttemptFixtures.prepared());
            assertSame(locked.binding(), context.gates());
            assertEquals(0L, context.attempt().stored().attempt().executionEpoch());
            var ordered = inOrder(gates, payments, codec);
            ordered.verify(gates).find("tenant", "payment");
            ordered.verify(gates).lockParent("tenant", "order");
            ordered.verify(gates).lockPayment("tenant", "payment");
            ordered.verify(payments).lockPayment("tenant", "payment");
            ordered.verify(codec).decode(accepted);
            return true;
        });
    }

    @Test void missingOrReadonlyTransactionIsRejectedBeforeAnyGatewayOrCodecAccess() {
        assertEquals(INVALID_STATE, assertThrows(CommonException.class, () -> locks.lock("tenant", "payment", "acquire")).getResultCode());
        transactions.readSnapshot("tenant", () -> {
            assertEquals(INVALID_STATE, assertThrows(CommonException.class, () -> locks.lock("tenant", "payment", "acquire")).getResultCode());
            return true;
        });
        verifyNoInteractions(gates, payments, codec);
    }

    @Test void unexpectedOriginalCallerCannotAcquireAnyFinancialLocks() {
        var rejected = assertThrows(CommonException.class, () -> transactions.writeLocal("tenant", () -> locks.lock("tenant", "payment", "other-service")));
        assertEquals(ACTION_FORBIDDEN, rejected.getResultCode());
        verify(gates, never()).lockParent(anyString(), anyString());
        verify(gates, never()).lockPayment(anyString(), anyString());
        verifyNoInteractions(payments, codec);
    }

    @Test void incorrectGateMerchantOrACompleteViewForAnotherRequestedIdIsStillCorrupt() {
        var original = AttemptFixtures.gate(false);
        var wrongMerchant = new StoredPaymentGate(original.id(), "other-merchant", original.callerServiceId(), original.gate(), original.rowVersion());
        when(gates.lockPayment("tenant", "payment")).thenReturn(Optional.of(wrongMerchant));
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class,
                () -> transactions.writeLocal("tenant", () -> locks.lock("tenant", "payment", "acquire"))).getResultCode());
        var order = accepted.payment();
        var another = new PaymentOrder("other-payment", order.orderId(), order.allocationId(), order.payeeMerchantId(), order.amount(), order.limitOperationId(),
                order.initiatedAt(), order.state(), order.activeAttemptId(), order.successfulAttemptId(), order.version());
        var unrelated = new StoredPaymentAcceptance(another, accepted.admissionId(), accepted.requestDigest(), accepted.callerServiceId(), accepted.intentPayload(),
                accepted.payloadHash(), accepted.terminalEvidenceId());
        when(payments.lockPayment("tenant", "payment")).thenReturn(Optional.of(unrelated));
        when(gates.lockPayment("tenant", "payment")).thenReturn(Optional.of(new StoredPaymentGate(original.id(), original.payeeMerchantId(), original.callerServiceId(),
                PaymentExecutionGate.init(order.orderId(), "other-payment").open(), original.rowVersion())));
        assertEquals(SNAPSHOT_INVALID, assertThrows(CommonException.class,
                () -> transactions.writeLocal("tenant", () -> locks.lock("tenant", "payment", "acquire"))).getResultCode());
    }
}
