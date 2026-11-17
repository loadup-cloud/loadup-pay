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
package io.github.loadup.productcenter.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.productcenter.app.model.limitcenter.enums.LimitMetric;
import io.github.loadup.productcenter.app.model.limitcenter.enums.LimitWindowType;
import io.github.loadup.productcenter.app.model.limitcenter.support.LimitPolicyHash;
import io.github.loadup.productcenter.client.enums.RefundAmountRestorePolicy;
import io.github.loadup.productcenter.client.enums.RefundCountRestorePolicy;
import io.github.loadup.productcenter.domain.model.TypedValue;
import io.github.loadup.productcenter.domain.policy.LimitPolicy;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class LimitPolicyCompilerTest {
    final LimitPolicyCompiler compiler = new LimitPolicyCompiler();

    @Test
    void disabledRulesStillMeterAllEightStableMerchantCounters() {
        var policy = compiler.compile(
                "merchant",
                AcquiringFixtures.validated(AcquiringFixtures.values()).limits());
        assertEquals(8, policy.rules().size());
        assertTrue(policy.rules().stream().allMatch(rule -> rule.meteringEnabled() && !rule.enforcementEnabled()));
        assertEquals(
                1,
                policy.rules().stream()
                        .map(rule -> rule.ruleScopeId())
                        .distinct()
                        .count());
        assertEquals(policy.policyHash(), LimitPolicyHash.compute(policy));
    }

    @Test
    void enabledLongThresholdIsExactAndDoesNotAlterCounterIdentity() {
        var values = AcquiringFixtures.values();
        values.put("limit.MONTH.AMOUNT.enabled", TypedValue.bool(true));
        values.put("limit.MONTH.AMOUNT.threshold", TypedValue.integer(9007199254740993L));
        var enabled =
                compiler.compile("merchant", AcquiringFixtures.validated(values).limits());
        var amount = enabled.rules().stream()
                .filter(rule -> rule.window() == LimitWindowType.MONTH && rule.metric() == LimitMetric.AMOUNT)
                .findFirst()
                .orElseThrow();
        assertEquals(9007199254740993L, amount.threshold());
        assertTrue(amount.enforcementEnabled());
        var disabled = compiler.compile(
                "merchant",
                AcquiringFixtures.validated(AcquiringFixtures.values()).limits());
        assertEquals(disabled.rules().getFirst().ruleScopeId(), amount.ruleScopeId());
    }

    @Test
    void changingPolicyReferenceCannotGiveTheMerchantANewLifetimeCounterIdentity() {
        var values = AcquiringFixtures.values();
        var original =
                compiler.compile("merchant", AcquiringFixtures.validated(values).limits());
        values.put("limit.policyRef", TypedValue.text("new-policy"));
        assertEquals(
                original.rules().getFirst().ruleScopeId(),
                compiler.compile("merchant", AcquiringFixtures.validated(values).limits())
                        .rules()
                        .getFirst()
                        .ruleScopeId());
    }

    @Test
    void unsupportedParentOrMissingEnabledThresholdFailsBeforeEnqueue() {
        var values = AcquiringFixtures.values();
        values.put("limit.parentAggregationEnabled", TypedValue.bool(true));
        assertThrows(
                CommonException.class,
                () -> compiler.compile(
                        "merchant", AcquiringFixtures.validated(values).limits()));
        values.put("limit.parentAggregationEnabled", TypedValue.bool(false));
        values.put("limit.DAY.AMOUNT.enabled", TypedValue.bool(true));
        assertThrows(
                CommonException.class,
                () -> compiler.compile(
                        "merchant", AcquiringFixtures.validated(values).limits()));
    }

    @Test
    void isolatedLimitFragmentProducesFixedV1IdentitiesWithoutOtherPolicies() {
        var rules = new ArrayList<LimitPolicy.Rule>();
        for (var window : LimitPolicy.Window.values()) {
            for (var metric : LimitPolicy.Metric.values()) {
                rules.add(new LimitPolicy.Rule(window, metric, false, null));
            }
        }
        var fragment = new LimitPolicy(
                "CNY",
                "INITIATED_AT",
                "policy",
                1,
                false,
                null,
                RefundAmountRestorePolicy.NO_RESTORE,
                RefundCountRestorePolicy.NO_RESTORE,
                rules);
        var command = compiler.compile("merchant", fragment);
        assertEquals(
                "8fa1b6e035f9cd0c569ac3bb78aca2f979a6dda9282dd131e95e810d02eaf3ba",
                command.rules().getFirst().ruleScopeId());
        assertEquals("ff62d62eecf3cb02906b1d47e8985e1300731feee0cbbe46f9fae227fd2602bc", command.policyHash());
        assertEquals(1, command.policyVersion());
        assertEquals("policy", command.policyId());
    }

    @Test
    void enabledLargeThresholdKeepsTheFixedCanonicalPolicyHash() {
        var values = AcquiringFixtures.values();
        values.put("limit.MONTH.AMOUNT.enabled", TypedValue.bool(true));
        values.put("limit.MONTH.AMOUNT.threshold", TypedValue.integer(9007199254740993L));
        var command =
                compiler.compile("merchant", AcquiringFixtures.validated(values).limits());
        assertEquals("d6eb4d892cbaa91dace6b99969ee7d72c38d35167139e65c7613fbe4024cce0d", command.policyHash());
    }
}
