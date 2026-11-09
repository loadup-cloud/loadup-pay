/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.acquire.infrastructure.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.util.TenantUtil;
import io.github.loadup.contracts.event.payment.PaymentOutcomeEventDTO;
import io.github.loadup.contracts.event.payment.PaymentState;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PaymentEventCodecImplTest {
    final PaymentEventCodecImpl codec = new PaymentEventCodecImpl();
    final OffsetDateTime at = OffsetDateTime.parse("2026-10-23T12:00:00+08:00");

    EventEnvelope<PaymentOutcomeEventDTO> event(String delivery, String operation, long amount, OffsetDateTime time) {
        return new EventEnvelope<>(
                delivery,
                "proof",
                "payment",
                "pay.payment.outcome.acquire.v1",
                1,
                "payment",
                2,
                time,
                new PaymentOutcomeEventDTO(
                        "payment",
                        "order",
                        "allocation",
                        operation,
                        "merchant",
                        amount,
                        "CNY",
                        PaymentState.SUCCEEDED,
                        2,
                        "proof",
                        false));
    }

    @Test
    void digestBindsTenantServiceAndDeliveryIdentity() {
        var e = event("delivery", "operation", 1000, at);
        String digest = digest("tenant", "payment", e);
        assertNotEquals(digest, digest("other-tenant", "payment", e));
        assertNotEquals(digest, digest("tenant", "other-service", e));
        assertNotEquals(digest, digest("tenant", "payment", event("other-delivery", "operation", 1000, at)));
    }

    @Test
    void financialFieldsAndOriginalEnvelopeTimeCannotChangeOnTheSameDelivery() {
        String digest = digest("tenant", "payment", event("delivery", "operation", 1000, at));
        assertNotEquals(digest, digest("tenant", "payment", event("delivery", "operation", 999, at)));
        assertNotEquals(digest, digest("tenant", "payment", event("delivery", "other-operation", 1000, at)));
        assertNotEquals(digest, digest("tenant", "payment", event("delivery", "operation", 1000, at.plusSeconds(1))));
    }

    @Test
    void equivalentOffsetTimesAreNormalizedBeforeDigesting() {
        assertEquals(
                digest("tenant", "payment", event("delivery", "operation", 1000, at)),
                digest(
                        "tenant",
                        "payment",
                        event("delivery", "operation", 1000, at.withOffsetSameInstant(ZoneOffset.UTC))));
    }

    private String digest(String tenant, String service, EventEnvelope<PaymentOutcomeEventDTO> event) {
        return TenantUtil.callWithTenant(tenant, () -> codec.digest(service, event));
    }
}
