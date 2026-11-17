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
import io.github.loadup.contracts.event.BusinessEventTypes;
import io.github.loadup.contracts.event.payment.PaymentFinancialEvidence;
import io.github.loadup.contracts.event.payment.PaymentOutcomeEventDTO;
import io.github.loadup.contracts.event.payment.PaymentState;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Assertions;
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
                        false,
                        null));
    }

    @Test
    void digestBindsServiceAndDeliveryIdentity() {
        var e = event("delivery", "operation", 1000, at);
        String digest = digest("payment", e);
        assertNotEquals(digest, digest("other-service", e));
        assertNotEquals(digest, digest("payment", event("other-delivery", "operation", 1000, at)));
    }

    @Test
    void financialFieldsAndOriginalEnvelopeTimeCannotChangeOnTheSameDelivery() {
        String digest = digest("payment", event("delivery", "operation", 1000, at));
        assertNotEquals(digest, digest("payment", event("delivery", "operation", 999, at)));
        assertNotEquals(digest, digest("payment", event("delivery", "other-operation", 1000, at)));
        assertNotEquals(digest, digest("payment", event("delivery", "operation", 1000, at.plusSeconds(1))));
    }

    @Test
    void equivalentOffsetTimesAreNormalizedBeforeDigesting() {
        assertEquals(
                digest("payment", event("delivery", "operation", 1000, at)),
                digest("payment", event("delivery", "operation", 1000, at.withOffsetSameInstant(ZoneOffset.UTC))));
    }

    private String digest(String service, EventEnvelope<PaymentOutcomeEventDTO> event) {
        return codec.digest(service, event);
    }

    @Test
    void frozenAndCanonicalEventHeadersKeepIndependentFixedReceiptDigests() {
        var data = new PaymentOutcomeEventDTO(
                "payment",
                "order",
                "allocation",
                "operation",
                "merchant",
                100,
                "CNY",
                PaymentState.SUCCEEDED,
                2,
                "evidence",
                false,
                null);
        var at = OffsetDateTime.parse("2026-11-15T12:00:00+08:00");
        var legacy = new EventEnvelope<>(
                "delivery", "evidence", "payment", "pay.payment.outcome.acquire.v1", 1, "payment", 2, at, data);
        var canonical = new EventEnvelope<>(
                "delivery", "evidence", "payment", BusinessEventTypes.PAYMENT_OUTCOME, 1, "payment", 2, at, data);
        data.requireEnvelope(legacy);
        data.requireEnvelope(canonical);
        var codec = new PaymentEventCodecImpl();
        Assertions.assertEquals(
                "e08c61f3b42bb59107c4910f169e7008037266882799c7db89dbc63c49013771", codec.digest("payment", legacy));
        Assertions.assertEquals(
                "bba3725f3767bd6bb337d9940ed10655a01e8c6dd71c2a537ea3206df6fb38a7", codec.digest("payment", canonical));
    }

    EventEnvelope<PaymentOutcomeEventDTO> financial(
            String attempt, OffsetDateTime initiated, OffsetDateTime succeeded) {
        var legacy = event("delivery", "operation", 1000, at);
        var proof = new PaymentFinancialEvidence(
                1,
                "payment",
                "order",
                "allocation",
                "operation",
                "merchant",
                1000,
                "CNY",
                PaymentState.SUCCEEDED,
                2,
                "proof",
                attempt,
                initiated,
                succeeded,
                false);
        return new EventEnvelope<>(
                legacy.deliveryId(),
                legacy.businessEventId(),
                legacy.producer(),
                BusinessEventTypes.PAYMENT_FINANCIAL,
                1,
                legacy.aggregateId(),
                legacy.aggregateVersion(),
                legacy.occurredAt(),
                legacy.data().withFinancialEvidence(proof));
    }

    @Test
    void fullProofIsBoundIntoTheReceiptAndEquivalentOffsetsRemainIdentical() {
        var initial = at.minusMinutes(1);
        String expected = digest("payment", financial("attempt", initial, at));
        assertNotEquals(expected, digest("payment", financial("other-attempt", initial, at)));
        assertNotEquals(expected, digest("payment", financial("attempt", initial.plusSeconds(1), at)));
        assertNotEquals(expected, digest("payment", financial("attempt", initial, at.plusSeconds(1))));
        assertEquals(
                expected,
                digest(
                        "payment",
                        financial(
                                "attempt",
                                initial.withOffsetSameInstant(ZoneOffset.UTC),
                                at.withOffsetSameInstant(ZoneOffset.UTC))));
    }
}
