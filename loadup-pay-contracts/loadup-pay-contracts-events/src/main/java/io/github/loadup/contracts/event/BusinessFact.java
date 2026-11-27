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
package io.github.loadup.contracts.event;

import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.event.EventEnvelope;
import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.time.BusinessTime;
import io.github.loadup.commons.validation.DomainChecks;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Fact metadata and an immutable DTO payload; each destination receives its own delivery identity.
 */
public record BusinessFact<T>(
        String businessEventId,
        String producer,
        String eventType,
        int schemaVersion,
        String aggregateId,
        long aggregateVersion,
        OffsetDateTime occurredAt,
        @DiagnosticHidden T data) {
    public BusinessFact {
        DomainChecks.id(businessEventId);
        DomainChecks.text(producer, 32);
        DomainChecks.text(eventType, 128);
        DomainChecks.id(aggregateId);
        Objects.requireNonNull(occurredAt);
        Objects.requireNonNull(data);
        DomainViolation.require(schemaVersion > 0 && aggregateVersion >= 0, DomainViolation.Reason.INVALID_ARGUMENT);
        occurredAt = occurredAt.atZoneSameInstant(BusinessTime.zone()).toOffsetDateTime();
    }

    public EventEnvelope<T> delivery(String deliveryId) {
        return new EventEnvelope<>(
                deliveryId,
                businessEventId,
                producer,
                eventType,
                schemaVersion,
                aggregateId,
                aggregateVersion,
                occurredAt,
                data);
    }

    @Override
    public String toString() {
        return "BusinessFact[type=" + eventType + ", aggregate=" + aggregateId + ", version=" + aggregateVersion + "]";
    }
}
