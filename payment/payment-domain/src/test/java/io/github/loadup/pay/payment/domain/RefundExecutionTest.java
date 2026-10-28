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

import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.money.Money;
import io.github.loadup.pay.channel.api.model.ChannelRefundOutcome;
import io.github.loadup.pay.payment.domain.model.PaymentRefundCapacity;
import io.github.loadup.pay.payment.domain.model.RefundOrder;
import io.github.loadup.pay.payment.domain.model.RefundRequest;
import io.github.loadup.pay.payment.domain.model.RefundRequestState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundExecutionTest {

    private RefundOrder child(String refund, String payment, long minor) {
        return RefundOrder.init(refund, "request", payment, Money.ofMinor(minor, "CNY"), "channel-" + refund);
    }

    private ChannelRefundOutcome outcome(String id, long amount, ChannelRefundOutcome.Kind kind) {
        return new ChannelRefundOutcome(id, kind, Money.ofMinor(amount, "CNY"), "evidence-" + id);
    }

    @Test
    void unknownBlocksNextChildAndConfirmedFailureContinuesOnlyTheFixedPlan() {
        var first = child("r1", "p1", 300);
        var second = child("r2", "p2", 700);
        var request = RefundRequest.init(
                "request", "order", "merchant", "refund-no", Money.ofMinor(1000, "CNY"), List.of(first, second));
        var unknown = first.recordOutcome(outcome("r1", 300, ChannelRefundOutcome.Kind.UNKNOWN));
        var waiting = request.apply(unknown);
        assertTrue(waiting.nextExecutable().isEmpty());
        assertThrows(
                DomainViolation.class,
                () -> waiting.apply(second.recordOutcome(outcome("r2", 700, ChannelRefundOutcome.Kind.SUCCEEDED))));
        var failed = unknown.recordOutcome(outcome("r1", 300, ChannelRefundOutcome.Kind.FAILED_WITH_NO_REFUND));
        var continuing = waiting.apply(failed);
        assertEquals("r2", continuing.nextExecutable().orElseThrow().id());
        var completed = continuing.apply(second.recordOutcome(outcome("r2", 700, ChannelRefundOutcome.Kind.SUCCEEDED)));
        assertEquals(RefundRequestState.PARTIAL_SUCCEEDED, completed.state());
        assertEquals(700, completed.succeededMinor());
        assertEquals(300, completed.failedMinor());
    }

    @Test
    void successConsumesHeldCapacityOnceAndFailureReleasesItWithoutChangingGrossPayment() {
        var capacity = new PaymentRefundCapacity(
                "p1",
                "order",
                "merchant",
                LocalDateTime.of(2026, 10, 21, 10, 0),
                Money.ofMinor(1000, "CNY"),
                0,
                0,
                0)
                .reserve(300);
        var refund = child("r1", "p1", 300);
        var unknown = capacity.applyOutcome(refund, outcome("r1", 300, ChannelRefundOutcome.Kind.UNKNOWN));
        assertSame(capacity, unknown.capacity());
        var paid = capacity.applyOutcome(unknown.refund(), outcome("r1", 300, ChannelRefundOutcome.Kind.SUCCEEDED));
        assertEquals(300, paid.capacity().refundedMinor());
        assertEquals(0, paid.capacity().reservedMinor());
        assertEquals(1000, paid.capacity().succeededAmount().getCent());
        assertSame(
                paid.capacity(),
                paid.capacity()
                        .applyOutcome(paid.refund(), outcome("r1", 300, ChannelRefundOutcome.Kind.SUCCEEDED))
                        .capacity());
        var next = paid.capacity().reserve(200);
        var failed = next.applyOutcome(
                child("r2", "p1", 200), outcome("r2", 200, ChannelRefundOutcome.Kind.FAILED_WITH_NO_REFUND));
        assertEquals(300, failed.capacity().refundedMinor());
        assertEquals(700, failed.capacity().availableMinor());
        assertThrows(
                DomainViolation.class,
                () -> failed.refund().recordOutcome(outcome("r2", 200, ChannelRefundOutcome.Kind.SUCCEEDED)));
    }
}
