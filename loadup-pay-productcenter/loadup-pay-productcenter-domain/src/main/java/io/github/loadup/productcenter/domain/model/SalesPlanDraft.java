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
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.productcenter.client.error.ContractResultCodeEnum;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record SalesPlanDraft(
        String planCode,
        int version,
        List<BundleSelection> bundles,
        Map<String, Map<String, TypedValue>> values,
        Map<String, Map<String, OverridePolicy>> merchantPolicies,
        Condition eligibilityCondition,
        Condition usageCondition,
        Instant saleStartsAt,
        Instant saleEndsAt) {
    public SalesPlanDraft {
        planCode = ContractChecks.code(planCode);
        version = ContractChecks.version(version);
        bundles = List.copyOf(bundles);
        if (bundles.isEmpty() || bundles.size() > 128) {
            throw new CommonException(ContractResultCodeEnum.INVALID_BUNDLE_COUNT);
        }
        values = copyNested(values);
        merchantPolicies = copyNested(merchantPolicies);
        new ConditionEvaluator().validate(eligibilityCondition);
        new ConditionEvaluator().validate(usageCondition);
        ContractChecks.interval(saleStartsAt, saleEndsAt);
    }

    private static <T> Map<String, Map<String, T>> copyNested(Map<String, Map<String, T>> input) {
        var result = new HashMap<String, Map<String, T>>();
        ContractChecks.map(input).forEach((key, value) -> result.put(key, ContractChecks.map(value)));
        return Map.copyOf(result);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }

    public record BundleSelection(String alias, BundleVersion bundle) {
        public BundleSelection {
            alias = ContractChecks.code(alias);
            Objects.requireNonNull(bundle, "bundle");
        }
    }
}
