/*
 * Copyright (C) 2025-2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.productcenter.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public final class ContractRuntimeResolver {
    public ContractDecision resolve(
            MerchantContract contract,
            String itemKey,
            Instant trustedBusinessTime,
            Map<String, TypedValue> trustedFacts) {
        Objects.requireNonNull(contract, "contract");
        ContractChecks.code(itemKey);
        Objects.requireNonNull(trustedBusinessTime, "business time");
        Objects.requireNonNull(trustedFacts, "facts");
        if (contract.status() == ContractStatus.SUSPENDED) {
            return denied(contract, null, ContractDecision.Reason.SUSPENDED);
        }
        if (contract.status() == ContractStatus.TERMINATED) {
            return denied(contract, null, ContractDecision.Reason.TERMINATED);
        }
        var revision = contract.revisions().stream()
                .filter(r -> r.effectiveAt(trustedBusinessTime))
                .findFirst()
                .orElse(null);
        if (revision == null) {
            return denied(contract, null, ContractDecision.Reason.NO_EFFECTIVE_REVISION);
        }
        var item = revision.items().get(itemKey);
        if (item == null) {
            return denied(contract, revision, ContractDecision.Reason.PRODUCT_NOT_SIGNED);
        }
        MatchResult result = new ConditionEvaluator()
                .evaluate(
                        item.usageCondition(),
                        trustedFacts,
                        item.configuration().values());
        if (result != MatchResult.MATCH) {
            return denied(
                    contract,
                    revision,
                    result == MatchResult.INDETERMINATE
                            ? ContractDecision.Reason.FACTS_INDETERMINATE
                            : ContractDecision.Reason.CONDITION_REJECTED);
        }
        return new ContractDecision(
                true,
                ContractDecision.Reason.ALLOWED,
                contract.contractId(),
                revision.revision(),
                revision.snapshotHash(),
                item.configuration());
    }

    private ContractDecision denied(
            MerchantContract contract, MerchantContractRevision revision, ContractDecision.Reason reason) {
        return new ContractDecision(
                false, reason, contract.contractId(), revision == null ? null : revision.revision(), null, null);
    }
}
