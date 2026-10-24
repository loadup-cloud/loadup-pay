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

import io.github.loadup.commons.json.ToStringAsJson;
import java.util.Objects;

/** Internal recovery result; a missing local intent must never be interpreted as financial evidence. */
public record PaymentIntentRecovery(State state, String paymentId) {
    public enum State { NOT_OBSERVED, INPUT_REQUIRED, RESOLVED }
    public PaymentIntentRecovery {
        Objects.requireNonNull(state);
        io.github.loadup.commons.error.DomainViolation.require((state == State.RESOLVED) == (paymentId != null),
                io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT);
    }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
