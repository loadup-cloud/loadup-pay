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
package io.github.loadup.pay.channel.api.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.validation.DomainChecks;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Internal adapter facts, after signature/decryption and actual collecting-account verification.
 * NOT_FOUND, timeout and unauthenticated responses never prove NO_COLLECTION. No HTTP binding.
 */
public record PaymentObservation(ChannelPaymentIdentity identity, ChannelPaymentOutcome.Kind kind, Money amount,
                                 String channelTransactionNo, LocalDateTime succeededAt, Source source,
                                 String receiptKey, String sourceDigest) {
    public PaymentObservation {
        Objects.requireNonNull(identity);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(source);
        DomainChecks.cnyAmount(amount);
        require(amount.getCent() > 0, INVALID_ARGUMENT);
        DomainChecks.text(receiptKey, 128);
        require(sourceDigest != null && sourceDigest.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
        if (channelTransactionNo != null) {
            DomainChecks.text(channelTransactionNo, 128);
        }
        if (succeededAt != null) {
            succeededAt = succeededAt.truncatedTo(ChronoUnit.MICROS);
        }
        if (kind == ChannelPaymentOutcome.Kind.SUCCEEDED || kind == ChannelPaymentOutcome.Kind.CLOSED_WITH_FUNDS_RETURNED) {
            require(channelTransactionNo != null && succeededAt != null, INVALID_ARGUMENT);
        } else {
            require(succeededAt == null, INVALID_ARGUMENT);
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }

    public enum Source {SUBMIT_RESPONSE, QUERY_RESPONSE, CLOSE_RESPONSE, CALLBACK}
}
