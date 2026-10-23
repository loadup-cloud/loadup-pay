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

import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.realtime.domain.model.ProjectionFacts;
import io.github.loadup.pay.realtime.domain.model.SourceVersion;
import io.github.loadup.pay.realtime.domain.model.TradePaymentProjection;
import io.github.loadup.pay.realtime.domain.model.TradeRefundSummary;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectionFactsTest {
    final LocalDateTime at = LocalDateTime.of(2026, 10, 23, 12, 0);

    TradePaymentProjection payment(String merchant, String state, long version) {
        return new TradePaymentProjection("payment", "order", merchant, DomainChecks.cnyAmount(1000, "CNY"), state,
                Channel.WECHAT, AccessMode.DIRECT, at, state.equals("SUCCEEDED") ? at.plusSeconds(1) : null,
                new SourceVersion("payment", "PaymentOrder", "payment", version), at.plusMinutes(1));
    }

    @Test
    void delayedIntermediateSnapshotCanBeIgnoredAfterSuccess() {
        assertTrue(ProjectionFacts.compatible(payment("merchant", "SUCCEEDED", 3), payment("merchant", "PROCESSING", 1)));
    }

    @Test
    void higherVersionCannotReopenATerminalPayment() {
        assertFalse(ProjectionFacts.compatible(payment("merchant", "SUCCEEDED", 3), payment("merchant", "PROCESSING", 4)));
        assertFalse(ProjectionFacts.compatible(payment("merchant", "FAILED", 3), payment("merchant", "SUCCEEDED", 4)));
    }

    @Test
    void merchantOwnershipIsImmutableEvenForAnOlderVersion() {
        assertFalse(ProjectionFacts.compatible(payment("merchant", "SUCCEEDED", 3), payment("other-merchant", "PROCESSING", 1)));
    }

    @Test
    void higherVersionCannotChangeTheOriginalPaymentAmount() {
        var original = payment("merchant", "PROCESSING", 1);
        var changed = new TradePaymentProjection("payment", "order", "merchant", DomainChecks.cnyAmount(999, "CNY"), "PROCESSING",
                Channel.WECHAT, AccessMode.DIRECT, at, null, new SourceVersion("payment", "PaymentOrder", "payment", 2), at);
        assertFalse(ProjectionFacts.compatible(original, changed));
    }

    @Test
    void higherMonetarySummaryCannotDecreaseTheSuccessfulRefundTotal() {
        var old = new TradeRefundSummary("order", "merchant", "CNY", 100, 0,
                new SourceVersion("payment", "OrderRefundSummary", "order", 2), at);
        var changed = new TradeRefundSummary("order", "merchant", "CNY", 0, 0,
                new SourceVersion("payment", "OrderRefundSummary", "order", 3), at);
        assertFalse(ProjectionFacts.compatible(old, changed));
    }
}
