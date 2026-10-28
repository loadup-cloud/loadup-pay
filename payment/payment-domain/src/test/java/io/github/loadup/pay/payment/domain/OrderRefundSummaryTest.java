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
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.payment.domain.model.OrderRefundSummary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderRefundSummaryTest {
    @Test
    void independentRequestsShareOneMonotonicMonetaryVersionWithoutChangingTheOrderTotal() {
        var initial = OrderRefundSummary.init("order", "merchant", DomainChecks.cnyAmount(10000, "CNY"));
        var held = initial.reserve(6000).reserve(1000);
        var succeeded = held.settle(2000, true);
        var failed = succeeded.settle(1000, false);
        assertEquals(10000, failed.orderAmount().getCent());
        assertEquals(2000, failed.refundedMinor());
        assertEquals(4000, failed.reservedMinor());
        assertEquals(4, failed.version());
        assertEquals(0, initial.version());
        assertEquals(0, initial.reservedMinor());
    }

    @Test
    void totalAndOriginalHoldsBoundEveryReservationAndSettlement() {
        var held = OrderRefundSummary.init("order", "merchant", DomainChecks.cnyAmount(1000, "CNY")).reserve(800);
        assertThrows(DomainViolation.class, () -> held.reserve(201));
        assertThrows(DomainViolation.class, () -> held.settle(801, true));
        assertThrows(DomainViolation.class, () -> held.settle(0, false));
        var refunded = held.settle(800, true);
        assertThrows(DomainViolation.class, () -> refunded.reserve(201));
        assertEquals(800, refunded.refundedMinor());
    }
}
