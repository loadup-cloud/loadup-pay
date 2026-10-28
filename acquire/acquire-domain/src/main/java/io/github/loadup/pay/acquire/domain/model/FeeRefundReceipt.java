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
package io.github.loadup.pay.acquire.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

public record FeeRefundReceipt(String id, String deliveryId, String businessEventId, String orderId, long sourceVersion,
                               long appliedVersion, String requestDigest, String callerServiceId,
                               LocalDateTime acceptedAt) {
    public FeeRefundReceipt {
        DomainChecks.id(id);
        DomainChecks.id(deliveryId);
        DomainChecks.id(businessEventId);
        DomainChecks.id(orderId);
        DomainChecks.id(callerServiceId);
        Objects.requireNonNull(acceptedAt);
        require(sourceVersion > 0 && appliedVersion >= sourceVersion && requestDigest != null && requestDigest.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
