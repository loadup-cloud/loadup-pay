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
import io.github.loadup.commons.contract.ContractReference;
import io.github.loadup.commons.validation.DomainChecks;

import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Authoritative current facts adapted from the reused framework contract aggregate.
 */
public record MerchantContractFacts(
        String merchantId,
        ContractReference reference,
        ContractAdmissionState state,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        boolean limitPolicyActivated) {
    public MerchantContractFacts {
        DomainChecks.id(merchantId);
        Objects.requireNonNull(reference);
        Objects.requireNonNull(state);
        Objects.requireNonNull(validFrom);
        require(validTo == null || validTo.isAfter(validFrom), INVALID_ARGUMENT);
    }

    public boolean admitsNewOrder(LocalDateTime now) {
        return state == ContractAdmissionState.ACTIVE
                && limitPolicyActivated
                && !now.isBefore(validFrom)
                && (validTo == null || now.isBefore(validTo));
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
