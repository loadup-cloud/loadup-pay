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

import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.pay.common.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.pay.common.DomainViolation.require;

/**
 * A single serial child transition and its exact capacity/request effects.
 */
public record RefundProgressChange(RefundExecutionFacts before, RefundOrder refund, RefundRequest beforeRequest,
                                   RefundRequest request,
                                   PaymentRefundCapacity beforeCapacity, PaymentRefundCapacity capacity,
                                   LocalDateTime submissionStartedAt,
                                   String channelRefundTransactionNo, LocalDateTime succeededAt) {
    public RefundProgressChange {
        Objects.requireNonNull(before);
        Objects.requireNonNull(refund);
        Objects.requireNonNull(beforeRequest);
        Objects.requireNonNull(request);
        Objects.requireNonNull(beforeCapacity);
        Objects.requireNonNull(capacity);
        Objects.requireNonNull(submissionStartedAt);
        var current = before.child().refund();
        require(!current.terminal() && refund.version() == Math.incrementExact(current.version())
                && beforeRequest.children().contains(current) && beforeRequest.apply(refund).equals(request), CONFLICT);
        require(beforeCapacity.paymentId().equals(current.paymentId()) && beforeCapacity.orderId().equals(beforeRequest.orderId())
                && beforeCapacity.payeeMerchantId().equals(beforeRequest.payeeMerchantId())
                && beforeCapacity.succeededAt().equals(before.child().originalSucceededAt())
                && beforeCapacity.succeededAmount().getCurrencyValue().equals(current.amount().getCurrencyValue())
                && beforeCapacity.reservedMinor() >= current.amount().getCent(), CONFLICT);
        require(before.submissionStartedAt() == null || before.submissionStartedAt().equals(submissionStartedAt), CONFLICT);
        if (before.submissionStartedAt() == null) require(refund.equals(current.beginSubmission()), CONFLICT);
        else {
            var kind = switch (refund.state()) {
                case PROCESSING -> ChannelRefundOutcome.Kind.ACCEPTED;
                case UNKNOWN -> ChannelRefundOutcome.Kind.UNKNOWN;
                case SUCCEEDED -> ChannelRefundOutcome.Kind.SUCCEEDED;
                case FAILED -> ChannelRefundOutcome.Kind.FAILED_WITH_NO_REFUND;
                case INIT -> throw new io.github.loadup.pay.common.DomainViolation(CONFLICT);
            };
            require(current.recordOutcome(new ChannelRefundOutcome(current.id(), kind, refund.amount(), refund.terminalEvidenceId())).equals(refund), CONFLICT);
        }
        var expected = !refund.terminal() ? beforeCapacity : beforeCapacity.applyOutcome(current, new ChannelRefundOutcome(current.id(),
                refund.state() == RefundState.SUCCEEDED ? ChannelRefundOutcome.Kind.SUCCEEDED : ChannelRefundOutcome.Kind.FAILED_WITH_NO_REFUND,
                refund.amount(), refund.terminalEvidenceId())).capacity();
        require(capacity.equals(expected), CONFLICT);
        if (refund.state() == RefundState.SUCCEEDED) {
            io.github.loadup.pay.common.DomainChecks.text(channelRefundTransactionNo, 128);
            Objects.requireNonNull(succeededAt);
            require(!succeededAt.isBefore(before.child().originalSucceededAt()), CONFLICT);
        } else require(channelRefundTransactionNo == null && succeededAt == null, CONFLICT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
