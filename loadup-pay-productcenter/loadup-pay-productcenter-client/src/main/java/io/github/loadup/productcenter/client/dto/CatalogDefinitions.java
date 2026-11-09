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
package io.github.loadup.productcenter.client.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CatalogDefinitions {
    private CatalogDefinitions() {}

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }

    public record Parameter(
            @Schema(description = "Key") String key,
            @Schema(description = "Type") String type,
            @Schema(description = "Required") boolean required,
            @Schema(description = "Default value") String defaultValue,
            @Schema(description = "Minimum") String minimum,
            @Schema(description = "Maximum") String maximum,
            @Schema(description = "Allowed values") Set<String> allowedValues,
            @Schema(description = "Editable layers") Set<String> editableLayers) {}

    public record Product(
            String capabilityCode,
            List<Parameter> parameters,
            String conditionId,
            Set<String> dependencies,
            Set<String> exclusions) {}

    public record Clause(
            String kind,
            boolean negated,
            String field,
            String operator,
            String valueType,
            List<String> expected,
            String configurationKey) {}

    public record ConditionDefinition(String mode, List<Clause> clauses) {}

    public record BundleItem(
            String itemKey,
            String productId,
            boolean required,
            boolean defaultSelected,
            Map<String, String> values,
            String conditionId) {}

    public record Bundle(List<BundleItem> items) {}

    public record BundleSelection(String alias, String bundleId) {}

    public record MerchantPolicy(String minimum, String maximum, Set<String> allowedValues) {}

    public record Plan(
            List<BundleSelection> bundles,
            Map<String, Map<String, String>> values,
            Map<String, Map<String, MerchantPolicy>> merchantPolicies,
            String eligibilityConditionId,
            String usageConditionId,
            Instant saleStartsAt,
            Instant saleEndsAt) {}
}
