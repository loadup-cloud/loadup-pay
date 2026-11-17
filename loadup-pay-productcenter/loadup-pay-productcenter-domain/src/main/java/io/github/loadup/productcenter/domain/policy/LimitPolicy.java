/*
 * Copyright (C) 2026 LoadUp Cloud
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
package io.github.loadup.productcenter.domain.policy;

import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.productcenter.client.enums.RefundAmountRestorePolicy;
import io.github.loadup.productcenter.client.enums.RefundCountRestorePolicy;
import java.util.List;

public record LimitPolicy(
        String currency,
        String countingBasis,
        String policyId,
        int weekStart,
        boolean parentAggregationEnabled,
        String parentPolicyRef,
        RefundAmountRestorePolicy amountRestore,
        RefundCountRestorePolicy countRestore,
        List<Rule> rules) {
    public LimitPolicy {
        PolicyChecks.require("CNY".equals(currency)
                && "INITIATED_AT".equals(countingBasis)
                && PolicyChecks.requiredText(policyId, 64)
                && weekStart >= 1
                && weekStart <= 7
                && PolicyChecks.optionalText(parentPolicyRef, 128)
                && amountRestore != null
                && countRestore != null
                && rules != null
                && rules.size() == 8);
        rules = List.copyOf(rules);
        PolicyChecks.require(rules.stream()
                        .map(rule -> rule.window().name() + ":" + rule.metric().name())
                        .distinct()
                        .count()
                == 8);
        if (parentAggregationEnabled) {
            DomainChecks.text(parentPolicyRef, 128);
        }
    }

    public Rule rule(Window window, Metric metric) {
        return rules.stream()
                .filter(rule -> rule.window() == window && rule.metric() == metric)
                .findFirst()
                .orElseThrow();
    }

    public enum Window {
        DAY,
        WEEK,
        MONTH,
        LIFETIME
    }

    public enum Metric {
        AMOUNT,
        PAYMENT_COUNT
    }

    public record Rule(Window window, Metric metric, boolean enabled, Long threshold) {
        public Rule {
            PolicyChecks.require(window != null
                    && metric != null
                    && (!enabled || threshold != null)
                    && (threshold == null || threshold >= 0));
        }
    }
}
