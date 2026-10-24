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
package io.github.loadup.pay.acquire.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import java.util.Set;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * An order-scoped capability grants summary, payment initiation and original-session progress only.
 */
public record CheckoutSessionScope(
        int schemaVersion, String callerScopeHash, String returnTargetCode, Set<String> permissions) {
    public CheckoutSessionScope {
        require(
                schemaVersion == 1 && callerScopeHash != null && callerScopeHash.matches("[0-9a-f]{64}"),
                INVALID_ARGUMENT);
        DomainChecks.text(returnTargetCode, 128);
        permissions = Set.copyOf(permissions);
        require(permissions.equals(Set.of("ORDER_SUMMARY", "PAYMENT_START", "PAYMENT_PROGRESS")), INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
