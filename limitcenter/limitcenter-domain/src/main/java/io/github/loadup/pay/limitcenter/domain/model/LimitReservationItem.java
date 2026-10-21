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
package io.github.loadup.pay.limitcenter.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.common.DomainChecks;

import java.util.Objects;

import static io.github.loadup.pay.common.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.pay.common.DomainViolation.Reason.DEPENDENCY_NOT_READY;
import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.pay.common.DomainViolation.require;

public record LimitReservationItem(
        String id, String reservationId, LimitBucketKey bucket, long reserved, long consumed, long restored) {
    public LimitReservationItem {
        DomainChecks.id(id);
        DomainChecks.id(reservationId);
        Objects.requireNonNull(bucket);
        require(
                reserved > 0 && consumed >= 0 && consumed <= reserved && restored >= 0 && restored <= consumed,
                INVALID_ARGUMENT);
    }

    public LimitReservationItem confirm() {
        if (consumed == reserved) return this;
        require(consumed == 0 && restored == 0, CONFLICT);
        return new LimitReservationItem(id, reservationId, bucket, reserved, reserved, 0);
    }

    /** The cumulative snapshot is a high-water mark, so duplicates and older refunds restore no extra quota. */
    public LimitItemRestore restoreRefund(
            LimitReservation reservation,
            long cumulativeRefundedMinor,
            RefundAmountRestorePolicy amountPolicy,
            RefundCountRestorePolicy countPolicy) {
        Objects.requireNonNull(amountPolicy);
        Objects.requireNonNull(countPolicy);
        require(
                reservationId.equals(reservation.id())
                        && bucket.currency().equals(reservation.amount().getCurrencyValue()),
                CONFLICT);
        require(reservation.state() == LimitReservationState.CONFIRMED && consumed == reserved, DEPENDENCY_NOT_READY);
        require(
                cumulativeRefundedMinor > 0
                        && cumulativeRefundedMinor <= reservation.amount().getCent(),
                INVALID_ARGUMENT);
        long target;
        if (bucket.metric() == LimitMetric.AMOUNT) {
            require(reserved == reservation.amount().getCent(), CONFLICT);
            target = amountPolicy == RefundAmountRestorePolicy.ORIGINAL_BUCKETS ? cumulativeRefundedMinor : 0;
        } else {
            require(reserved == 1, CONFLICT);
            target = countPolicy == RefundCountRestorePolicy.FULL_PAYMENT_REFUNDED_ONCE
                            && cumulativeRefundedMinor == reservation.amount().getCent()
                    ? 1
                    : 0;
        }
        long delta = Math.max(0, target - restored);
        return new LimitItemRestore(
                delta == 0 ? this : new LimitReservationItem(id, reservationId, bucket, reserved, consumed, target),
                delta);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
