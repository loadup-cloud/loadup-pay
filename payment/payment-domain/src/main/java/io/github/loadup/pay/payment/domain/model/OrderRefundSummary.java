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
package io.github.loadup.pay.payment.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.money.Money;
import io.github.loadup.pay.common.DomainChecks;

import static io.github.loadup.pay.common.DomainViolation.Reason.AMOUNT_EXCEEDED;
import static io.github.loadup.pay.common.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.pay.common.DomainViolation.require;

/**
 * Order monetary aggregate, independent of order/payment lifecycle and individual request versions.
 */
public record OrderRefundSummary(String orderId, String payeeMerchantId, Money orderAmount, long refundedMinor,
                                 long reservedMinor, long version) {
    public OrderRefundSummary {
        DomainChecks.id(orderId);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.amount(orderAmount);
        require(orderAmount.getCent() > 0 && refundedMinor >= 0 && refundedMinor <= orderAmount.getCent()
                && reservedMinor >= 0 && reservedMinor <= orderAmount.getCent() - refundedMinor && version >= 0, INVALID_ARGUMENT);
    }

    public static OrderRefundSummary init(String order, String merchant, Money amount) {
        return new OrderRefundSummary(order, merchant, amount, 0, 0, 0);
    }

    public OrderRefundSummary reserve(long amount) {
        require(amount > 0 && amount <= orderAmount.getCent() - refundedMinor - reservedMinor, AMOUNT_EXCEEDED);
        return new OrderRefundSummary(orderId, payeeMerchantId, orderAmount, refundedMinor, reservedMinor + amount, Math.incrementExact(version));
    }

    public OrderRefundSummary settle(long amount, boolean succeeded) {
        require(amount > 0 && amount <= reservedMinor, CONFLICT);
        return new OrderRefundSummary(orderId, payeeMerchantId, orderAmount, succeeded ? Math.addExact(refundedMinor, amount) : refundedMinor,
                reservedMinor - amount, Math.incrementExact(version));
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
