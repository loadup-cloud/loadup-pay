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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

public record ActivatedLimitPolicy(
        String policyId,
        int policyVersion,
        String policyHash,
        String payeeMerchantId,
        RefundAmountRestorePolicy refundAmountRestore,
        RefundCountRestorePolicy refundCountRestore,
        List<LimitRule> rules,
        LocalDateTime activatedAt) {
    public ActivatedLimitPolicy {
        DomainChecks.id(policyId);
        DomainChecks.id(payeeMerchantId);
        require(policyVersion > 0 && policyHash != null && policyHash.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
        Objects.requireNonNull(activatedAt);
        Objects.requireNonNull(refundAmountRestore);
        Objects.requireNonNull(refundCountRestore);
        rules = rules.stream()
                .sorted(Comparator.comparing(RuntimeLimitRule::identity))
                .toList();
        require(!rules.isEmpty() && rules.size() <= 64, INVALID_ARGUMENT);
        require(rules.stream().map(RuntimeLimitRule::identity).distinct().count() == rules.size(), CONFLICT);
        // Parent aggregation needs a dedicated shared-policy publication protocol.
        require(rules.stream().allMatch(rule -> payeeMerchantId.equals(rule.merchantId())), INVALID_ARGUMENT);
    }

    public void requireStableLineage(ActivatedLimitPolicy prior) {
        require(
                policyId.equals(prior.policyId)
                        && payeeMerchantId.equals(prior.payeeMerchantId)
                        && rules.size() == prior.rules.size(),
                CONFLICT);
        for (int index = 0; index < rules.size(); index++) {
            RuntimeLimitRule.requireSameShape(prior.rules.get(index), rules.get(index));
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
