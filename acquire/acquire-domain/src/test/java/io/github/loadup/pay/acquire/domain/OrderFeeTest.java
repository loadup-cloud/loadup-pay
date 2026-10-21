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
import io.github.loadup.pay.acquire.domain.model.FeeRefundPolicy;
import io.github.loadup.pay.acquire.domain.model.OrderFee;
import io.github.loadup.pay.acquire.domain.model.OrderFeePolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderFeeTest {
    @Test
    void earlyRefundBasisIsRetainedUntilTheFeeIsConfirmed() {
        var policy = new OrderFeePolicy(
                new BigDecimal("0.003"), 0, RoundingMode.HALF_UP, 0, null, FeeRefundPolicy.PRO_RATA_CUMULATIVE);
        var fee = OrderFee.init("fee", "order", Money.ofMinor(10000, "CNY"), policy)
                .recordRefundBasis(1, 3000);
        assertEquals(30, fee.feeAmount().getCent());
        assertEquals(0, fee.adjustmentDueMinor());
        fee = fee.confirm();
        assertEquals(9, fee.adjustmentDueMinor());
        fee = fee.acknowledgeAdjustment().recordRefundBasis(2, 10000);
        assertEquals(21, fee.adjustmentDueMinor());
        assertEquals(0, fee.acknowledgeAdjustment().adjustmentDueMinor());
        assertEquals(30, fee.feeAmount().getCent());
    }
}
