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
package io.github.loadup.pay.product.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.common.ContractReference;
import io.github.loadup.pay.common.DomainChecks;

import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.pay.common.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.pay.common.DomainViolation.require;

public record OrderAdmission(
        String id,
        String orderId,
        String payeeMerchantId,
        String requestDigest,
        AdmissionDecision decision,
        String reasonCode,
        ContractReference contract,
        LocalDateTime admittedAt,
        LocalDateTime materializeBefore) {
    public OrderAdmission {
        DomainChecks.id(id);
        DomainChecks.id(orderId);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.text(requestDigest, 64);
        Objects.requireNonNull(decision);
        DomainChecks.text(reasonCode, 64);
        if (decision == AdmissionDecision.ADMITTED)
            require(
                    contract != null
                            && admittedAt != null
                            && materializeBefore != null
                            && materializeBefore.isAfter(admittedAt),
                    INVALID_ARGUMENT);
        else require(contract == null && admittedAt == null && materializeBefore == null, INVALID_ARGUMENT);
    }

    public void requireSameIntent(String order, String payee, String digest) {
        require(orderId.equals(order) && payeeMerchantId.equals(payee) && requestDigest.equals(digest), CONFLICT);
    }

    public boolean canMaterialize(LocalDateTime now) {
        return decision == AdmissionDecision.ADMITTED && !now.isBefore(admittedAt) && now.isBefore(materializeBefore);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
