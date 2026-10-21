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
package io.github.loadup.pay.limitcenter.infrastructure.converter;

import io.github.loadup.pay.common.DomainChecks;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucket;
import io.github.loadup.pay.limitcenter.domain.model.LimitBucketKey;
import io.github.loadup.pay.limitcenter.domain.model.LimitMetric;
import io.github.loadup.pay.limitcenter.domain.model.LimitPolicyHead;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservation;
import io.github.loadup.pay.limitcenter.domain.model.LimitReservationState;
import io.github.loadup.pay.limitcenter.domain.model.LimitWindowType;
import io.github.loadup.pay.limitcenter.domain.model.StoredLimitReservation;
import io.github.loadup.pay.limitcenter.domain.model.WindowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LimitStorageConverterTest {
    private final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(LimitStorageConverter.class.getPackageName());
    private final LimitStorageConverter converter = context.getBean(LimitStorageConverter.class);

    @AfterEach
    void close() {
        context.close();
    }

    @Test
    void rehydratesThePolicyHeadWithoutAdvancingItsVersion() {
        var head = new LimitPolicyHead("policy", "merchant", 3, 7);
        assertEquals(head, converter.toHead(converter.toHeadDO("tenant", head)));
    }

    @Test
    void rehydratesBucketCountersWithoutConfirmingReleasingOrRestoring() {
        var start = LocalDateTime.of(2026, 10, 21, 0, 0);
        var key = new LimitBucketKey(
                "scope",
                "merchant",
                LimitMetric.AMOUNT,
                "CNY",
                LimitWindowType.DAY,
                new WindowBounds(start, start.plusDays(1)));
        var bucket = new LimitBucket(key, 1200, 400, 7);
        assertEquals(bucket, converter.toBucket(converter.toBucketDO("tenant", bucket)));
    }

    @Test
    void rehydratesAnExecutingReservationWithoutTransitioningReleasingOrExpiringIt() {
        var initiated = LocalDateTime.of(2026, 10, 21, 12, 0);
        var reservation = new LimitReservation(
                "reservation",
                "operation",
                "payment",
                "order",
                "merchant",
                DomainChecks.amount(400, "CNY"),
                "policy",
                3,
                initiated,
                LimitReservationState.EXECUTING,
                initiated.plusMinutes(5),
                "attempt",
                2,
                4,
                7);
        var stored = new StoredLimitReservation(reservation, "a".repeat(64), "b".repeat(64), "payment-service");
        assertEquals(stored, converter.toStored(converter.toReservationDO("tenant", stored)));
    }
}
