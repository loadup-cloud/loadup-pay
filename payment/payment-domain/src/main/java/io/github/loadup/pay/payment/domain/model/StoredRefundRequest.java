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
package io.github.loadup.pay.payment.domain.model;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

public record StoredRefundRequest(RefundRequest request, String reasonCode, String requestDigest,
                                  String callerSubjectHash,
                                  String admissionId, @DiagnosticHidden String termsPayload, String termsHash,
                                  LocalDateTime plannedAt,
                                  List<StoredRefundChild> allocations) {
    public StoredRefundRequest {
        Objects.requireNonNull(request);
        Objects.requireNonNull(plannedAt);
        DomainChecks.text(reasonCode, 64);
        DomainChecks.id(admissionId);
        DomainChecks.text(termsPayload, 262144);
        require(requestDigest != null && requestDigest.matches("[0-9a-f]{64}")
                && callerSubjectHash != null && callerSubjectHash.matches("[0-9a-f]{64}")
                && termsHash != null && termsHash.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
        allocations = List.copyOf(allocations);
        require(allocations.size() == request.children().size(), CONFLICT);
        for (int i = 0; i < allocations.size(); i++) {
            require(allocations.get(i).sequenceNo() == i + 1 && allocations.get(i).refund().equals(request.children().get(i)), CONFLICT);
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
