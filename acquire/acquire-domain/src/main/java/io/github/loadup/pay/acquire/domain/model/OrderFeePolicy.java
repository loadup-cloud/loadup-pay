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
package io.github.loadup.pay.acquire.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.validation.DomainChecks;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

public record OrderFeePolicy(
        BigDecimal rate,
        long fixedMinor,
        RoundingMode rounding,
        long minimumMinor,
        Long maximumMinor,
        FeeRefundPolicy refundPolicy) {
    public OrderFeePolicy {
        Objects.requireNonNull(rate);
        Objects.requireNonNull(rounding);
        Objects.requireNonNull(refundPolicy);
        require(
                rate.signum() >= 0
                        && fixedMinor >= 0
                        && minimumMinor >= 0
                        && (maximumMinor == null || maximumMinor >= minimumMinor),
                INVALID_ARGUMENT);
    }

    public Money calculate(Money orderAmount) {
        DomainChecks.cnyAmount(orderAmount);
        require(orderAmount.getCent() > 0, INVALID_ARGUMENT);
        long rounded = BigDecimal.valueOf(orderAmount.getCent())
                .multiply(rate)
                .add(BigDecimal.valueOf(fixedMinor))
                .setScale(0, rounding)
                .longValueExact();
        long fee = Math.max(minimumMinor, rounded);
        if (maximumMinor != null) {
            fee = Math.min(maximumMinor, fee);
        }
        return DomainChecks.cnyAmount(fee, orderAmount.getCurrencyValue());
    }

    public long refundableFee(long originalFee, long orderAmount, long cumulativeRefund) {
        require(
                originalFee >= 0 && orderAmount > 0 && cumulativeRefund >= 0 && cumulativeRefund <= orderAmount,
                INVALID_ARGUMENT);
        return switch (refundPolicy) {
            case NO_RETURN -> 0;
            case FULL_ON_FULL_REFUND -> cumulativeRefund == orderAmount ? originalFee : 0;
            case PRO_RATA_CUMULATIVE -> BigDecimal.valueOf(originalFee)
                    .multiply(BigDecimal.valueOf(cumulativeRefund))
                    .divide(BigDecimal.valueOf(orderAmount), 0, rounding)
                    .longValueExact();
        };
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
