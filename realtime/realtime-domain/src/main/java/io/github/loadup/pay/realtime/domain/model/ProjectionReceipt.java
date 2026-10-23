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
package io.github.loadup.pay.realtime.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.common.DomainChecks;
import io.github.loadup.pay.common.DomainViolation;

import java.time.LocalDateTime;
import java.util.Objects;

public record ProjectionReceipt(String id, String deliveryId, String businessEventId, String producer,
                                String aggregateId,
                                long sourceVersion, long projectedVersion, String disposition, String requestDigest,
                                String callerServiceId,
                                LocalDateTime acceptedAt) {
    public ProjectionReceipt {
        DomainChecks.id(id);
        DomainChecks.id(deliveryId);
        DomainChecks.id(businessEventId);
        DomainChecks.text(producer, 32);
        DomainChecks.id(aggregateId);
        DomainChecks.id(callerServiceId);
        Objects.requireNonNull(acceptedAt);
        DomainViolation.require(sourceVersion >= 0 && projectedVersion >= sourceVersion && requestDigest != null
                && requestDigest.matches("[0-9a-f]{64}"), DomainViolation.Reason.INVALID_ARGUMENT);
        new ProjectionAdvance(disposition, projectedVersion);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
