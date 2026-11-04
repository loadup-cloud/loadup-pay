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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class SalesPlanCompiler {
    private final ConfigurationResolver resolver = new ConfigurationResolver();

    public SalesPlanVersion publish(SalesPlanDraft draft) {
        new ConditionEvaluator().validateConfigurationReferences(draft.eligibilityCondition(), Set.of());
        var items = new HashMap<String, SalesPlanVersion.Item>();
        var aliases = new HashSet<String>();
        for (var selection : draft.bundles()) {
            if (!aliases.add(selection.alias())) {
                throw new CommonException(ContractResultCodeEnum.DUPLICATE_BUNDLE_ALIAS);
            }
            for (var item : selection.bundle().items()) {
                String key = ContractChecks.code(selection.alias() + "." + item.itemKey());
                if (items.containsKey(key)) {
                    throw new CommonException(ContractResultCodeEnum.DUPLICATE_QUALIFIED_ITEM);
                }
                var product = item.product();
                var policies = draft.merchantPolicies().getOrDefault(key, Map.of());
                policies.forEach((field, policy) -> {
                    ParameterDefinition definition = product.parameters().get(field);
                    if (definition == null) {
                        throw new CommonException(ContractResultCodeEnum.UNKNOWN_PERMISSION_FIELD);
                    }
                    policy.validateDefinition(definition);
                });
                var resolved = resolver.apply(
                        product,
                        resolver.defaults(product),
                        item.values(),
                        new ValueOrigin(
                                ConfigurationLayer.BUNDLE,
                                selection.bundle().bundleCode(),
                                selection.bundle().version()),
                        Map.of());
                resolved = resolver.apply(
                        product,
                        resolved,
                        draft.values().getOrDefault(key, Map.of()),
                        new ValueOrigin(ConfigurationLayer.SALES_PLAN, draft.planCode(), draft.version()),
                        Map.of());
                for (var definition : product.parameters().values()) {
                    if (definition.required()
                            && !resolved.values().containsKey(definition.key())
                            && !policies.containsKey(definition.key())) {
                        throw new CommonException(ContractResultCodeEnum.REQUIRED_FIELD_CANNOT_BE_FILLED);
                    }
                }
                Condition usage = new Condition.All(
                        List.of(product.usageCondition(), item.usageCondition(), draft.usageCondition()));
                new ConditionEvaluator()
                        .validateConfigurationReferences(
                                usage, product.parameters().keySet());
                items.put(
                        key,
                        new SalesPlanVersion.Item(
                                product,
                                item.required(),
                                item.defaultSelected(),
                                resolved,
                                policies,
                                usage,
                                new ValueOrigin(
                                        ConfigurationLayer.BUNDLE,
                                        selection.bundle().bundleCode(),
                                        selection.bundle().version())));
                if (items.size() > 256) {
                    throw new CommonException(ContractResultCodeEnum.TOO_MANY_PLAN_ITEMS);
                }
            }
        }
        if (!items.keySet().containsAll(draft.values().keySet())
                || !items.keySet().containsAll(draft.merchantPolicies().keySet())) {
            throw new CommonException(ContractResultCodeEnum.UNKNOWN_PLAN_ITEM);
        }
        SelectionValidator.validate(
                items,
                items.entrySet().stream()
                        .filter(e -> e.getValue().defaultSelected())
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet()));
        return new SalesPlanVersion(draft, items);
    }
}
