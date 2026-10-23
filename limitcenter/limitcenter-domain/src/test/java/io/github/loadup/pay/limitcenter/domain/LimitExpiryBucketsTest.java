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

import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucket;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucketKey;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucketSet;
import io.github.loadup.pay.limitcenter.domain.model.LimitMetric;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservationItem;
import io.github.loadup.pay.limitcenter.domain.model.LimitWindowType;
import io.github.loadup.pay.limitcenter.domain.policy.LimitWindowPolicy;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LimitExpiryBucketsTest {
    LimitBucketKey key(LimitMetric metric) {
        return new LimitBucketKey(
                "scope",
                "merchant",
                metric,
                "CNY",
                LimitWindowType.MONTH,
                new LimitWindowPolicy()
                        .at(LimitWindowType.MONTH, LocalDateTime.of(2026, 9, 30, 23, 59), DayOfWeek.MONDAY));
    }

    @Test
    void originalAmountAndCountBucketsAreReleasedTogether() {
        var amount = key(LimitMetric.AMOUNT);
        var count = key(LimitMetric.PAYMENT_COUNT);
        var before = new LimitBucketSet(
                Map.of(amount, new LimitBucket(amount, 10, 60, 0), count, new LimitBucket(count, 2, 3, 0)));
        var after = before.releaseReserved(List.of(
                new LimitReservationItem("a", "reservation", amount, 40, 0, 0),
                new LimitReservationItem("c", "reservation", count, 1, 0, 0)));
        assertEquals(20, after.buckets().get(amount).held());
        assertEquals(2, after.buckets().get(count).held());
        assertEquals(
                LocalDate.of(2026, 9, 1).atStartOfDay(),
                after.buckets().get(amount).key().bounds().start());
    }

    @Test
    void aFailedSecondReleaseDoesNotMutateTheInputBucketSet() {
        var amount = key(LimitMetric.AMOUNT);
        var count = key(LimitMetric.PAYMENT_COUNT);
        var before = new LimitBucketSet(
                Map.of(amount, new LimitBucket(amount, 0, 40, 0), count, new LimitBucket(count, 0, 0, 0)));
        assertThrows(
                DomainViolation.class,
                () -> before.releaseReserved(List.of(
                        new LimitReservationItem("a", "reservation", amount, 40, 0, 0),
                        new LimitReservationItem("c", "reservation", count, 1, 0, 0))));
        assertEquals(40, before.buckets().get(amount).held());
    }

    @Test
    void consumedQuotaIsNotAnUnexecutedReservationHold() {
        var amount = key(LimitMetric.AMOUNT);
        var before = new LimitBucketSet(Map.of(amount, new LimitBucket(amount, 40, 0, 0)));
        assertThrows(
                DomainViolation.class,
                () -> before.releaseReserved(List.of(new LimitReservationItem("a", "reservation", amount, 40, 40, 0))));
    }

    @Test
    void duplicateItemKeysCannotReleaseTheSameBucketTwice() {
        var amount = key(LimitMetric.AMOUNT);
        var count = key(LimitMetric.PAYMENT_COUNT);
        var before = new LimitBucketSet(
                Map.of(amount, new LimitBucket(amount, 0, 80, 0), count, new LimitBucket(count, 0, 1, 0)));
        assertThrows(
                DomainViolation.class,
                () -> before.releaseReserved(List.of(
                        new LimitReservationItem("a", "reservation", amount, 40, 0, 0),
                        new LimitReservationItem("b", "reservation", amount, 40, 0, 0))));
    }
}
