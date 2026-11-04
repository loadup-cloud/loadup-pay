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

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.productcenter.client.error.ContractResultCodeEnum;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class ContractSigner {
    public MerchantContractRevision sign(
            String merchantId,
            String scopeKey,
            String contractId,
            int revision,
            SalesPlanVersion plan,
            Set<String> selectedItems,
            Map<String, Map<String, TypedValue>> merchantValues,
            Map<String, TypedValue> trustedMerchantFacts,
            Instant signedAt,
            Instant effectiveFrom,
            Instant effectiveTo) {
        if (!plan.canSellAt(signedAt)) {
            throw new CommonException(ContractResultCodeEnum.PLAN_OUTSIDE_SALE_WINDOW);
        }
        if (new ConditionEvaluator().evaluate(plan.eligibilityCondition(), trustedMerchantFacts, Map.of())
                != MatchResult.MATCH) {
            throw new CommonException(ContractResultCodeEnum.MERCHANT_ELIGIBILITY_NOT_ESTABLISHED);
        }
        Set<String> selected = Set.copyOf(selectedItems);
        SelectionValidator.validate(plan.items(), selected);
        if (!selected.containsAll(merchantValues.keySet())) {
            throw new CommonException(ContractResultCodeEnum.OVERRIDE_FOR_UNSELECTED_PRODUCT);
        }
        var resolvedItems = new HashMap<String, ResolvedContractItem>();
        var resolver = new ConfigurationResolver();
        var origin = new ValueOrigin(ConfigurationLayer.MERCHANT, contractId, revision);
        for (String key : selected) {
            var item = plan.items().get(key);
            var configuration = resolver.apply(
                    item.product(),
                    item.configuration(),
                    merchantValues.getOrDefault(key, Map.of()),
                    origin,
                    item.merchantPolicies());
            resolver.validateComplete(item.product(), configuration);
            resolvedItems.put(
                    key,
                    new ResolvedContractItem(
                            item.product().productCode(),
                            item.product().version(),
                            item.product().capabilityCode(),
                            configuration,
                            item.usageCondition(),
                            item.bundleOrigin()));
        }
        return new MerchantContractRevision(
                merchantId,
                scopeKey,
                contractId,
                revision,
                plan.planCode(),
                plan.version(),
                signedAt,
                effectiveFrom,
                effectiveTo,
                resolvedItems);
    }
}
