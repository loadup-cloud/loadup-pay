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
import java.util.Arrays;

/** Reads already-frozen v1 deliveries without rewriting their type, payload or receipt digest. */
public final class FrozenEventTypes {
    private FrozenEventTypes() {}

    public static boolean accepts(String factType, String frozenDeliveryType, String actualType) {
        return factType.equals(actualType) || frozenDeliveryType.equals(actualType);
    }

    public static void requireSource(
            EventEnvelope<?> envelope,
            String factType,
            String producer,
            String aggregateId,
            long aggregateVersion,
            String... frozenDeliveryTypes) {
        DomainViolation.require(
                factType.equals(envelope.eventType())
                        || Arrays.asList(frozenDeliveryTypes).contains(envelope.eventType()),
                DomainViolation.Reason.CONFLICT);
        envelope.requireSource(envelope.eventType(), producer, aggregateId, aggregateVersion);
    }
}
