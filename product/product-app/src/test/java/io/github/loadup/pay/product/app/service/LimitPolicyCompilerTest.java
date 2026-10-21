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
package io.github.loadup.pay.product.app.service;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.modules.contract.domain.model.TypedValue;
import io.github.loadup.pay.limitcenter.client.enums.LimitMetric;
import io.github.loadup.pay.limitcenter.client.enums.LimitWindowType;
import io.github.loadup.pay.limitcenter.client.support.LimitPolicyHash;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimitPolicyCompilerTest {
    final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    final LimitPolicyCompiler compiler = new LimitPolicyCompiler(new AcquiringTermsCompiler(validation.getValidator()));

    @AfterEach
    void close() {
        validation.close();
    }

    @Test
    void disabledRulesStillMeterAllEightStableMerchantCounters() {
        var policy =
                compiler.compile("tenant", "merchant", AcquiringFixtures.configuration(AcquiringFixtures.values()));
        assertEquals(8, policy.rules().size());
        assertTrue(policy.rules().stream().allMatch(rule -> rule.meteringEnabled() && !rule.enforcementEnabled()));
        assertEquals(
                1,
                policy.rules().stream()
                        .map(rule -> rule.ruleScopeId())
                        .distinct()
                        .count());
        assertEquals(policy.policyHash(), LimitPolicyHash.compute("tenant", policy));
    }

    @Test
    void enabledLongThresholdIsExactAndDoesNotAlterCounterIdentity() {
        var values = AcquiringFixtures.values();
        values.put("limit.MONTH.AMOUNT.enabled", TypedValue.bool(true));
        values.put("limit.MONTH.AMOUNT.threshold", TypedValue.integer(9007199254740993L));
        var enabled = compiler.compile("tenant", "merchant", AcquiringFixtures.configuration(values));
        var amount = enabled.rules().stream()
                .filter(rule -> rule.window() == LimitWindowType.MONTH && rule.metric() == LimitMetric.AMOUNT)
                .findFirst()
                .orElseThrow();
        assertEquals(9007199254740993L, amount.threshold());
        assertTrue(amount.enforcementEnabled());
        var disabled =
                compiler.compile("tenant", "merchant", AcquiringFixtures.configuration(AcquiringFixtures.values()));
        assertEquals(disabled.rules().getFirst().ruleScopeId(), amount.ruleScopeId());
    }

    @Test
    void changingPolicyReferenceCannotGiveTheMerchantANewLifetimeCounterIdentity() {
        var values = AcquiringFixtures.values();
        var original = compiler.compile("tenant", "merchant", AcquiringFixtures.configuration(values));
        values.put("limit.policyRef", TypedValue.text("new-policy"));
        assertEquals(
                original.rules().getFirst().ruleScopeId(),
                compiler.compile("tenant", "merchant", AcquiringFixtures.configuration(values))
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
                () -> compiler.compile("tenant", "merchant", AcquiringFixtures.configuration(values)));
        values.put("limit.parentAggregationEnabled", TypedValue.bool(false));
        values.put("limit.DAY.AMOUNT.enabled", TypedValue.bool(true));
        assertThrows(
                CommonException.class,
                () -> compiler.compile("tenant", "merchant", AcquiringFixtures.configuration(values)));
    }
}
