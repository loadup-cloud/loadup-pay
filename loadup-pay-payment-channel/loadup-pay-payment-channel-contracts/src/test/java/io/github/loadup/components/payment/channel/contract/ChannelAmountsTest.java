/*
 * Copyright (C) 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.components.payment.channel.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.money.Money;
import org.junit.jupiter.api.Test;

class ChannelAmountsTest {
    @Test
    void acceptsZeroAndTheLargestCnyMinorAmountWithoutRounding() {
        assertEquals(Money.ofMinor(0, "CNY"), ChannelAmounts.cnyAmount(0, "CNY"));
        assertEquals(
                Long.MAX_VALUE, ChannelAmounts.cnyAmount(Long.MAX_VALUE, "CNY").getCent());
        ChannelAmounts.cnyAmount(Money.ofMinor(0, "CNY"));
    }

    @Test
    void preservesCurrencyAndNegativeAmountFailureReasonsForBothOverloads() {
        assertEquals(
                DomainViolation.Reason.CURRENCY_MISMATCH,
                assertThrows(DomainViolation.class, () -> ChannelAmounts.cnyAmount(1, "USD"))
                        .reason());
        assertEquals(
                DomainViolation.Reason.CURRENCY_MISMATCH,
                assertThrows(DomainViolation.class, () -> ChannelAmounts.cnyAmount(Money.ofMinor(1, "USD")))
                        .reason());
        assertEquals(
                DomainViolation.Reason.INVALID_ARGUMENT,
                assertThrows(DomainViolation.class, () -> ChannelAmounts.cnyAmount(-1, "CNY"))
                        .reason());
        assertEquals(
                DomainViolation.Reason.INVALID_ARGUMENT,
                assertThrows(DomainViolation.class, () -> ChannelAmounts.cnyAmount(Money.ofMinor(-1, "CNY")))
                        .reason());
    }

    @Test
    void rejectsMissingMoneyAndCurrencyWithoutRestrictingTheGenericMoneyType() {
        assertThrows(NullPointerException.class, () -> ChannelAmounts.cnyAmount(null));
        assertEquals(
                DomainViolation.Reason.CURRENCY_MISMATCH,
                assertThrows(DomainViolation.class, () -> ChannelAmounts.cnyAmount(1, null))
                        .reason());
        assertEquals(-1, Money.ofMinor(-1, "USD").getCent());
    }
}
