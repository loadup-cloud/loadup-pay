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
import java.util.Set;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/** Immutable source delivery binding; ACK concerns registration, not merchant receipt or money movement. */
public record RefundNotificationReceipt(String id, String deliveryId, String businessEventId, String aggregateId,
        long sourceVersion, String orderId, String payeeMerchantId, String requestDigest, String callerServiceId,
        String merchantEventId, String disposition, LocalDateTime acceptedAt) {
    public RefundNotificationReceipt {
        DomainChecks.id(id); DomainChecks.id(deliveryId); DomainChecks.id(businessEventId); DomainChecks.id(aggregateId);
        DomainChecks.id(orderId); DomainChecks.id(payeeMerchantId); DomainChecks.id(callerServiceId);
        Objects.requireNonNull(acceptedAt);
        require(sourceVersion > 0 && requestDigest != null && requestDigest.matches("[0-9a-f]{64}")
                && merchantEventId != null && merchantEventId.matches("[0-9a-f]{64}")
                && disposition != null && Set.of("ENQUEUED", "DISABLED").contains(disposition), INVALID_ARGUMENT);
    }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
