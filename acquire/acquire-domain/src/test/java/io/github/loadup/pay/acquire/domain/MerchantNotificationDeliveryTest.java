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
package io.github.loadup.pay.acquire.domain;

import io.github.loadup.pay.acquire.domain.model.MerchantNotificationDelivery;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationPolicy;
import io.github.loadup.pay.acquire.domain.model.MerchantNotificationState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class MerchantNotificationDeliveryTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 21, 12, 0);
    private static final String BODY = "frozen merchant event body";
    private static final String DIGEST = "a".repeat(64);
    private static final String FINGERPRINT = "b".repeat(64);

    private static MerchantNotificationDelivery delivery(MerchantNotificationPolicy policy) {
        return MerchantNotificationDelivery.init("delivery", "event", "order", "merchant", "PaymentSucceeded",
                "payment", 7, BODY, DIGEST, policy, NOW);
    }

    private static MerchantNotificationPolicy policy(long initial, BigDecimal multiplier, long maximum,
                                                     int attempts, long window) {
        return new MerchantNotificationPolicy("target", "signing-key", 1000, initial, multiplier, maximum,
                attempts, window);
    }

    @Test
    void retryBackoffRoundsUpAndSaturatesAtTheConfiguredMaximumEvenForMaximumAttempt() {
        var policy = policy(3, new BigDecimal("1.5"), 20, Integer.MAX_VALUE, 3600);

        assertEquals(3, policy.delaySeconds(1));
        assertEquals(5, policy.delaySeconds(2));
        assertEquals(7, policy.delaySeconds(3));
        assertEquals(20, policy.delaySeconds(Integer.MAX_VALUE));

        var unitMultiplier = policy(2, BigDecimal.ONE, 9, 3, 3600);
        assertEquals(2, unitMultiplier.delaySeconds(Integer.MAX_VALUE));
        assertEquals(policy(2, new BigDecimal("2"), 9, 3, 3600),
                policy(2, new BigDecimal("2.000000000000000000"), 9, 3, 3600));
    }

    @Test
    void beginAdvancesOnlyTheDeliveryCursorAndPreservesIdentityAndFrozenSourceBody() {
        var initial = delivery(policy(5, new BigDecimal("2"), 60, 5, 3600));

        var begun = initial.begin(NOW, FINGERPRINT);

        assertNotSame(initial, begun);
        assertEquals(initial.id(), begun.id());
        assertEquals(initial.eventId(), begun.eventId());
        assertEquals(initial.orderId(), begun.orderId());
        assertEquals(initial.payeeMerchantId(), begun.payeeMerchantId());
        assertEquals(initial.eventType(), begun.eventType());
        assertEquals(initial.aggregateId(), begun.aggregateId());
        assertEquals(initial.sourceVersion(), begun.sourceVersion());
        assertEquals(BODY, initial.body());
        assertEquals(BODY, begun.body());
        assertEquals(DIGEST, begun.bodyDigest());
        assertEquals(initial.occurredAt(), begun.occurredAt());
        assertEquals(initial.expiresAt(), begun.expiresAt());
        assertEquals(1, begun.attemptCount());
        assertEquals(MerchantNotificationState.PENDING, begun.state());
        assertEquals(FINGERPRINT, begun.routeFingerprint());
        assertEquals(1, begun.rowVersion());
        assertEquals(0, initial.attemptCount());
        assertEquals(MerchantNotificationState.INIT, initial.state());
        assertEquals(null, initial.routeFingerprint());
    }
}
