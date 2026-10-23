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
package io.github.loadup.pay.acquire.domain;

import io.github.loadup.commons.money.Money;
import io.github.loadup.pay.acquire.domain.model.AcquireOrder;
import io.github.loadup.pay.acquire.domain.model.FeeRefundPolicy;
import io.github.loadup.pay.acquire.domain.model.OrderFeePolicy;
import io.github.loadup.pay.acquire.domain.model.OrderPaymentPolicy;
import io.github.loadup.pay.acquire.domain.model.UnpaidOrderAction;
import io.github.loadup.pay.common.DomainChecks;
import io.github.loadup.pay.common.DomainViolation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SharedMoneyTest {
    private AcquireOrder order(Money amount) {
        return AcquireOrder.init(
                "order",
                "merchant",
                amount,
                LocalDateTime.of(2026, 10, 21, 12, 0),
                UnpaidOrderAction.CLOSE,
                new OrderPaymentPolicy(1, 10000, null, false));
    }

    @Test
    void frameworkMoneyDoesNotWeakenAcquiringCurrencyOrSignGuards() {
        var foreign = Money.ofMinor(100, "USD");
        var negative = Money.ofMinor(-1, "CNY");
        assertEquals(
                DomainViolation.Reason.CURRENCY_MISMATCH,
                assertThrows(DomainViolation.class, () -> order(foreign)).reason());
        assertEquals(
                DomainViolation.Reason.INVALID_ARGUMENT,
                assertThrows(DomainViolation.class, () -> order(negative)).reason());
        assertEquals(
                DomainViolation.Reason.CURRENCY_MISMATCH,
                assertThrows(DomainViolation.class, () -> DomainChecks.amount(100, "XXX"))
                        .reason());
        assertEquals(0, DomainChecks.amount(0, "CNY").getCent());
    }

    @Test
    void sharedMoneyKeepsIntegerMinorUnitsAndOriginalFeeRounding() {
        var amount = Money.ofMinor(10000, "CNY");
        assertSame(amount, order(amount).amount());
        var fee = new OrderFeePolicy(
                new BigDecimal("0.003"), 0, RoundingMode.HALF_UP, 0, null, FeeRefundPolicy.PRO_RATA_CUMULATIVE)
                .calculate(amount);
        assertEquals(30, fee.getCent());
        assertEquals("CNY", fee.getCurrencyValue());
        assertEquals(Money.ofMinor(30, "CNY"), fee);
    }
}
