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
package io.github.loadup.pay.limitcenter.domain;

import io.github.loadup.commons.money.Money;
import io.github.loadup.pay.common.DomainViolation;
import io.github.loadup.pay.limitcenter.domain.model.LimitAttemptEvidence;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucketKey;
import io.github.loadup.pay.limitcenter.domain.model.LimitMetric;
import io.github.loadup.pay.limitcenter.domain.model.LimitOperationGate;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservation;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservationItem;
import io.github.loadup.pay.limitcenter.domain.model.LimitWindowType;
import io.github.loadup.pay.limitcenter.domain.model.RefundAmountRestorePolicy;
import io.github.loadup.pay.limitcenter.domain.model.RefundCountRestorePolicy;
import io.github.loadup.pay.limitcenter.domain.model.WindowBounds;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LimitRecoveryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 10, 0);

    private LimitReservation reservation() {
        return LimitReservation.init(
                        "reservation",
                        "operation",
                        "payment",
                        "order",
                        "merchant",
                        Money.ofMinor(1000, "CNY"),
                        "policy",
                        1,
                        NOW,
                        NOW.plusSeconds(30))
                .reserve();
    }

    @Test
    void retryNeedsProofForTheExactPriorAttemptAndEpochAndDoesNotResetTheHold() {
        var executing = reservation().beginExecution("a1", NOW);
        assertThrows(DomainViolation.class, () -> executing.beginExecution("a2", NOW));
        var proof = new LimitAttemptEvidence("operation", "payment", "a1", 1, "no-collection", true);
        var next = executing.beginExecution("a2", NOW.plusDays(1), proof);
        assertEquals(2, next.executionEpoch());
        assertEquals(executing.initiatedAt(), next.initiatedAt());
        assertEquals(executing.amount(), next.amount());
        assertSame(next, next.beginExecution("a2", NOW.plusDays(2), proof));
        assertThrows(DomainViolation.class, () -> next.beginExecution("a3", NOW.plusDays(2), proof));
        assertThrows(
                DomainViolation.class,
                () -> LimitOperationGate.init("operation", "payment")
                        .fence(1, "evidence")
                        .open());
    }

    @Test
    void refundRestorationUsesOriginalBucketsAndCumulativeHighWaterMarks() {
        var executing = reservation().beginExecution("a1", NOW);
        var confirmed = executing.confirm("payment", Money.ofMinor(1000, "CNY"), 2);
        var originalWindow = new WindowBounds(
                NOW.toLocalDate().withDayOfMonth(1).atStartOfDay(),
                NOW.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay());
        var key = new LimitBucketKey(
                "stable-scope", "merchant", LimitMetric.AMOUNT, "CNY", LimitWindowType.MONTH, originalWindow);
        var item = new LimitReservationItem("item", "reservation", key, 1000, 0, 0);
        assertEquals(
                DomainViolation.Reason.DEPENDENCY_NOT_READY,
                assertThrows(
                        DomainViolation.class,
                        () -> item.restoreRefund(
                                executing,
                                300,
                                RefundAmountRestorePolicy.ORIGINAL_BUCKETS,
                                RefundCountRestorePolicy.NO_RESTORE))
                        .reason());
        var first = item.confirm()
                .restoreRefund(
                        confirmed,
                        300,
                        RefundAmountRestorePolicy.ORIGINAL_BUCKETS,
                        RefundCountRestorePolicy.NO_RESTORE);
        assertEquals(300, first.delta());
        assertEquals(
                0,
                first.item()
                        .restoreRefund(
                                confirmed,
                                200,
                                RefundAmountRestorePolicy.ORIGINAL_BUCKETS,
                                RefundCountRestorePolicy.NO_RESTORE)
                        .delta());
        var full = first.item()
                .restoreRefund(
                        confirmed,
                        1000,
                        RefundAmountRestorePolicy.ORIGINAL_BUCKETS,
                        RefundCountRestorePolicy.NO_RESTORE);
        assertEquals(700, full.delta());
        assertEquals(originalWindow, full.item().bucket().bounds());
    }

    @Test
    void paymentCountRestoresOnlyOnceAfterTheWholePaymentIsRefunded() {
        var confirmed = reservation().beginExecution("a1", NOW).confirm("payment", Money.ofMinor(1000, "CNY"), 2);
        var key = new LimitBucketKey(
                "scope",
                "merchant",
                LimitMetric.PAYMENT_COUNT,
                "CNY",
                LimitWindowType.LIFETIME,
                new WindowBounds(LocalDateTime.of(1970, 1, 1, 0, 0), LocalDateTime.of(9999, 12, 31, 0, 0)));
        var item = new LimitReservationItem("item", "reservation", key, 1, 1, 0);
        assertEquals(
                0,
                item.restoreRefund(
                                confirmed,
                                999,
                                RefundAmountRestorePolicy.NO_RESTORE,
                                RefundCountRestorePolicy.FULL_PAYMENT_REFUNDED_ONCE)
                        .delta());
        var full = item.restoreRefund(
                confirmed,
                1000,
                RefundAmountRestorePolicy.NO_RESTORE,
                RefundCountRestorePolicy.FULL_PAYMENT_REFUNDED_ONCE);
        assertEquals(1, full.delta());
        assertEquals(
                0,
                full.item()
                        .restoreRefund(
                                confirmed,
                                1000,
                                RefundAmountRestorePolicy.NO_RESTORE,
                                RefundCountRestorePolicy.FULL_PAYMENT_REFUNDED_ONCE)
                        .delta());
    }
}
