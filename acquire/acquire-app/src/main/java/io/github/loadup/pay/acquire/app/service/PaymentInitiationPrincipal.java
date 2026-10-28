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
package io.github.loadup.pay.acquire.app.service;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.acquire.domain.model.StoredAcquireOrder;

import java.util.Objects;
import java.util.function.BiConsumer;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Ephemeral internal authority bound by an authenticated entry point or a committed original recovery intent.
 */
record PaymentInitiationPrincipal(String tenantId, String scopeHash, String actionScope,
                                  @DiagnosticHidden BiConsumer<StoredAcquireOrder, Boolean> guard) {
    PaymentInitiationPrincipal {
        DomainChecks.id(tenantId);
        DomainChecks.text(actionScope, 128);
        Objects.requireNonNull(guard);
        require(scopeHash != null && scopeHash.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
    }

    void authorize(StoredAcquireOrder order, boolean creating) {
        guard.accept(order, creating);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
