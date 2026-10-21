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

import io.github.loadup.commons.json.*;
import io.github.loadup.pay.common.DomainChecks;
import java.time.LocalDateTime;
import java.util.Objects;
import static io.github.loadup.pay.common.DomainViolation.require;
import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT;

/** Original caller ownership and immutable admission travel with the domain order. */
public record StoredAcquireOrder(
        AcquireOrder order,
        String merchantOrderNo,
        String subject,
        OrderAdmissionProof admission,
        LocalDateTime admittedAt,
        String callerScopeHash,
        String snapshotId,
        @DiagnosticHidden String admissionPayload,
        String payloadHash) {
    public StoredAcquireOrder {
        Objects.requireNonNull(order);
        Objects.requireNonNull(admission);
        Objects.requireNonNull(admittedAt);
        DomainChecks.id(merchantOrderNo);
        DomainChecks.text(subject, 200);
        DomainChecks.id(snapshotId);
        DomainChecks.text(admissionPayload, 262144);
        require(
                payloadHash != null
                        && payloadHash.matches("[0-9a-f]{64}")
                        && callerScopeHash != null
                        && callerScopeHash.matches("[0-9a-f]{64}"),
                INVALID_ARGUMENT);
        admission.requireBoundTo(order.id(), order.payeeMerchantId(), admission.requestDigest());
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
