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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.loadup.commons.error.CommonException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ConditionEvaluatorTest {
    private final ConditionEvaluator evaluator = new ConditionEvaluator();

    private Condition equal(String field) {
        return new Condition.Compare(field, ComparisonOperator.EQ, List.of(TypedValue.text("yes")), null);
    }

    @Test
    void negatingMissingFactsRemainsUnknown() {
        assertThat(evaluator.evaluate(new Condition.Not(equal("missing")), Map.of(), Map.of()))
                .isEqualTo(MatchResult.INDETERMINATE);
    }

    @Test
    void threeValuedAndOrKeepDecisiveResults() {
        var facts = Map.of("answer", TypedValue.text("yes"), "wrong", TypedValue.text("no"));
        assertThat(evaluator.evaluate(new Condition.All(List.of(equal("missing"), equal("wrong"))), facts, Map.of()))
                .isEqualTo(MatchResult.NO_MATCH);
        assertThat(evaluator.evaluate(new Condition.Any(List.of(equal("missing"), equal("answer"))), facts, Map.of()))
                .isEqualTo(MatchResult.MATCH);
        assertThat(evaluator.evaluate(new Condition.All(List.of(equal("missing"), equal("answer"))), facts, Map.of()))
                .isEqualTo(MatchResult.INDETERMINATE);
    }

    @Test
    void incompatibleTypesAndMissingConfigurationDoNotAllow() {
        assertThat(evaluator.evaluate(equal("answer"), Map.of("answer", TypedValue.bool(true)), Map.of()))
                .isEqualTo(MatchResult.INDETERMINATE);
        var rule = new Condition.Compare("amount", ComparisonOperator.LE, List.of(), "limit");
        assertThat(evaluator.evaluate(rule, Map.of("amount", TypedValue.integer(1)), Map.of()))
                .isEqualTo(MatchResult.INDETERMINATE);
    }

    @Test
    void integerAndDecimalUseExactNumericComparison() {
        var rule = new Condition.Compare("amount", ComparisonOperator.EQ, List.of(TypedValue.decimal("123.000")), null);
        assertThat(evaluator.evaluate(rule, Map.of("amount", TypedValue.integer(123)), Map.of()))
                .isEqualTo(MatchResult.MATCH);
    }

    @Test
    void rangeAndSetComparisonHonorBothBoundaries() {
        var between = new Condition.Compare(
                "amount", ComparisonOperator.BETWEEN, List.of(TypedValue.integer(1), TypedValue.integer(10)), null);
        assertThat(evaluator.evaluate(between, Map.of("amount", TypedValue.integer(10)), Map.of()))
                .isEqualTo(MatchResult.MATCH);
        assertThat(evaluator.evaluate(between, Map.of("amount", TypedValue.integer(11)), Map.of()))
                .isEqualTo(MatchResult.NO_MATCH);
        var excluded = new Condition.Compare(
                "country", ComparisonOperator.NOT_IN, List.of(TypedValue.text("US"), TypedValue.text("CA")), null);
        assertThat(evaluator.evaluate(excluded, Map.of("country", TypedValue.text("CA")), Map.of()))
                .isEqualTo(MatchResult.NO_MATCH);
    }

    @Test
    void validatesDepthAndNodeBudgetBeforeEvaluation() {
        Condition deep = equal("answer");
        for (int i = 0; i < 9; i++) {
            deep = new Condition.Not(deep);
        }
        Condition overBudget = deep;
        assertThatThrownBy(() -> evaluator.evaluate(overBudget, Map.of(), Map.of()))
                .isInstanceOf(CommonException.class);
        var wide = new Condition.All(
                IntStream.range(0, 128).mapToObj(i -> equal("answer")).toList());
        assertThatThrownBy(() -> evaluator.validate(wide)).isInstanceOf(CommonException.class);
    }

    @Test
    void rejectsMalformedOperatorsAndUnknownConfigurationReferences() {
        assertThatThrownBy(() -> new Condition.Compare(
                        "amount",
                        ComparisonOperator.BETWEEN,
                        List.of(TypedValue.integer(10), TypedValue.integer(1)),
                        null))
                .isInstanceOf(CommonException.class);
        assertThatThrownBy(() ->
                        new Condition.Compare("enabled", ComparisonOperator.GT, List.of(TypedValue.bool(true)), null))
                .isInstanceOf(CommonException.class);
        assertThatThrownBy(() -> evaluator.validateConfigurationReferences(
                        new Condition.Compare("amount", ComparisonOperator.LE, List.of(), "unknown"), Set.of()))
                .isInstanceOf(CommonException.class);
    }

    @Test
    void refusesHugeExponentBeforeCanonicalExpansion() {
        assertThatThrownBy(() -> TypedValue.decimal("1E+100000000")).isInstanceOf(CommonException.class);
        assertThat(TypedValue.decimal("0.0045000")).isEqualTo(TypedValue.decimal("0.0045"));
    }
}
