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
package io.github.loadup.pay.product.domain.policy;

import io.github.loadup.pay.product.domain.model.AdmissionDecision;
import io.github.loadup.pay.product.domain.model.AdmissionIntent;
import io.github.loadup.pay.product.domain.model.MerchantContractFacts;
import io.github.loadup.pay.product.domain.model.MerchantEligibilityFacts;
import io.github.loadup.pay.product.domain.model.OrderAdmission;

import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.pay.common.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.pay.common.DomainViolation.require;

/** Evaluate under the current-contract lock; reuse persisted admissions before consulting this policy. */
public final class OrderAdmissionPolicy {
    public boolean acceptsRequestedDeadline(AdmissionIntent intent, LocalDateTime now, long maximumWaitSeconds) {
        Objects.requireNonNull(intent);
        Objects.requireNonNull(now);
        io.github.loadup.pay.common.DomainViolation.require(
                maximumWaitSeconds > 0, io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT);
        return intent.requestedDueAt() == null
                || intent.requestedDueAt().isAfter(now)
                        && !intent.requestedDueAt().isAfter(now.plusSeconds(maximumWaitSeconds));
    }

    public OrderAdmission authorize(
            String admissionId,
            String orderId,
            String payee,
            String digest,
            MerchantContractFacts current,
            MerchantEligibilityFacts merchant,
            LocalDateTime now,
            LocalDateTime materializeBefore) {
        Objects.requireNonNull(merchant);
        Objects.requireNonNull(now);
        require(payee.equals(merchant.payeeMerchantId()), CONFLICT);
        if (current != null) require(payee.equals(current.merchantId()), CONFLICT);
        String denial = !merchant.eligible()
                ? merchant.reasonCode()
                : current == null
                        ? "CONTRACT_NOT_FOUND"
                        : !current.admitsNewOrder(now) ? "CONTRACT_NOT_ADMISSIBLE" : null;
        return new OrderAdmission(
                admissionId,
                orderId,
                payee,
                digest,
                denial == null ? AdmissionDecision.ADMITTED : AdmissionDecision.DENIED,
                denial == null ? "ALLOW" : denial,
                denial == null ? current.reference() : null,
                denial == null ? now : null,
                denial == null ? materializeBefore : null);
    }
}
