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
import io.github.loadup.pay.common.DomainChecks;

import static io.github.loadup.pay.common.DomainViolation.Reason.AMOUNT_EXCEEDED;
import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.pay.common.DomainViolation.require;

public record OrderPaymentPolicy(
        long minAmountMinor, long maxAmountMinor, Integer maxSuccessfulPayments, boolean allowExactRemainderBelowMin) {
    public OrderPaymentPolicy {
        require(
                minAmountMinor > 0
                        && maxAmountMinor >= minAmountMinor
                        && (maxSuccessfulPayments == null || maxSuccessfulPayments > 0),
                INVALID_ARGUMENT);
    }

    public void validate(Money payment, long available, long occupiedCount) {
        DomainChecks.amount(payment);
        require(
                payment.getCent() > 0 && payment.getCent() <= available && payment.getCent() <= maxAmountMinor,
                AMOUNT_EXCEEDED);
        require(
                payment.getCent() >= minAmountMinor || allowExactRemainderBelowMin && payment.getCent() == available,
                INVALID_ARGUMENT);
        require(maxSuccessfulPayments == null || occupiedCount < maxSuccessfulPayments, AMOUNT_EXCEEDED);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
