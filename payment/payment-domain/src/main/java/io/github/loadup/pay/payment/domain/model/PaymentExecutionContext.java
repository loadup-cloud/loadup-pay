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

import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * A consistent validated financial view, never proof that a database lock or Y2 ticket is still owned.
 */
public record PaymentExecutionContext(PaymentGateBinding gates, PaymentAttemptBinding attempt) {
    public PaymentExecutionContext {
        Objects.requireNonNull(gates);
        Objects.requireNonNull(attempt);
        require(gates.accepted().equals(attempt.accepted()), CONFLICT);
    }

    public PaymentExecutionContext requireActive() {
        attempt.requireActive();
        return this;
    }

    public PaymentExecutionContext requireSubmittable() {
        gates.requireOpen();
        attempt.requireSubmittable();
        return this;
    }

    public PaymentExecutionContext requireObservable() {
        attempt.requireObservable();
        return this;
    }

    public PaymentExecutionContext requireClosable() {
        attempt.requireClosable();
        return this;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
