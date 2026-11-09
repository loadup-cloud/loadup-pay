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
package io.github.loadup.productcenter.app.support;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.productcenter.app.converter.ContractConverter;
import io.github.loadup.productcenter.client.dto.CatalogDefinitions;
import io.github.loadup.productcenter.client.error.ContractResultCodeEnum;
import io.github.loadup.productcenter.domain.gateway.CatalogGateway;
import io.github.loadup.productcenter.domain.model.BundleVersion;
import io.github.loadup.productcenter.domain.model.CatalogEntry;
import io.github.loadup.productcenter.domain.model.CatalogKind;
import io.github.loadup.productcenter.domain.model.CatalogStatus;
import io.github.loadup.productcenter.domain.model.ComparisonOperator;
import io.github.loadup.productcenter.domain.model.Condition;
import io.github.loadup.productcenter.domain.model.ConditionEvaluator;
import io.github.loadup.productcenter.domain.model.ConfigurationLayer;
import io.github.loadup.productcenter.domain.model.ConfigurationResolver;
import io.github.loadup.productcenter.domain.model.OverridePolicy;
import io.github.loadup.productcenter.domain.model.ParameterDefinition;
import io.github.loadup.productcenter.domain.model.ProductVersion;
import io.github.loadup.productcenter.domain.model.SalesPlanCompiler;
import io.github.loadup.productcenter.domain.model.SalesPlanDraft;
import io.github.loadup.productcenter.domain.model.SalesPlanVersion;
import io.github.loadup.productcenter.domain.model.TypedValue;
import io.github.loadup.productcenter.domain.model.ValueOrigin;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CatalogAssembler {
    private final CatalogGateway gateway;
    private final ContractCodec codec;
    private final ContractConverter converter;

    public CatalogAssembler(CatalogGateway gateway, ContractCodec codec, ContractConverter converter) {
        this.gateway = gateway;
        this.codec = codec;
        this.converter = converter;
    }

    private static ProductVersion requireProduct(Map<String, ProductVersion> products, String key) {
        ProductVersion product = products.get(key);
        if (product == null) {
            throw new CommonException(ContractResultCodeEnum.UNKNOWN_PLAN_ITEM);
        }
        return product;
    }

    private static <T> Set<T> set(Set<T> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }

    public void validate(CatalogEntry entry, boolean publishing) {
        switch (entry.kind()) {
            case PRODUCT -> product(entry, publishing);
            case CONDITION -> condition(entry);
            case BUNDLE -> bundle(entry, publishing);
            case SALES_PLAN -> plan(entry, publishing);
        }
    }

    private CatalogEntry reference(String id, CatalogKind kind, boolean publishing) {
        CatalogEntry entry = (publishing ? gateway.lock(id) : gateway.find(id))
                .orElseThrow(() -> new CommonException(ContractResultCodeEnum.NOT_FOUND));
        if (entry.kind() != kind
                || entry.status() == CatalogStatus.DRAFT
                || publishing && entry.status() != CatalogStatus.PUBLISHED) {
            throw new CommonException(ContractResultCodeEnum.INVALID_CATALOG_REFERENCE);
        }
        return entry;
    }

    private Condition condition(String id, boolean publishing) {
        return id == null || id.isBlank()
                ? Condition.always()
                : condition(reference(id, CatalogKind.CONDITION, publishing));
    }

    public Condition condition(CatalogEntry entry) {
        var definition = codec.read(entry.content(), CatalogDefinitions.ConditionDefinition.class);
        if (definition.clauses() == null || definition.clauses().size() > 100) {
            throw new CommonException(ContractResultCodeEnum.INVALID_CONDITION_CLAUSES);
        }
        List<Condition> clauses = new ArrayList<>();
        for (var clause : definition.clauses()) {
            if (!clause.field().startsWith("merchant.") && !clause.field().startsWith("transaction.")) {
                throw new CommonException(ContractResultCodeEnum.UNSUPPORTED_FACT_NAMESPACE);
            }
            Condition node;
            if ("EXISTS".equals(clause.kind())) {
                node = new Condition.Exists(clause.field());
            } else if ("COMPARE".equals(clause.kind())) {
                var expected = clause.expected() == null
                        ? List.<TypedValue>of()
                        : clause.expected().stream()
                                .map(v -> ContractMappingSupport.value(clause.valueType(), v))
                                .toList();
                String key = clause.configurationKey() == null
                                || clause.configurationKey().isBlank()
                        ? null
                        : clause.configurationKey();
                node = new Condition.Compare(
                        clause.field(), ComparisonOperator.valueOf(clause.operator()), expected, key);
            } else {
                throw new CommonException(ContractResultCodeEnum.UNKNOWN_CLAUSE_KIND);
            }
            clauses.add(clause.negated() ? new Condition.Not(node) : node);
        }
        Condition condition =
                switch (definition.mode()) {
                    case "ALL" -> new Condition.All(clauses);
                    case "ANY" -> new Condition.Any(clauses);
                    default -> throw new CommonException(ContractResultCodeEnum.UNKNOWN_CONDITION_MODE);
                };
        new ConditionEvaluator().validate(condition);
        return condition;
    }

    public ProductVersion product(CatalogEntry entry, boolean publishing) {
        var d = codec.read(entry.content(), CatalogDefinitions.Product.class);
        if (d.parameters() == null || d.parameters().size() > 256) {
            throw new CommonException(ContractResultCodeEnum.INVALID_PARAMETER_COUNT);
        }
        var parameters = new HashMap<String, ParameterDefinition>();
        for (var parameter : d.parameters()) {
            ParameterDefinition mapped = converter.toParameter(parameter);
            if (parameters.putIfAbsent(mapped.key(), mapped) != null) {
                throw new CommonException(ContractResultCodeEnum.DUPLICATE_PARAMETER);
            }
        }
        return new ProductVersion(
                entry.code(),
                entry.version(),
                d.capabilityCode(),
                parameters,
                condition(d.conditionId(), publishing),
                set(d.dependencies()),
                set(d.exclusions()));
    }

    public BundleVersion bundle(CatalogEntry entry, boolean publishing) {
        var d = codec.read(entry.content(), CatalogDefinitions.Bundle.class);
        if (d.items() == null || d.items().isEmpty() || d.items().size() > 128) {
            throw new CommonException(ContractResultCodeEnum.INVALID_BUNDLE_ITEMS);
        }
        List<BundleVersion.Item> items = new ArrayList<>();
        for (var item : d.items()) {
            ProductVersion product = product(reference(item.productId(), CatalogKind.PRODUCT, publishing), publishing);
            Map<String, TypedValue> values = typedValues(product, item.values());
            new ConfigurationResolver()
                    .apply(
                            product,
                            new ConfigurationResolver().defaults(product),
                            values,
                            new ValueOrigin(ConfigurationLayer.BUNDLE, entry.code(), entry.version()),
                            Map.of());
            items.add(new BundleVersion.Item(
                    item.itemKey(),
                    product,
                    item.required(),
                    item.defaultSelected(),
                    values,
                    condition(item.conditionId(), publishing)));
        }
        return new BundleVersion(entry.code(), entry.version(), items);
    }

    public SalesPlanVersion plan(CatalogEntry entry, boolean publishing) {
        var d = codec.read(entry.content(), CatalogDefinitions.Plan.class);
        if (d.bundles() == null || d.bundles().isEmpty() || d.bundles().size() > 128) {
            throw new CommonException(ContractResultCodeEnum.INVALID_PLAN_BUNDLES);
        }
        List<SalesPlanDraft.BundleSelection> bundles = new ArrayList<>();
        Map<String, ProductVersion> products = new HashMap<>();
        for (var selected : d.bundles()) {
            BundleVersion bundle = bundle(reference(selected.bundleId(), CatalogKind.BUNDLE, publishing), publishing);
            bundles.add(new SalesPlanDraft.BundleSelection(selected.alias(), bundle));
            for (var item : bundle.items()) {
                if (products.putIfAbsent(selected.alias() + "." + item.itemKey(), item.product()) != null) {
                    throw new CommonException(ContractResultCodeEnum.DUPLICATE_PLAN_ITEM);
                }
            }
        }
        Map<String, Map<String, TypedValue>> values = new HashMap<>();
        if (d.values() != null) {
            d.values()
                    .forEach(
                            (key, overrides) -> values.put(key, typedValues(requireProduct(products, key), overrides)));
        }
        Map<String, Map<String, OverridePolicy>> policies = new HashMap<>();
        if (d.merchantPolicies() != null) {
            d.merchantPolicies().forEach((key, fields) -> {
                ProductVersion product = requireProduct(products, key);
                Map<String, OverridePolicy> mapped = new HashMap<>();
                fields.forEach((field, policy) -> {
                    ParameterDefinition parameter = product.parameters().get(field);
                    if (parameter == null) {
                        throw new CommonException(ContractResultCodeEnum.UNKNOWN_POLICY_FIELD);
                    }
                    mapped.put(
                            field,
                            new OverridePolicy(
                                    ContractMappingSupport.decimal(policy.minimum()),
                                    ContractMappingSupport.decimal(policy.maximum()),
                                    ContractMappingSupport.values(
                                            parameter.type().name(), policy.allowedValues())));
                });
                policies.put(key, mapped);
            });
        }
        Condition eligibility = condition(d.eligibilityConditionId(), publishing);
        merchantOnly(eligibility);
        return new SalesPlanCompiler()
                .publish(new SalesPlanDraft(
                        entry.code(),
                        entry.version(),
                        bundles,
                        values,
                        policies,
                        eligibility,
                        condition(d.usageConditionId(), publishing),
                        d.saleStartsAt(),
                        d.saleEndsAt()));
    }

    private void merchantOnly(Condition condition) {
        switch (condition) {
            case Condition.All all -> all.children().forEach(this::merchantOnly);
            case Condition.Any any -> any.children().forEach(this::merchantOnly);
            case Condition.Not not -> merchantOnly(not.child());
            case Condition.Exists exists -> {
                if (!exists.field().startsWith("merchant.")) {
                    throw new CommonException(ContractResultCodeEnum.ELIGIBILITY_REQUIRES_MERCHANT_FACTS);
                }
            }
            case Condition.Compare compare -> {
                if (!compare.field().startsWith("merchant.")) {
                    throw new CommonException(ContractResultCodeEnum.ELIGIBILITY_REQUIRES_MERCHANT_FACTS);
                }
            }
        }
    }

    public Map<String, TypedValue> typedValues(ProductVersion product, Map<String, String> input) {
        Map<String, TypedValue> result = new HashMap<>();
        if (input == null) {
            return Map.of();
        }
        input.forEach((field, value) -> {
            ParameterDefinition parameter = product.parameters().get(field);
            if (parameter == null || value == null) {
                throw new CommonException(ContractResultCodeEnum.UNKNOWN_OR_NULL_PARAMETER);
            }
            result.put(field, ContractMappingSupport.value(parameter.type().name(), value));
        });
        return Map.copyOf(result);
    }

    public Map<String, Map<String, TypedValue>> merchantValues(
            SalesPlanVersion plan, Map<String, Map<String, String>> input) {
        if (input == null) {
            throw new CommonException(ContractResultCodeEnum.VALUES_REQUIRED);
        }
        var mapped = new HashMap<String, Map<String, TypedValue>>();
        input.forEach((key, value) -> {
            var item = plan.items().get(key);
            if (item == null) {
                throw new CommonException(ContractResultCodeEnum.UNKNOWN_SIGNED_ITEM);
            }
            mapped.put(key, typedValues(item.product(), value));
        });
        return mapped;
    }
}
