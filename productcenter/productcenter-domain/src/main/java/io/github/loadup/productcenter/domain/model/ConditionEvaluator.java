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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ConditionEvaluator {
    private static boolean compatible(TypedValue left, TypedValue right) {
        return left.type() == right.type() || left.numeric() && right.numeric();
    }

    static int compare(TypedValue left, TypedValue right) {
        return left.numeric() && right.numeric()
                ? left.number().compareTo(right.number())
                : left.value().compareTo(right.value());
    }

    public void validate(Condition condition) {
        count(Objects.requireNonNull(condition), 1, new int[1]);
    }

    public void validateConfigurationReferences(Condition condition, Set<String> parameterKeys) {
        validate(condition);
        switch (condition) {
            case Condition.All all ->
                all.children().forEach(child -> validateConfigurationReferences(child, parameterKeys));
            case Condition.Any any ->
                any.children().forEach(child -> validateConfigurationReferences(child, parameterKeys));
            case Condition.Not not -> validateConfigurationReferences(not.child(), parameterKeys);
            case Condition.Compare rule -> {
                if (rule.configurationKey() != null && !parameterKeys.contains(rule.configurationKey())) {
                    throw new CommonException(ContractResultCodeEnum.UNKNOWN_RULE_CONFIGURATION_REFERENCE);
                }
            }
            default -> {}
        }
    }

    private void count(Condition condition, int depth, int[] nodes) {
        if (depth > 8 || ++nodes[0] > 128) {
            throw new CommonException(ContractResultCodeEnum.RULE_TREE_EXCEEDS_BUDGET);
        }
        switch (condition) {
            case Condition.All all -> all.children().forEach(child -> count(child, depth + 1, nodes));
            case Condition.Any any -> any.children().forEach(child -> count(child, depth + 1, nodes));
            case Condition.Not not -> count(not.child(), depth + 1, nodes);
            default -> {}
        }
    }

    public MatchResult evaluate(
            Condition condition, Map<String, TypedValue> facts, Map<String, TypedValue> configuration) {
        validate(condition);
        return evaluateNode(condition, ContractChecks.map(facts), ContractChecks.map(configuration));
    }

    private MatchResult evaluateNode(
            Condition condition, Map<String, TypedValue> facts, Map<String, TypedValue> configuration) {
        return switch (condition) {
            case Condition.All all -> combine(all.children(), facts, configuration, true);
            case Condition.Any any -> combine(any.children(), facts, configuration, false);
            case Condition.Not not ->
                switch (evaluateNode(not.child(), facts, configuration)) {
                    case MATCH -> MatchResult.NO_MATCH;
                    case NO_MATCH -> MatchResult.MATCH;
                    case INDETERMINATE -> MatchResult.INDETERMINATE;
                };
            case Condition.Exists exists ->
                facts.get(exists.field()) == null ? MatchResult.NO_MATCH : MatchResult.MATCH;
            case Condition.Compare comparison -> evaluateComparison(comparison, facts, configuration);
        };
    }

    private MatchResult combine(
            List<Condition> children,
            Map<String, TypedValue> facts,
            Map<String, TypedValue> configuration,
            boolean all) {
        boolean unknown = false;
        for (Condition child : children) {
            MatchResult result = evaluateNode(child, facts, configuration);
            if (all && result == MatchResult.NO_MATCH) {
                return result;
            }
            if (!all && result == MatchResult.MATCH) {
                return result;
            }
            unknown |= result == MatchResult.INDETERMINATE;
        }
        return unknown ? MatchResult.INDETERMINATE : all ? MatchResult.MATCH : MatchResult.NO_MATCH;
    }

    private MatchResult evaluateComparison(
            Condition.Compare rule, Map<String, TypedValue> facts, Map<String, TypedValue> configuration) {
        TypedValue actual = facts.get(rule.field());
        TypedValue reference = rule.configurationKey() == null ? null : configuration.get(rule.configurationKey());
        if (actual == null || rule.configurationKey() != null && reference == null) {
            return MatchResult.INDETERMINATE;
        }
        List<TypedValue> expected = reference == null ? rule.expected() : List.of(reference);
        if (expected.stream().anyMatch(v -> !compatible(actual, v))) {
            return MatchResult.INDETERMINATE;
        }
        if (actual.type() == ValueType.BOOLEAN
                && rule.operator() != ComparisonOperator.EQ
                && rule.operator() != ComparisonOperator.NE
                && rule.operator() != ComparisonOperator.IN
                && rule.operator() != ComparisonOperator.NOT_IN) {
            return MatchResult.INDETERMINATE;
        }
        int compared = compare(actual, expected.getFirst());
        boolean matches =
                switch (rule.operator()) {
                    case EQ -> compared == 0;
                    case NE -> compared != 0;
                    case GT -> compared > 0;
                    case GE -> compared >= 0;
                    case LT -> compared < 0;
                    case LE -> compared <= 0;
                    case IN -> expected.stream().anyMatch(v -> compare(actual, v) == 0);
                    case NOT_IN -> expected.stream().noneMatch(v -> compare(actual, v) == 0);
                    case BETWEEN -> compared >= 0 && compare(actual, expected.getLast()) <= 0;
                };
        return matches ? MatchResult.MATCH : MatchResult.NO_MATCH;
    }
}
