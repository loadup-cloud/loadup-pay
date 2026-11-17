/*
 * Copyright (C) 2026 LoadUp Cloud
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
package io.github.loadup.payment.app.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.json.JsonUtil;
import io.github.loadup.contracts.event.BusinessEventTypes;
import io.github.loadup.contracts.event.FrozenEventTypes;
import io.github.loadup.contracts.event.payment.PaymentOutcomeEventDTO;
import io.github.loadup.contracts.event.payment.PaymentState;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FrozenEventContractTest {
    private final PaymentOutcomeEventDTO data = new PaymentOutcomeEventDTO(
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

    private EventEnvelope<PaymentOutcomeEventDTO> envelope(String type, String producer, long version) {
        return new EventEnvelope<>(
                "delivery",
                "evidence",
                producer,
                type,
                1,
                "payment",
                version,
                OffsetDateTime.parse("2026-11-15T12:00:00+08:00"),
                data);
    }

    @Test
    void newFactTypesAndFrozenPayloadsKeepTheirOwnHeadersWithoutNormalization() {
        for (String type : new String[] {
            BusinessEventTypes.PAYMENT_OUTCOME,
            PaymentDeliveryRoutes.ACQUIRE,
            PaymentDeliveryRoutes.LIMIT,
            PaymentDeliveryRoutes.RISK
        }) {
            var envelope = envelope(type, "payment", 2);
            String frozen = JsonUtil.toJson(envelope);
            data.requireEnvelope(envelope);
            assertEquals(frozen, JsonUtil.toJson(envelope));
            assertEquals(type, envelope.eventType());
        }
    }

    @Test
    void factKindSourceVersionAndDestinationSpecificFrozenTypesRemainBound() {
        assertThrows(
                DomainViolation.class,
                () -> data.requireEnvelope(envelope(BusinessEventTypes.REFUND_OUTCOME, "payment", 2)));
        assertThrows(
                DomainViolation.class,
                () -> data.requireEnvelope(envelope(BusinessEventTypes.PAYMENT_OUTCOME, "other", 2)));
        assertThrows(
                DomainViolation.class,
                () -> data.requireEnvelope(envelope(BusinessEventTypes.PAYMENT_OUTCOME, "payment", 3)));
        assertTrue(FrozenEventTypes.accepts(
                BusinessEventTypes.PAYMENT_OUTCOME, PaymentDeliveryRoutes.ACQUIRE, BusinessEventTypes.PAYMENT_OUTCOME));
        assertFalse(FrozenEventTypes.accepts(
                BusinessEventTypes.PAYMENT_OUTCOME, PaymentDeliveryRoutes.ACQUIRE, PaymentDeliveryRoutes.LIMIT));
    }

    @Test
    void eachFactKindReadsOnlyItsOwnFrozenV1TypesWithoutChangingMetadata() {
        String[][] vocabulary = {
            {
                BusinessEventTypes.PAYMENT_OUTCOME,
                "pay.payment.outcome.acquire.v1",
                "pay.payment.outcome.limit.v1",
                "pay.payment.outcome.risk.v1"
            },
            {BusinessEventTypes.PAYMENT_SNAPSHOT, "pay.payment.snapshot.realtime.v1"},
            {BusinessEventTypes.REFUND_OUTCOME, "pay.payment.refund.limit.v1"},
            {BusinessEventTypes.REFUND_SNAPSHOT, "pay.payment.refund.realtime.v1"},
            {
                BusinessEventTypes.ORDER_REFUND_SUMMARY,
                "pay.payment.refund-summary.realtime.v1",
                "pay.payment.refund.fee.v1"
            },
            {BusinessEventTypes.REFUND_PROGRESS, "pay.payment.refund.merchant.v1"},
            {BusinessEventTypes.ORDER_SNAPSHOT, "pay.acquire.snapshot.realtime.v1"}
        };
        for (var kind : vocabulary) {
            var frozenTypes = Arrays.copyOfRange(kind, 1, kind.length);
            String producer = BusinessEventTypes.ORDER_SNAPSHOT.equals(kind[0]) ? "acquire" : "payment";
            for (String actual : kind) {
                var envelope = new EventEnvelope<>(
                        "delivery",
                        "fact",
                        producer,
                        actual,
                        1,
                        "aggregate",
                        2,
                        OffsetDateTime.parse("2026-11-15T12:00:00+08:00"),
                        Map.of("frozen", "payload"));
                FrozenEventTypes.requireSource(envelope, kind[0], producer, "aggregate", 2, frozenTypes);
                assertEquals(actual, envelope.eventType());
            }
            assertThrows(
                    DomainViolation.class,
                    () -> FrozenEventTypes.requireSource(
                            envelope("unrelated.v1", producer, 2), kind[0], producer, "payment", 2, frozenTypes));
        }
    }
}
