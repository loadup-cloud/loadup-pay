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
import io.github.loadup.commons.money.Money;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucket;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucketChange;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucketKey;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucketSet;
import io.github.loadup.pay.limitcenter.domain.model.LimitMetric;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservation;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservationState;
import io.github.loadup.pay.limitcenter.domain.model.LimitWindowType;
import io.github.loadup.pay.limitcenter.domain.model.WindowBounds;
import io.github.loadup.pay.limitcenter.domain.policy.LimitWindowPolicy;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MerchantLimitTest {
    @Test
    void monthAndWeekUseShanghaiCalendarBoundaries() {
        var policy = new LimitWindowPolicy();
        var month = policy.at(LimitWindowType.MONTH, OffsetDateTime.parse("2024-02-29T16:00:00Z"), DayOfWeek.MONDAY);
        assertEquals(LocalDateTime.of(2024, 3, 1, 0, 0), month.start());
        assertEquals(LocalDateTime.of(2024, 4, 1, 0, 0), month.end());
        var week = policy.at(LimitWindowType.WEEK, LocalDateTime.of(2026, 10, 25, 10, 0), DayOfWeek.MONDAY);
        assertEquals(LocalDateTime.of(2026, 10, 19, 0, 0), week.start());
    }

    @Test
    void failingOneWindowDoesNotMutateAnyBucket() {
        var window = new WindowBounds(LocalDateTime.of(2026, 10, 21, 0, 0), LocalDateTime.of(2026, 10, 22, 0, 0));
        var amount = new LimitBucketKey("scope", "merchant", LimitMetric.AMOUNT, "CNY", LimitWindowType.DAY, window);
        var count =
                new LimitBucketKey("scope", "merchant", LimitMetric.PAYMENT_COUNT, "CNY", LimitWindowType.DAY, window);
        var original = new LimitBucketSet(
                Map.of(amount, new LimitBucket(amount, 0, 0, 0), count, new LimitBucket(count, 1, 0, 0)));
        assertThrows(
                DomainViolation.class,
                () -> original.reserve(List.of(
                        new LimitBucketChange(amount, 100, 1000, true), new LimitBucketChange(count, 1, 1, true))));
        assertEquals(0, original.buckets().get(amount).held());
        assertEquals(0, original.buckets().get(count).held());
    }

    @Test
    void executionIsNotReleasedByReservationTtl() {
        var now = LocalDateTime.of(2026, 10, 21, 10, 0);
        var reservation = new LimitReservation(
                "reservation",
                "operation",
                "payment",
                "order",
                "merchant",
                Money.ofMinor(100, "CNY"),
                "policy",
                1,
                now,
                LimitReservationState.RESERVED,
                now.plusMinutes(1),
                null,
                0,
                0,
                0);
        var executing = reservation.beginExecution("attempt", now);
        assertThrows(DomainViolation.class, () -> executing.expire(now.plusDays(1)));
    }
}
