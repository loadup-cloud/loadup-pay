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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.pay.common.DomainViolation.require;

/**
 * Verified, account-bound adapter facts only. No HTTP binding or merchant-supplied success evidence.
 */
public record RefundObservation(String refundId, ChannelPaymentIdentity original, String channelRefundNo,
                                String originalChannelTransactionNo, ChannelRefundOutcome.Kind kind, Money amount,
                                String channelRefundTransactionNo, LocalDateTime succeededAt, Source source,
                                String receiptKey, String sourceDigest) {
    public RefundObservation {
        DomainChecks.id(refundId);
        Objects.requireNonNull(original);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(source);
        DomainChecks.text(channelRefundNo, 64);
        DomainChecks.text(originalChannelTransactionNo, 128);
        DomainChecks.amount(amount);
        require(amount.getCent() > 0, INVALID_ARGUMENT);
        DomainChecks.text(receiptKey, 128);
        require(sourceDigest != null && sourceDigest.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
        if (kind == ChannelRefundOutcome.Kind.SUCCEEDED) {
            DomainChecks.text(channelRefundTransactionNo, 128);
            Objects.requireNonNull(succeededAt);
            succeededAt = succeededAt.truncatedTo(ChronoUnit.MICROS);
        } else require(channelRefundTransactionNo == null && succeededAt == null, INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }

    public enum Source {SUBMIT_RESPONSE, QUERY_RESPONSE, CALLBACK}
}
