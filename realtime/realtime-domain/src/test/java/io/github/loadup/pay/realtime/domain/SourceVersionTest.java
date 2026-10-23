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
package io.github.loadup.pay.realtime.domain;

import io.github.loadup.pay.common.DomainViolation;
import io.github.loadup.pay.realtime.domain.model.SourceVersion;
import io.github.loadup.pay.realtime.domain.model.TradeRefundSummary;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceVersionTest {
    @Test
    void versionsOfDifferentProducersOrAggregatesAreNotComparable() {
        var original = new SourceVersion("acquire", "AcquireOrder", "order", 5);
        assertTrue(new SourceVersion("acquire", "AcquireOrder", "order", 6).newerThan(original));
        assertFalse(new SourceVersion("acquire", "AcquireOrder", "order", 4).newerThan(original));
        assertThrows(
                DomainViolation.class,
                () -> new SourceVersion("payment", "OrderRefundSummary", "order", 100).newerThan(original));
    }

    @Test
    void monetaryRefundSummaryKeepsOriginalOrderIdentityAndIndependentSourceVersion() {
        var summary = new TradeRefundSummary(
                "order",
                "merchant",
                "CNY",
                40,
                0,
                new SourceVersion("payment", "OrderRefundSummary", "order", 2),
                LocalDateTime.of(2026, 10, 21, 10, 0));
        assertEquals(40, summary.refundedMinor());
        assertEquals("merchant", summary.payeeMerchantId());
        assertThrows(
                DomainViolation.class,
                () -> new TradeRefundSummary(
                        "other-order", "merchant", "CNY", 40, 0, summary.source(), summary.snapshotAt()));
    }
}
