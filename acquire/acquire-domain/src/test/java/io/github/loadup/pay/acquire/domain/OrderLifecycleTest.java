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

import io.github.loadup.commons.money.Money;
import io.github.loadup.pay.acquire.domain.model.AcquireOrder;
import io.github.loadup.pay.acquire.domain.model.AcquireOrderState;
import io.github.loadup.pay.acquire.domain.model.CheckoutSession;
import io.github.loadup.pay.acquire.domain.model.OrderPaymentPolicy;
import io.github.loadup.pay.acquire.domain.model.ParentCloseEvidence;
import io.github.loadup.pay.acquire.domain.model.PaymentIntent;
import io.github.loadup.pay.acquire.domain.model.UnpaidOrderAction;
import io.github.loadup.commons.error.DomainViolation;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderLifecycleTest {

    private static final LocalDateTime DUE = LocalDateTime.of(2026, 10, 21, 12, 0);

    private AcquireOrder order(UnpaidOrderAction action) {
        return AcquireOrder.init(
                "order",
                "merchant",
                Money.ofMinor(1000, "CNY"),
                DUE,
                action,
                new OrderPaymentPolicy(1, 1000, null, true));
    }

    @Test
    void zeroHeldAmountDoesNotReplaceTheParentFenceAcknowledgement() {
        var closing = order(UnpaidOrderAction.CLOSE).requestClose();
        assertThrows(DomainViolation.class, () -> closing.finishClose(new ParentCloseEvidence("other", 1, true, true)));
        assertThrows(DomainViolation.class, () -> closing.finishClose(new ParentCloseEvidence("order", 2, true, true)));
        var evidence = new ParentCloseEvidence("order", closing.closeEpoch(), true, true);
        var closed = closing.finishClose(evidence);
        assertEquals(AcquireOrderState.CLOSED, closed.state());
        assertSame(closed, closed.finishClose(evidence));
        assertThrows(DomainViolation.class, closed::requestClose);
    }

    @Test
    void extensionsShareOneOriginalCumulativeAllowance() {
        var order = order(UnpaidOrderAction.CLOSE);
        var once = order.extend(DUE.plusSeconds(30), DUE, true, 60, DUE.minusSeconds(1));
        var twice = once.extend(DUE.plusSeconds(60), DUE, true, 60, DUE.plusSeconds(1));
        assertThrows(DomainViolation.class, () -> twice.extend(DUE.plusSeconds(90), DUE, true, 60, DUE.plusSeconds(2)));
        assertThrows(
                DomainViolation.class, () -> order.extend(DUE.plusSeconds(30), DUE, false, 0, DUE.minusSeconds(1)));
        assertEquals(order.amount(), twice.amount());
    }

    @Test
    void keepPendingDeadlineAndCheckoutExpiryAreSeparatePolicies() {
        var order = order(UnpaidOrderAction.KEEP_PENDING).checkDue(DUE);
        assertEquals(AcquireOrderState.PENDING, order.state());
        assertTrue(order.canAcceptNewPayment(DUE.plusDays(10)));
        var session = CheckoutSession.init("session", "order", DUE).activate(DUE.minusSeconds(1));
        assertFalse(session.permits("order", DUE));
        var expired = session.expire(DUE);
        assertThrows(DomainViolation.class, () -> expired.activate(DUE.minusSeconds(1)));
        assertEquals(
                AcquireOrderState.CLOSING,
                order(UnpaidOrderAction.CLOSE).checkDue(DUE).state());
    }

    @Test
    void actionReplayChecksOriginalDigestEvenWhenOrderIsNoLongerPayable() {
        var intent = new PaymentIntent("allocation", "payment", "scope", "key", "digest", Money.ofMinor(1000, "CNY"));
        var change = order(UnpaidOrderAction.CLOSE).reservePayment(intent, DUE.minusSeconds(1));
        assertDoesNotThrow(() -> change.allocation().requireSameAction("order", intent));
        assertThrows(
                DomainViolation.class,
                () -> change.allocation()
                        .requireSameAction(
                                "order",
                                new PaymentIntent(
                                        "unused", "unused", "scope", "key", "changed", Money.ofMinor(1000, "CNY"))));
    }
}
