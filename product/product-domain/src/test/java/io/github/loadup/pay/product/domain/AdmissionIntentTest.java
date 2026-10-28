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
package io.github.loadup.pay.product.domain;

import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.product.domain.model.AdmissionIntent;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdmissionIntentTest {
    private AdmissionIntent intent(long amount, String scope, String caller) {
        return new AdmissionIntent(
                "order", "merchant", scope, "a".repeat(64), DomainChecks.cnyAmount(amount, "CNY"), null, caller);
    }

    @Test
    void unchangedDigestCannotHideChangedAmountOrProductScope() {
        var original = intent(100, "scope", "b".repeat(64));
        assertThrows(DomainViolation.class, () -> original.requireSame(intent(200, "scope", "b".repeat(64))));
        assertThrows(DomainViolation.class, () -> original.requireSame(intent(100, "other", "b".repeat(64))));
    }

    @Test
    void callerScopeAndRequestedDeadlineArePartOfTheFrozenIntent() {
        var original = intent(100, "scope", "b".repeat(64));
        assertThrows(DomainViolation.class, () -> original.requireSame(intent(100, "scope", "c".repeat(64))));
        var due = new AdmissionIntent(
                "order",
                "merchant",
                "scope",
                "a".repeat(64),
                DomainChecks.cnyAmount(100, "CNY"),
                LocalDateTime.of(2026, 10, 21, 11, 0),
                "b".repeat(64));
        assertThrows(DomainViolation.class, () -> original.requireSame(due));
    }

    @Test
    void requestedDeadlineMustBeFutureAndWithinTheOriginalMaximumWait() {
        var policy = new io.github.loadup.pay.product.domain.policy.OrderAdmissionPolicy();
        var now = LocalDateTime.of(2026, 10, 21, 10, 0);
        var future = new AdmissionIntent(
                "order",
                "merchant",
                "scope",
                "a".repeat(64),
                DomainChecks.cnyAmount(100, "CNY"),
                now.plusSeconds(600),
                "b".repeat(64));
        assertTrue(policy.acceptsRequestedDeadline(future, now, 600));
        assertFalse(policy.acceptsRequestedDeadline(future, now, 599));
        assertFalse(policy.acceptsRequestedDeadline(future, now.plusSeconds(600), 600));
    }

    @Test
    void unrepresentableDatabasePrecisionIsRejectedBeforePersistence() {
        assertThrows(
                DomainViolation.class,
                () -> new AdmissionIntent(
                        "order",
                        "merchant",
                        "scope",
                        "a".repeat(64),
                        DomainChecks.cnyAmount(100, "CNY"),
                        LocalDateTime.of(2026, 10, 21, 11, 0, 0, 1),
                        "b".repeat(64)));
    }
}
