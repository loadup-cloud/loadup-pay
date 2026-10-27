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
import io.github.loadup.pay.channel.api.model.ChannelPaymentAction;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/** Immutable first issued action per original attempt; receipts and successful amounts remain independent. */
public record StoredPaymentAction(ChannelPaymentIdentity identity, Money amount, long executionEpoch,
        @DiagnosticHidden ChannelPaymentAction action, PaymentActionSource source, String receiptId, String sourceDigest, LocalDateTime issuedAt) {
    public StoredPaymentAction {
        Objects.requireNonNull(identity);
        DomainChecks.cnyAmount(amount);
        Objects.requireNonNull(action);
        require(amount.getCent() > 0 && executionEpoch > 0 && action.channel() == identity.channel(), INVALID_ARGUMENT);
        Objects.requireNonNull(source);
        require((source == PaymentActionSource.LOCAL_SIGNED)
                == (action instanceof io.github.loadup.pay.channel.api.model.AlipayH5ChannelAction), INVALID_ARGUMENT);
        if (source == PaymentActionSource.CHANNEL_RECEIPT) DomainChecks.id(receiptId);
        else require(receiptId == null && action instanceof io.github.loadup.pay.channel.api.model.AlipayH5ChannelAction,
                INVALID_ARGUMENT);
        require(sourceDigest != null && sourceDigest.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
        Objects.requireNonNull(issuedAt);
        issuedAt = issuedAt.truncatedTo(ChronoUnit.MICROS);
    }

    @Override
    public String toString() { return ToStringAsJson.reflectionToString(this); }
}

