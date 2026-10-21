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
package io.github.loadup.pay.limitcenter.domain;

import io.github.loadup.pay.common.DomainViolation;
import io.github.loadup.pay.limitcenter.domain.model.ActivatedLimitPolicy;
import io.github.loadup.pay.limitcenter.domain.model.LimitMetric;
import io.github.loadup.pay.limitcenter.domain.model.LimitRule;
import io.github.loadup.pay.limitcenter.domain.model.LimitWindowType;
import io.github.loadup.pay.limitcenter.domain.model.RefundAmountRestorePolicy;
import io.github.loadup.pay.limitcenter.domain.model.RefundCountRestorePolicy;
import io.github.loadup.pay.limitcenter.domain.model.RuntimeLimitRule;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActivatedLimitPolicyTest {
    private LimitRule rule(String scope, long threshold, DayOfWeek weekStart) {
        return new LimitRule(
                scope,
                "merchant",
                LimitMetric.AMOUNT,
                "CNY",
                LimitWindowType.LIFETIME,
                weekStart,
                threshold,
                true,
                true);
    }

    private ActivatedLimitPolicy policy(int revision, List<LimitRule> rules) {
        return new ActivatedLimitPolicy(
                "policy",
                revision,
                "a".repeat(64),
                "merchant",
                RefundAmountRestorePolicy.NO_RESTORE,
                RefundCountRestorePolicy.NO_RESTORE,
                rules,
                LocalDateTime.of(2026, 10, 21, 10, 0));
    }

    @Test
    void thresholdUpgradeRetainsTheStableLifetimeIdentity() {
        var first = rule("stable", 100, DayOfWeek.MONDAY);
        var next = rule("stable", 50, DayOfWeek.MONDAY);
        policy(2, List.of(next)).requireStableLineage(policy(1, List.of(first)));
        assertEquals(RuntimeLimitRule.identity(first), RuntimeLimitRule.identity(next));
        assertEquals(
                50,
                new RuntimeLimitRule("policy", 1, first, 0)
                        .revise("policy", 2, next)
                        .rule()
                        .threshold());
    }

    @Test
    void aRevisionCannotDropOrRenameAnExistingCounter() {
        var first = policy(1, List.of(rule("stable", 100, DayOfWeek.MONDAY)));
        assertThrows(
                DomainViolation.class,
                () -> policy(2, List.of(rule("new", 100, DayOfWeek.MONDAY))).requireStableLineage(first));
        assertThrows(
                DomainViolation.class,
                () -> policy(2, List.of(rule("stable", 100, DayOfWeek.MONDAY), rule("extra", 100, DayOfWeek.MONDAY)))
                        .requireStableLineage(first));
    }

    @Test
    void calendarShapeCannotMoveAnExistingCounter() {
        var first = policy(1, List.of(rule("stable", 100, DayOfWeek.MONDAY)));
        assertThrows(
                DomainViolation.class,
                () -> policy(2, List.of(rule("stable", 100, DayOfWeek.SUNDAY))).requireStableLineage(first));
    }

    @Test
    void duplicateIdentitiesCannotCreateMultipleHoldsOnOneBucket() {
        assertThrows(
                DomainViolation.class,
                () -> policy(1, List.of(rule("stable", 100, DayOfWeek.MONDAY), rule("stable", 200, DayOfWeek.MONDAY))));
    }

    @Test
    void anotherPolicyCannotTakeOwnershipOfRuntimeCounters() {
        var rule = rule("stable", 100, DayOfWeek.MONDAY);
        assertThrows(
                DomainViolation.class, () -> new RuntimeLimitRule("policy", 1, rule, 0).revise("different", 2, rule));
    }

    @Test
    void unsupportedParentRulesCannotSilentlyBecomePayeeRules() {
        var parent = new LimitRule(
                "parent",
                "parent-merchant",
                LimitMetric.AMOUNT,
                "CNY",
                LimitWindowType.DAY,
                DayOfWeek.MONDAY,
                100,
                true,
                true);
        assertThrows(DomainViolation.class, () -> policy(1, List.of(parent)));
    }
}
