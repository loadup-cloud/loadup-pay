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
package io.github.loadup.pay.acquire.infrastructure.codec;

import io.github.loadup.pay.common.PayEventEnvelope;
import io.github.loadup.pay.payment.client.enums.PaymentState;
import io.github.loadup.pay.payment.client.event.PaymentOutcomeEventDTO;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PaymentEventCodecImplTest {
    final PaymentEventCodecImpl codec = new PaymentEventCodecImpl();
    final OffsetDateTime at = OffsetDateTime.parse("2026-10-23T12:00:00+08:00");

    PayEventEnvelope<PaymentOutcomeEventDTO> event(String delivery, String operation, long amount, OffsetDateTime time) {
        return new PayEventEnvelope<>(delivery, "proof", "payment", "pay.payment.outcome.acquire.v1", 1, "payment", 2, time,
                new PaymentOutcomeEventDTO("payment", "order", "allocation", operation, "merchant", amount, "CNY",
                        PaymentState.SUCCEEDED, 2, "proof", false));
    }

    @Test
    void digestBindsTenantServiceAndDeliveryIdentity() {
        var e = event("delivery", "operation", 1000, at);
        String digest = codec.digest("tenant", "payment", e);
        assertNotEquals(digest, codec.digest("other-tenant", "payment", e));
        assertNotEquals(digest, codec.digest("tenant", "other-service", e));
        assertNotEquals(digest, codec.digest("tenant", "payment", event("other-delivery", "operation", 1000, at)));
    }

    @Test
    void financialFieldsAndOriginalEnvelopeTimeCannotChangeOnTheSameDelivery() {
        String digest = codec.digest("tenant", "payment", event("delivery", "operation", 1000, at));
        assertNotEquals(digest, codec.digest("tenant", "payment", event("delivery", "operation", 999, at)));
        assertNotEquals(digest, codec.digest("tenant", "payment", event("delivery", "other-operation", 1000, at)));
        assertNotEquals(digest, codec.digest("tenant", "payment", event("delivery", "operation", 1000, at.plusSeconds(1))));
    }

    @Test
    void equivalentOffsetTimesAreNormalizedBeforeDigesting() {
        assertEquals(codec.digest("tenant", "payment", event("delivery", "operation", 1000, at)),
                codec.digest("tenant", "payment", event("delivery", "operation", 1000, at.withOffsetSameInstant(java.time.ZoneOffset.UTC))));
    }
}
