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
import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.*;
import static io.github.loadup.commons.error.DomainViolation.require;

/** Only business continuation identity; scheduling claims and leases belong to DelayedTask. */
public record PaymentRecoveryCursor(String paymentId, long generation, LocalDateTime dueAt, boolean completed) {
    public PaymentRecoveryCursor {
        DomainChecks.id(paymentId);
        Objects.requireNonNull(dueAt);
        require(generation >= 0, INVALID_ARGUMENT);
    }

    public static PaymentRecoveryCursor init(String paymentId, LocalDateTime dueAt) {
        return new PaymentRecoveryCursor(paymentId, 0, dueAt, false);
    }

    public PaymentRecoveryCursor schedule(LocalDateTime nextAt, long expectedGeneration) {
        require(!completed && generation == expectedGeneration, CONFLICT);
        require(nextAt != null && nextAt.isAfter(dueAt), INVALID_ARGUMENT);
        return new PaymentRecoveryCursor(paymentId, Math.incrementExact(generation), nextAt, false);
    }

    public PaymentRecoveryCursor finish() {
        return completed ? this : new PaymentRecoveryCursor(paymentId, generation, dueAt, true);
    }

    @Override
    public String toString() { return ToStringAsJson.reflectionToString(this); }
}
