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
import io.github.loadup.pay.payment.domain.gateway.*;
import io.github.loadup.pay.payment.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static io.github.loadup.pay.payment.client.error.PaymentResultCodeEnum.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefundExecutionLocksTest {
    final RefundGateway refunds = mock(RefundGateway.class);
    final PaymentAcceptanceGateway payments = mock(PaymentAcceptanceGateway.class);
    final RefundExecutionLocks locks = new RefundExecutionLocks(refunds, mock(RefundResultGateway.class), payments,
            mock(PaymentResultGateway.class), mock(PaymentAttemptGateway.class));
    final StoredRefundRequest request = RefundResultFixtures.initial();
    final PaymentAcceptanceTransactions transactions = new PaymentAcceptanceTransactions(new PaymentAcceptanceServiceTest.UnitTransactions());

    @BeforeEach
    void setup() {
        when(refunds.reference("tenant", "request")).thenReturn(java.util.Optional.of(
                new RefundRequestReference("request", "order", "merchant", request.requestDigest())));
        when(payments.lockParent("tenant", "order")).thenReturn(java.util.Optional.of(AttemptFixtures.gate(true)));
        when(refunds.lock("tenant", "request")).thenReturn(request);
    }
    StoredRefundRequest lock() { return transactions.writeLocal("tenant", () -> locks.request("tenant", "request")); }

    @Test
    void requestLockRetainsTheOriginalReferenceParentAndRequestOrder() {
        assertSame(request, lock());
        var sequence = inOrder(refunds, payments);
        sequence.verify(refunds).reference("tenant", "request");
        sequence.verify(payments).lockParent("tenant", "order");
        sequence.verify(refunds).lock("tenant", "request");
    }

    @Test
    void aReferenceCannotSelectAnotherRequestOrOriginalDigest() {
        when(refunds.reference("tenant", "request")).thenReturn(java.util.Optional.of(
                new RefundRequestReference("another-request", "order", "merchant", request.requestDigest())));
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, this::lock).getResultCode());
        when(refunds.reference("tenant", "request")).thenReturn(java.util.Optional.of(
                new RefundRequestReference("request", "order", "merchant", "a".repeat(64))));
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, this::lock).getResultCode());
    }

    @Test
    void theLockedRequestCannotBelongToAnotherOrderThanTheLockedParent() {
        var original = request.request();
        var changed = new RefundRequest(original.id(), "another-order", original.payeeMerchantId(), original.merchantRefundNo(),
                original.amount(), original.planVersion(), original.children(), original.state(), original.version());
        when(refunds.lock("tenant", "request")).thenReturn(new StoredRefundRequest(changed, request.reasonCode(), request.requestDigest(),
                request.callerSubjectHash(), request.admissionId(), request.termsPayload(), request.termsHash(), request.plannedAt(), request.allocations()));
        assertSame(SNAPSHOT_INVALID, assertThrows(CommonException.class, this::lock).getResultCode());
    }
}
