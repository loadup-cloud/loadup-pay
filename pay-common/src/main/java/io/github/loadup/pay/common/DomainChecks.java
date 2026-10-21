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

import io.github.loadup.commons.money.Money;
import java.util.Objects;

public final class DomainChecks {
    private DomainChecks() {}

    public static String id(String value) {
        return text(value, 64);
    }

    public static String text(String value, int max) {
        DomainViolation.require(
                value != null
                        && !value.isBlank()
                        && value.length() <= max
                        && value.chars().noneMatch(Character::isISOControl),
                DomainViolation.Reason.INVALID_ARGUMENT);
        return value;
    }

    public static long positive(long value) {
        DomainViolation.require(value > 0, DomainViolation.Reason.INVALID_ARGUMENT);
        return value;
    }

    /** Acquiring accepts non-negative CNY amounts; the framework Money type also supports other cases. */
    public static void amount(Money value) {
        Objects.requireNonNull(value);
        DomainViolation.require("CNY".equals(value.getCurrencyValue()), DomainViolation.Reason.CURRENCY_MISMATCH);
        DomainViolation.require(value.getCent() >= 0, DomainViolation.Reason.INVALID_ARGUMENT);
    }

    /** Validate the acquiring boundary before creating the shared immutable Money value. */
    public static Money amount(long minor, String currency) {
        DomainViolation.require("CNY".equals(currency), DomainViolation.Reason.CURRENCY_MISMATCH);
        DomainViolation.require(minor >= 0, DomainViolation.Reason.INVALID_ARGUMENT);
        return Money.ofMinor(minor, currency);
    }
}
