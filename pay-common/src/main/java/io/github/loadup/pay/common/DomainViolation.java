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
package io.github.loadup.pay.common;

import java.util.Objects;

/** A pure-domain failure; application adapters map reasons to public result-code enums. */
public final class DomainViolation extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    public enum Reason {
        INVALID_ARGUMENT,
        INVALID_STATE,
        CONFLICT,
        AMOUNT_EXCEEDED,
        CURRENCY_MISMATCH,
        OUTCOME_UNKNOWN,
        DEPENDENCY_NOT_READY
    }

    private final Reason reason;

    public DomainViolation(Reason reason) {
        super(Objects.requireNonNull(reason, "reason").name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public static void require(boolean condition, Reason reason) {
        if (!condition) throw new DomainViolation(reason);
    }
}
