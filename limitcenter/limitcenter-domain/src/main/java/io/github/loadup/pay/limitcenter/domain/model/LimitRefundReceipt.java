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
package io.github.loadup.pay.limitcenter.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Immutable command binding and first semantic result, independent of later bucket changes.
 */
public record LimitRefundReceipt(String id, String callerServiceId, String operationId, String paymentId,
                                 String refundId,
                                 long refundVersion, String businessEventId, String refundEvidenceId,
                                 String requestDigest,
                                 long cumulativePaymentRefundedMinor, long verifiedPaymentRefundedMinor,
                                 long verifiedPaymentRefundVersion,
                                 String result, long restoredAmountMinor, boolean restoredPaymentCount,
                                 LocalDateTime acceptedAt) {
    public LimitRefundReceipt {
        DomainChecks.id(id);
        DomainChecks.id(callerServiceId);
        DomainChecks.id(operationId);
        DomainChecks.id(paymentId);
        DomainChecks.id(refundId);
        DomainChecks.id(businessEventId);
        DomainChecks.id(refundEvidenceId);
        Objects.requireNonNull(acceptedAt);
        require(refundVersion > 0 && cumulativePaymentRefundedMinor > 0
                && verifiedPaymentRefundedMinor >= cumulativePaymentRefundedMinor && verifiedPaymentRefundVersion > 0
                && restoredAmountMinor >= 0 && restoredAmountMinor <= verifiedPaymentRefundedMinor
                && requestDigest != null && requestDigest.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
        require((restoredAmountMinor > 0 || restoredPaymentCount ? "RESTORED" : "NO_ACTION").equals(result), INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
