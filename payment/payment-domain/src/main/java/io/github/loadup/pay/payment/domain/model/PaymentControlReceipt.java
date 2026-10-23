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

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.common.DomainChecks;

import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.pay.common.DomainViolation.require;

/**
 * Immutable local command receipt. The digest is calculated by payment, never accepted from the caller.
 */
public record PaymentControlReceipt(
        String id,
        String kind,
        String targetId,
        String orderId,
        String payeeMerchantId,
        String callerServiceId,
        String requestKey,
        String requestDigest,
        long requestedEpoch,
        String reasonCode,
        String noCollectionEvidenceId) {
    public PaymentControlReceipt {
        DomainChecks.id(id);
        DomainChecks.id(targetId);
        DomainChecks.id(orderId);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.id(callerServiceId);
        DomainChecks.text(requestKey, 128);
        require(
                ("PARENT_FENCE".equals(kind) || "PAYMENT_CLOSE".equals(kind))
                        && requestedEpoch > 0
                        && requestDigest != null
                        && requestDigest.matches("[0-9a-f]{64}"),
                INVALID_ARGUMENT);
        if (reasonCode != null) DomainChecks.text(reasonCode, 64);
        if (noCollectionEvidenceId != null) DomainChecks.id(noCollectionEvidenceId);
        if ("PARENT_FENCE".equals(kind))
            require(targetId.equals(orderId) && reasonCode == null && noCollectionEvidenceId == null, INVALID_ARGUMENT);
        else require(reasonCode != null, INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
