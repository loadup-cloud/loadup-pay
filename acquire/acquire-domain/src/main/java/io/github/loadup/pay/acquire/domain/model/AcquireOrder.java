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

import io.github.loadup.pay.common.*;
import java.time.*;
import java.util.*;
import static io.github.loadup.pay.common.DomainViolation.Reason.*;
import static io.github.loadup.pay.common.DomainViolation.require;

/** Gross successful receipts are immutable with respect to independent refunds. */
public record AcquireOrder(
        String id,
        String payeeMerchantId,
        Money amount,
        long paidAmountMinor,
        long paymentReservedMinor,
        int successfulPaymentCount,
        int reservedPaymentCount,
        AcquireOrderState state,
        LocalDateTime orderDueAt,
        UnpaidOrderAction unpaidAction,
        OrderPaymentPolicy paymentPolicy,
        long closeEpoch,
        long version) {
    public AcquireOrder {
        DomainChecks.id(id);
        DomainChecks.id(payeeMerchantId);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(state);
        Objects.requireNonNull(orderDueAt);
        Objects.requireNonNull(unpaidAction);
        Objects.requireNonNull(paymentPolicy);
        require(
                amount.minor() > 0
                        && paidAmountMinor >= 0
                        && paymentReservedMinor >= 0
                        && paidAmountMinor <= amount.minor()
                        && paymentReservedMinor <= amount.minor() - paidAmountMinor
                        && successfulPaymentCount >= 0
                        && reservedPaymentCount >= 0
                        && closeEpoch >= 0
                        && version >= 0,
                INVALID_ARGUMENT);
        require((state == AcquireOrderState.PAID) == (paidAmountMinor == amount.minor()), INVALID_STATE);
        if (state == AcquireOrderState.CLOSED)
            require(paymentReservedMinor == 0 && reservedPaymentCount == 0, INVALID_STATE);
        if (state == AcquireOrderState.INIT)
            require(
                    paidAmountMinor == 0
                            && paymentReservedMinor == 0
                            && successfulPaymentCount == 0
                            && reservedPaymentCount == 0,
                    INVALID_STATE);
    }

    public static AcquireOrder init(
            String id,
            String payee,
            Money amount,
            LocalDateTime due,
            UnpaidOrderAction action,
            OrderPaymentPolicy policy) {
        return new AcquireOrder(id, payee, amount, 0, 0, 0, 0, AcquireOrderState.INIT, due, action, policy, 0, 0);
    }

    public long outstandingAmountMinor() {
        return amount.minor() - paidAmountMinor;
    }

    public long unallocatedAmountMinor() {
        return outstandingAmountMinor() - paymentReservedMinor;
    }

    public boolean canAcceptNewPayment(LocalDateTime now) {
        Objects.requireNonNull(now);
        return (state == AcquireOrderState.INIT || state == AcquireOrderState.PENDING)
                && (unpaidAction == UnpaidOrderAction.KEEP_PENDING || now.isBefore(orderDueAt));
    }

    public long availableToPayMinor(LocalDateTime now) {
        return canAcceptNewPayment(now) ? unallocatedAmountMinor() : 0;
    }

    public OrderPaymentChange reservePayment(PaymentIntent intent, LocalDateTime now) {
        Objects.requireNonNull(intent);
        require(canAcceptNewPayment(now), INVALID_STATE);
        amount.sameCurrency(intent.amount());
        paymentPolicy.validate(
                intent.amount(), unallocatedAmountMinor(), (long) successfulPaymentCount + reservedPaymentCount);
        var allocation = OrderPaymentAllocation.init(id, intent).hold();
        var next = new AcquireOrder(
                id,
                payeeMerchantId,
                amount,
                paidAmountMinor,
                Math.addExact(paymentReservedMinor, intent.amount().minor()),
                successfulPaymentCount,
                Math.incrementExact(reservedPaymentCount),
                AcquireOrderState.PENDING,
                orderDueAt,
                unpaidAction,
                paymentPolicy,
                closeEpoch,
                Math.incrementExact(version));
        return new OrderPaymentChange(next, allocation);
    }

    public OrderPaymentChange applyPaymentOutcome(OrderPaymentAllocation current, VerifiedPaymentOutcome outcome) {
        require(
                id.equals(current.orderId())
                        && id.equals(outcome.orderId())
                        && current.paymentId().equals(outcome.paymentId())
                        && current.amount().equals(outcome.amount()),
                CONFLICT);
        if (outcome.paymentVersion() <= current.paymentSourceVersion()
                || outcome.kind() == VerifiedPaymentOutcome.Kind.UNKNOWN) return new OrderPaymentChange(this, current);
        var target = outcome.kind() == VerifiedPaymentOutcome.Kind.SUCCEEDED
                ? AllocationState.CONSUMED
                : AllocationState.RELEASED;
        if (current.state() == target) return new OrderPaymentChange(this, current);
        require(
                current.state() == AllocationState.HELD
                        && state != AcquireOrderState.PAID
                        && state != AcquireOrderState.CLOSED,
                CONFLICT);
        long paid = target == AllocationState.CONSUMED
                ? Math.addExact(paidAmountMinor, current.amount().minor())
                : paidAmountMinor;
        long held = Math.subtractExact(paymentReservedMinor, current.amount().minor());
        require(held >= 0 && reservedPaymentCount > 0, CONFLICT);
        var nextState = paid == amount.minor() ? AcquireOrderState.PAID : state;
        var next = new AcquireOrder(
                id,
                payeeMerchantId,
                amount,
                paid,
                held,
                target == AllocationState.CONSUMED
                        ? Math.incrementExact(successfulPaymentCount)
                        : successfulPaymentCount,
                reservedPaymentCount - 1,
                nextState,
                orderDueAt,
                unpaidAction,
                paymentPolicy,
                closeEpoch,
                Math.incrementExact(version));
        return new OrderPaymentChange(next, current.transition(target, outcome.paymentVersion()));
    }

    public AcquireOrder requestClose() {
        if (state == AcquireOrderState.CLOSING) return this;
        require(state == AcquireOrderState.INIT || state == AcquireOrderState.PENDING, INVALID_STATE);
        return new AcquireOrder(
                id,
                payeeMerchantId,
                amount,
                paidAmountMinor,
                paymentReservedMinor,
                successfulPaymentCount,
                reservedPaymentCount,
                AcquireOrderState.CLOSING,
                orderDueAt,
                unpaidAction,
                paymentPolicy,
                Math.incrementExact(closeEpoch),
                Math.incrementExact(version));
    }

    public AcquireOrder finishClose(ParentCloseEvidence evidence) {
        Objects.requireNonNull(evidence);
        require(id.equals(evidence.orderId()) && closeEpoch == evidence.closeEpoch(), CONFLICT);
        if (state == AcquireOrderState.CLOSED) return this;
        require(
                state == AcquireOrderState.CLOSING && paymentReservedMinor == 0 && reservedPaymentCount == 0,
                OUTCOME_UNKNOWN);
        return new AcquireOrder(
                id,
                payeeMerchantId,
                amount,
                paidAmountMinor,
                0,
                successfulPaymentCount,
                0,
                AcquireOrderState.CLOSED,
                orderDueAt,
                unpaidAction,
                paymentPolicy,
                closeEpoch,
                Math.incrementExact(version));
    }

    /** A due-time check never silently stops a KEEP_PENDING order from accepting payments. */
    public AcquireOrder checkDue(LocalDateTime now) {
        Objects.requireNonNull(now);
        if (now.isBefore(orderDueAt) || state != AcquireOrderState.INIT && state != AcquireOrderState.PENDING)
            return this;
        if (unpaidAction == UnpaidOrderAction.CLOSE) return requestClose();
        if (state == AcquireOrderState.PENDING) return this;
        return new AcquireOrder(id, payeeMerchantId, amount, paidAmountMinor, paymentReservedMinor,
                successfulPaymentCount, reservedPaymentCount, AcquireOrderState.PENDING, orderDueAt,
                unpaidAction, paymentPolicy, closeEpoch, Math.incrementExact(version));
    }

    /** The deadline is derived from the original due time and the frozen cumulative extension allowance. */
    public AcquireOrder extend(LocalDateTime requestedDueAt, LocalDateTime originalDueAt,
            boolean extensionAllowed, long extensionMaxSeconds, LocalDateTime now) {
        Objects.requireNonNull(requestedDueAt);
        Objects.requireNonNull(originalDueAt);
        require(extensionMaxSeconds >= 0 && !originalDueAt.isAfter(orderDueAt), INVALID_ARGUMENT);
        require(canAcceptNewPayment(now) && extensionAllowed, INVALID_STATE);
        require(requestedDueAt.isAfter(orderDueAt) && requestedDueAt.isAfter(now)
                && !requestedDueAt.isAfter(originalDueAt.plusSeconds(extensionMaxSeconds)), INVALID_ARGUMENT);
        return new AcquireOrder(id, payeeMerchantId, amount, paidAmountMinor, paymentReservedMinor,
                successfulPaymentCount, reservedPaymentCount, state, requestedDueAt, unpaidAction,
                paymentPolicy, closeEpoch, Math.incrementExact(version));
    }
}
