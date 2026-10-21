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
package io.github.loadup.pay.acquire.domain;

import io.github.loadup.pay.acquire.domain.model.*;
import io.github.loadup.pay.common.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AcquireOrderTest {
    private static final LocalDateTime DUE = LocalDateTime.of(2026, 10, 21, 12, 0);

    private AcquireOrder order(UnpaidOrderAction action) {
        return AcquireOrder.init(
                "order", "merchant", Money.cny(10000), DUE, action, new OrderPaymentPolicy(1, 10000, null, false));
    }

    private OrderPaymentChange pay(AcquireOrder order, String id, long minor) {
        var held = order.reservePayment(
                new PaymentIntent("a" + id, id, "merchant", id, "digest", Money.cny(minor)), DUE.minusMinutes(1));
        return held.order()
                .applyPaymentOutcome(
                        held.allocation(),
                        new VerifiedPaymentOutcome(
                                "order",
                                id,
                                Money.cny(minor),
                                VerifiedPaymentOutcome.Kind.SUCCEEDED,
                                1,
                                "evidence",
                                false));
    }

    @Test
    void becomesPaidOnlyAfterExactGrossTotalAndDeduplicatesSuccess() {
        var first = pay(order(UnpaidOrderAction.CLOSE), "p1", 3000);
        assertEquals(AcquireOrderState.PENDING, first.order().state());
        var second = pay(first.order(), "p2", 2000);
        assertEquals(5000, second.order().paidAmountMinor());
        var last = pay(second.order(), "p3", 5000);
        assertEquals(AcquireOrderState.PAID, last.order().state());
        var replay = last.order()
                .applyPaymentOutcome(
                        last.allocation(),
                        new VerifiedPaymentOutcome(
                                "order",
                                "p3",
                                Money.cny(5000),
                                VerifiedPaymentOutcome.Kind.SUCCEEDED,
                                2,
                                "evidence",
                                false));
        assertSame(last.order(), replay.order());
        assertEquals(0, replay.order().availableToPayMinor(DUE));
    }

    @Test
    void unknownKeepsAllocationAndCloseRequiresConvergence() {
        var held = order(UnpaidOrderAction.CLOSE)
                .reservePayment(
                        new PaymentIntent("a1", "p1", "merchant", "action", "digest", Money.cny(7000)),
                        DUE.minusMinutes(1));
        var unknown = held.order()
                .applyPaymentOutcome(
                        held.allocation(),
                        new VerifiedPaymentOutcome(
                                "order", "p1", Money.cny(7000), VerifiedPaymentOutcome.Kind.UNKNOWN, 1, null, false));
        assertEquals(7000, unknown.order().paymentReservedMinor());
        assertThrows(
                DomainViolation.class,
                () -> unknown.order()
                        .reservePayment(
                                new PaymentIntent("a2", "p2", "merchant", "next", "digest", Money.cny(4000)),
                                DUE.minusMinutes(1)));
        var closing = unknown.order().requestClose();
        assertThrows(
                DomainViolation.class,
                () -> closing.finishClose(new ParentCloseEvidence("order", closing.closeEpoch(), true, true)));
    }

    @Test
    void dueTimeBlocksClosePolicyButKeepPendingCanContinue() {
        assertFalse(order(UnpaidOrderAction.CLOSE).canAcceptNewPayment(DUE));
        assertTrue(order(UnpaidOrderAction.KEEP_PENDING).canAcceptNewPayment(DUE.plusDays(10)));
    }
}
