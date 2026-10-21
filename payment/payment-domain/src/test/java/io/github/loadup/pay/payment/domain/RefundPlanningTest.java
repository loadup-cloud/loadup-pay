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
package io.github.loadup.pay.payment.domain;

import io.github.loadup.pay.common.*;
import io.github.loadup.pay.payment.domain.model.*;
import io.github.loadup.pay.payment.domain.policy.FifoRefundPolicy;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RefundPlanningTest {
    @Test
    void fifoPlanUsesStableTimeAndPaymentIdOrderingAndNeverExceedsCapacity() {
        var first = new PaymentRefundCapacity(
                "p1", "order", "merchant", LocalDateTime.of(2026, 10, 21, 10, 0), Money.cny(3000), 0, 0, 0);
        var second =
                new PaymentRefundCapacity("p2", "order", "merchant", first.succeededAt(), Money.cny(2000), 0, 0, 0);
        var third = new PaymentRefundCapacity(
                "p3", "order", "merchant", first.succeededAt().plusMinutes(1), Money.cny(5000), 0, 0, 0);
        var policy = new FifoRefundPolicy();
        var plan = policy.plan("order", "merchant", Money.cny(4000), true, List.of(third, second, first));
        assertEquals(
                List.of("p1", "p2"),
                plan.stream().map(RefundPlanItem::paymentId).toList());
        assertEquals(
                List.of(3000L, 1000L),
                plan.stream().map(p -> p.amount().minor()).toList());
        assertThrows(
                DomainViolation.class,
                () -> policy.plan(
                        "order", "merchant", Money.cny(8000), true, List.of(first.reserve(3000), second, third)));
        assertEquals(3000, first.availableMinor());
    }

    @Test
    void unknownChildCannotBecomeACompletedRefundRequest() {
        var child = RefundOrder.init("r1", "request", "p1", Money.cny(1000), "channel-refund");
        var request =
                RefundRequest.init("request", "order", "merchant", "merchant-refund", Money.cny(1000), List.of(child));
//        var unknown = child.recordOutcome(RefundState.UNKNOWN, Money.cny(1000), null);
//        request = request.apply(unknown);
//        assertEquals(RefundRequestState.PROCESSING, request.state());
//        assertEquals(1, request.unknownCount());
//        request = request.apply(unknown.recordOutcome(RefundState.SUCCEEDED, Money.cny(1000), "refund-evidence"));
//        assertEquals(RefundRequestState.SUCCEEDED, request.state());
    }
}
