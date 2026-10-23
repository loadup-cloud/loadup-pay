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

/**
 * Semantic inbox: immutable source binding and replayable ACK, committed with the order/allocation/fee.
 */
public record PaymentEventReceipt(String id, String deliveryId, String businessEventId, String paymentId,
                                  String orderId, String allocationId, long paymentVersion, String evidenceId,
                                  String requestDigest,
                                  String callerServiceId, long orderVersion, LocalDateTime acceptedAt) {
    public PaymentEventReceipt {
        DomainChecks.id(id);
        DomainChecks.id(deliveryId);
        DomainChecks.id(businessEventId);
        DomainChecks.id(paymentId);
        DomainChecks.id(orderId);
        DomainChecks.id(allocationId);
        DomainChecks.id(evidenceId);
        DomainChecks.id(callerServiceId);
        require(paymentVersion > 0 && orderVersion >= 0 && requestDigest != null && requestDigest.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
        Objects.requireNonNull(acceptedAt);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
