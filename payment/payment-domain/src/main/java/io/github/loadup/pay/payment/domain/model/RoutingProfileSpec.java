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
package io.github.loadup.pay.payment.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import java.util.HashSet;
import java.util.List;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Ordered immutable policy. Operational availability is still controlled by the live binding head.
 */
public record RoutingProfileSpec(String profileRef, String payeeMerchantId, List<RoutingRule> rules) {
    public RoutingProfileSpec {
        DomainChecks.text(profileRef, 128);
        DomainChecks.id(payeeMerchantId);
        require(rules != null && !rules.isEmpty() && rules.size() <= 100, INVALID_ARGUMENT);
        rules = List.copyOf(rules);
        require(new HashSet<>(rules).size() == rules.size(), INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
