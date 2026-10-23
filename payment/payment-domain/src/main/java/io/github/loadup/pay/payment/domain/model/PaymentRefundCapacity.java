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
import io.github.loadup.commons.validation.DomainChecks;

import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.AMOUNT_EXCEEDED;
import static io.github.loadup.commons.error.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

public record PaymentRefundCapacity(
        String paymentId,
        String orderId,
        String payeeMerchantId,
        LocalDateTime succeededAt,
        Money succeededAmount,
        long refundedMinor,
        long reservedMinor,
        long version) {
    public PaymentRefundCapacity {
        DomainChecks.id(paymentId);
        DomainChecks.id(orderId);
        DomainChecks.id(payeeMerchantId);
        Objects.requireNonNull(succeededAt);
        DomainChecks.cnyAmount(succeededAmount);
        require(
                succeededAmount.getCent() > 0
                        && refundedMinor >= 0
                        && reservedMinor >= 0
                        && refundedMinor <= succeededAmount.getCent()
                        && reservedMinor <= succeededAmount.getCent() - refundedMinor
                        && version >= 0,
                INVALID_ARGUMENT);
    }

    public long availableMinor() {
        return succeededAmount.getCent() - refundedMinor - reservedMinor;
    }

    public PaymentRefundCapacity reserve(long minor) {
        require(minor > 0 && minor <= availableMinor(), AMOUNT_EXCEEDED);
        return new PaymentRefundCapacity(
                paymentId,
                orderId,
                payeeMerchantId,
                succeededAt,
                succeededAmount,
                refundedMinor,
                Math.addExact(reservedMinor, minor),
                Math.incrementExact(version));
    }

    /**
     * Call with the locked persisted child, then commit the child/request/capacity together.
     */
    public RefundCapacityChange applyOutcome(RefundOrder current, ChannelRefundOutcome outcome) {
        require(
                paymentId.equals(current.paymentId())
                        && succeededAmount
                        .getCurrencyValue()
                        .equals(current.amount().getCurrencyValue()),
                CONFLICT);
        var updated = current.recordOutcome(outcome);
        if (!current.terminal()) require(current.amount().getCent() <= reservedMinor, CONFLICT);
        if (current.terminal() || !updated.terminal()) return new RefundCapacityChange(this, updated);
        long minor = current.amount().getCent();
        require(reservedMinor >= minor, CONFLICT);
        var next = new PaymentRefundCapacity(
                paymentId,
                orderId,
                payeeMerchantId,
                succeededAt,
                succeededAmount,
                updated.state() == RefundState.SUCCEEDED ? Math.addExact(refundedMinor, minor) : refundedMinor,
                reservedMinor - minor,
                Math.incrementExact(version));
        return new RefundCapacityChange(next, updated);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
