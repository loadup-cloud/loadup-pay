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

import java.util.Locale;

/** Non-negative CNY minor units. No floating-point arithmetic or implicit FX. */
public record Money(long minor, String currency) {
    public Money {
        DomainViolation.require(minor >= 0, DomainViolation.Reason.INVALID_ARGUMENT);
        DomainViolation.require("CNY".equals(currency), DomainViolation.Reason.CURRENCY_MISMATCH);
    }

    public static Money cny(long minor) {
        return new Money(minor, "CNY");
    }

    public Money add(Money other) {
        sameCurrency(other);
        return new Money(Math.addExact(minor, other.minor), currency);
    }

    public Money subtract(Money other) {
        sameCurrency(other);
        DomainViolation.require(minor >= other.minor, DomainViolation.Reason.AMOUNT_EXCEEDED);
        return new Money(Math.subtractExact(minor, other.minor), currency);
    }

    public void sameCurrency(Money other) {
        DomainViolation.require(
                other != null && currency.equals(other.currency), DomainViolation.Reason.CURRENCY_MISMATCH);
    }

    public static String defaultCurrency(String currency) {
        String value = currency == null ? "CNY" : currency.toUpperCase(Locale.ROOT);
        DomainViolation.require("CNY".equals(value), DomainViolation.Reason.CURRENCY_MISMATCH);
        return value;
    }
}
