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

import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.commons.error.DomainViolation.Reason.CURRENCY_MISMATCH;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_STATE;
import static io.github.loadup.commons.error.DomainViolation.require;

public record OrderFee(
        String id,
        String orderId,
        Money baseAmount,
        Money feeAmount,
        OrderFeePolicy policy,
        FeeState state,
        long refundBasisVersion,
        long refundBasisMinor,
        long adjustedMinor) {
    public OrderFee {
        DomainChecks.id(id);
        DomainChecks.id(orderId);
        DomainChecks.cnyAmount(baseAmount);
        DomainChecks.cnyAmount(feeAmount);
        Objects.requireNonNull(policy);
        Objects.requireNonNull(state);
        require(baseAmount.getCurrencyValue().equals(feeAmount.getCurrencyValue()), CURRENCY_MISMATCH);
        require(
                policy.calculate(baseAmount).equals(feeAmount)
                        && refundBasisVersion >= 0
                        && refundBasisMinor >= 0
                        && refundBasisMinor <= baseAmount.getCent()
                        && adjustedMinor >= 0
                        && adjustedMinor <= feeAmount.getCent(),
                INVALID_ARGUMENT);
        require(state == FeeState.CONFIRMED || adjustedMinor == 0, INVALID_STATE);
        if (state == FeeState.CONFIRMED)
            require(
                    adjustedMinor <= policy.refundableFee(feeAmount.getCent(), baseAmount.getCent(), refundBasisMinor),
                    CONFLICT);
    }

    public static OrderFee init(String id, String orderId, Money base, OrderFeePolicy policy) {
        return new OrderFee(id, orderId, base, policy.calculate(base), policy, FeeState.INIT, 0, 0, 0);
    }

    public OrderFee confirm() {
        return state == FeeState.CONFIRMED
                ? this
                : new OrderFee(
                id,
                orderId,
                baseAmount,
                feeAmount,
                policy,
                FeeState.CONFIRMED,
                refundBasisVersion,
                refundBasisMinor,
                adjustedMinor);
    }

    public OrderFee recordRefundBasis(long sourceVersion, long cumulativeMinor) {
        require(sourceVersion >= 0 && cumulativeMinor >= 0 && cumulativeMinor <= baseAmount.getCent(), INVALID_ARGUMENT);
        if (sourceVersion == refundBasisVersion) {
            require(cumulativeMinor == refundBasisMinor, CONFLICT);
            return this;
        }
        if (sourceVersion < refundBasisVersion) {
            require(cumulativeMinor <= refundBasisMinor, CONFLICT);
            return this;
        }
        require(cumulativeMinor >= refundBasisMinor, CONFLICT);
        return new OrderFee(
                id, orderId, baseAmount, feeAmount, policy, state, sourceVersion, cumulativeMinor, adjustedMinor);
    }

    public long adjustmentDueMinor() {
        return state == FeeState.CONFIRMED
                ? policy.refundableFee(feeAmount.getCent(), baseAmount.getCent(), refundBasisMinor) - adjustedMinor
                : 0;
    }

    public OrderFee acknowledgeAdjustment() {
        return new OrderFee(
                id,
                orderId,
                baseAmount,
                feeAmount,
                policy,
                state,
                refundBasisVersion,
                refundBasisMinor,
                Math.addExact(adjustedMinor, adjustmentDueMinor()));
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
