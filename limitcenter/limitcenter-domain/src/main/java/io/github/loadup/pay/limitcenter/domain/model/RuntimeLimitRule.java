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
package io.github.loadup.pay.limitcenter.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.common.DomainChecks;

import java.util.Objects;

import static io.github.loadup.pay.common.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.pay.common.DomainViolation.require;

/**
 * Stable counters use the latest threshold, including reservations from older immutable policy revisions.
 */
public record RuntimeLimitRule(String ownerPolicyId, int policyVersion, LimitRule rule, long rowVersion) {
    public RuntimeLimitRule {
        DomainChecks.id(ownerPolicyId);
        Objects.requireNonNull(rule);
        require(policyVersion > 0 && rowVersion >= 0, INVALID_ARGUMENT);
    }

    public static String identity(LimitRule rule) {
        return String.join(
                "\0",
                rule.ruleScopeId(),
                rule.merchantId(),
                rule.metric().name(),
                rule.currency(),
                rule.window().name());
    }

    public static void requireSameShape(LimitRule first, LimitRule next) {
        require(identity(first).equals(identity(next)) && first.weekStart() == next.weekStart(), CONFLICT);
    }

    public RuntimeLimitRule revise(String policyId, int revision, LimitRule next) {
        require(ownerPolicyId.equals(policyId) && revision > policyVersion, CONFLICT);
        requireSameShape(rule, next);
        return new RuntimeLimitRule(ownerPolicyId, revision, next, Math.incrementExact(rowVersion));
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
