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

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;

import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Immutable acknowledged no-collection close, saved before applying financial state in a separate transaction.
 */
public record PaymentCloseEvidence(ChannelPaymentIdentity identity, Money amount,
                                   @DiagnosticHidden String channelTransactionNo, String receiptKey,
                                   String sourceDigest,
                                   long executionEpoch, LocalDateTime recordedAt) {
    public PaymentCloseEvidence {
        Objects.requireNonNull(identity);
        DomainChecks.cnyAmount(amount);
        require(amount.getCent() > 0 && executionEpoch > 0, INVALID_ARGUMENT);
        if (channelTransactionNo != null) {
            DomainChecks.text(channelTransactionNo, 128);
        }
        DomainChecks.text(receiptKey, 128);
        require(sourceDigest != null && sourceDigest.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
        require(recordedAt != null && recordedAt.getNano() % 1000 == 0, INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
