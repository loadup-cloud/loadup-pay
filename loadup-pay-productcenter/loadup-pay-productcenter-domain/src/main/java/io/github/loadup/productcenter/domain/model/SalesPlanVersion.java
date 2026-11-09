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

import io.github.loadup.commons.json.ToStringAsJson;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class SalesPlanVersion {
    private final String planCode;
    private final int version;
    private final Map<String, Item> items;
    private final Condition eligibilityCondition;
    private final Instant saleStartsAt;
    private final Instant saleEndsAt;

    SalesPlanVersion(SalesPlanDraft draft, Map<String, Item> items) {
        this.planCode = draft.planCode();
        this.version = draft.version();
        this.items = Map.copyOf(items);
        this.eligibilityCondition = draft.eligibilityCondition();
        this.saleStartsAt = draft.saleStartsAt();
        this.saleEndsAt = draft.saleEndsAt();
    }

    public String planCode() {
        return planCode;
    }

    public int version() {
        return version;
    }

    public Map<String, Item> items() {
        return items;
    }

    public Condition eligibilityCondition() {
        return eligibilityCondition;
    }

    public boolean canSellAt(Instant time) {
        return !time.isBefore(saleStartsAt) && (saleEndsAt == null || time.isBefore(saleEndsAt));
    }

    public Set<String> defaultSelection() {
        return items.entrySet().stream()
                .filter(e -> e.getValue().defaultSelected())
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }

    public record Item(
            ProductVersion product,
            boolean required,
            boolean defaultSelected,
            ResolvedConfiguration configuration,
            Map<String, OverridePolicy> merchantPolicies,
            Condition usageCondition,
            ValueOrigin bundleOrigin) {
        public Item {
            merchantPolicies = Map.copyOf(merchantPolicies);
        }
    }
}
