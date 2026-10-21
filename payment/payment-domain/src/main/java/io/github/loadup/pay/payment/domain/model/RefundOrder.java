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

import io.github.loadup.pay.common.*;
import java.time.*;
import java.util.*;
import static io.github.loadup.pay.common.DomainViolation.Reason.*;
import static io.github.loadup.pay.common.DomainViolation.require;

public record RefundOrder(
        String id,
        String refundRequestId,
        String paymentId,
        Money amount,
        String channelRefundNo,
        RefundState state,
        String terminalEvidenceId,
        long version) {
    public RefundOrder {
        DomainChecks.id(id);
        DomainChecks.id(refundRequestId);
        DomainChecks.id(paymentId);
        DomainChecks.text(channelRefundNo, 64);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(state);
        require(amount.minor() > 0 && version >= 0, INVALID_ARGUMENT);
        if (state == RefundState.SUCCEEDED || state == RefundState.FAILED) DomainChecks.id(terminalEvidenceId);
    }

    public static RefundOrder init(String id, String request, String payment, Money amount, String channelRefundNo) {
        return new RefundOrder(id, request, payment, amount, channelRefundNo, RefundState.INIT, null, 0);
    }

    public boolean terminal() {
        return state == RefundState.SUCCEEDED || state == RefundState.FAILED;
    }

    public RefundOrder recordOutcome(ChannelRefundOutcome verified) {
        require(id.equals(verified.refundId()) && amount.equals(verified.amount()), CONFLICT);
        var outcome = switch (verified.kind()) {
            case ACCEPTED -> RefundState.PROCESSING;
            case SUCCEEDED -> RefundState.SUCCEEDED;
            case FAILED_WITH_NO_REFUND -> RefundState.FAILED;
            case UNKNOWN -> RefundState.UNKNOWN;
        };
        if (terminal()) {
            if (outcome == RefundState.PROCESSING || outcome == RefundState.UNKNOWN) return this;
            require(state == outcome, CONFLICT);
            return this;
        }
        if (state == outcome || state == RefundState.UNKNOWN && outcome == RefundState.PROCESSING) return this;
        return new RefundOrder(
                id,
                refundRequestId,
                paymentId,
                amount,
                channelRefundNo,
                outcome,
                verified.evidenceId(),
                Math.incrementExact(version));
    }
}
