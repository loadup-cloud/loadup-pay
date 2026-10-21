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
package io.github.loadup.pay.common;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import java.time.OffsetDateTime;
import java.util.Objects;

/** An internal integration header; never treats a declared producer as authenticated identity. */
public record PayEventEnvelope<T>(
        String deliveryId,
        String businessEventId,
        String producer,
        String eventType,
        int schemaVersion,
        String aggregateId,
        long aggregateVersion,
        OffsetDateTime occurredAt,
        @DiagnosticHidden T data) {
    public PayEventEnvelope {
        DomainChecks.id(deliveryId);
        DomainChecks.id(businessEventId);
        DomainChecks.text(producer, 32);
        DomainChecks.text(eventType, 128);
        DomainChecks.id(aggregateId);
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(data);
        occurredAt = occurredAt.atZoneSameInstant(BusinessTime.ZONE).toOffsetDateTime();
        DomainViolation.require(schemaVersion > 0 && aggregateVersion >= 0, DomainViolation.Reason.INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }

    /** Structural binding only; authenticate the producer and tenant at the transport boundary. */
    public void requireSource(String expectedType, String expectedProducer, String expectedId, long expectedVersion) {
        DomainViolation.require(eventType.equals(expectedType) && producer.equals(expectedProducer)
                && aggregateId.equals(expectedId) && aggregateVersion == expectedVersion && schemaVersion == 1,
                DomainViolation.Reason.CONFLICT);
    }
}
