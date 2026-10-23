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
package io.github.loadup.pay.realtime.domain.model;

import java.util.Objects;
import java.util.Set;

public final class ProjectionFacts {
    private ProjectionFacts() {
    }

    public static boolean compatible(TradeOrderProjection old, TradeOrderProjection next) {
        if (!old.orderId().equals(next.orderId()) || !old.payeeMerchantId().equals(next.payeeMerchantId())
                || !old.merchantOrderNo().equals(next.merchantOrderNo()) || !old.amount().equals(next.amount())
                || !old.orderCreatedAt().equals(next.orderCreatedAt())) return false;
        return next.source().number() <= old.source().number() || !Set.of("PAID", "CLOSED").contains(old.state())
                || old.state().equals(next.state()) && old.paidMinor() == next.paidMinor() && old.reservedMinor() == next.reservedMinor();
    }

    public static boolean compatible(TradePaymentProjection old, TradePaymentProjection next) {
        if (!old.paymentId().equals(next.paymentId()) || !old.orderId().equals(next.orderId())
                || !old.payeeMerchantId().equals(next.payeeMerchantId()) || !old.amount().equals(next.amount())
                || !old.initiatedAt().equals(next.initiatedAt())) return false;
        return next.source().number() <= old.source().number() || !Set.of("SUCCEEDED", "FAILED", "CLOSED").contains(old.state())
                || old.state().equals(next.state()) && old.channel() == next.channel() && old.accessMode() == next.accessMode()
                && Objects.equals(old.succeededAt(), next.succeededAt());
    }

    public static boolean compatible(TradeRefundProjection old, TradeRefundProjection next) {
        if (!old.refundId().equals(next.refundId()) || !old.refundRequestId().equals(next.refundRequestId())
                || !old.paymentId().equals(next.paymentId()) || !old.orderId().equals(next.orderId())
                || !old.payeeMerchantId().equals(next.payeeMerchantId()) || !old.amount().equals(next.amount())
                || !old.refundCreatedAt().equals(next.refundCreatedAt())) return false;
        return next.source().number() <= old.source().number() || !Set.of("SUCCEEDED", "FAILED").contains(old.state())
                || old.state().equals(next.state()) && Objects.equals(old.succeededAt(), next.succeededAt());
    }

    public static boolean compatible(TradeRefundSummary old, TradeRefundSummary next) {
        if (!old.orderId().equals(next.orderId()) || !old.payeeMerchantId().equals(next.payeeMerchantId())
                || !old.currency().equals(next.currency())) return false;
        if (next.source().number() <= old.source().number()) return true;
        return next.refundedMinor() >= old.refundedMinor();
    }
}
