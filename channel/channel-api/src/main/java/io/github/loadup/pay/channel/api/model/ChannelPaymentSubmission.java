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

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.validation.DomainChecks;
import java.util.Objects;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/** Separates verified channel observations from client actions; a locally signed action may have no observation. */
public record ChannelPaymentSubmission(ChannelPaymentIdentity identity, Money amount, PaymentObservation observation,
        @DiagnosticHidden ChannelPaymentAction action) {
    public ChannelPaymentSubmission {
        Objects.requireNonNull(identity);
        DomainChecks.cnyAmount(amount);
        require(amount.getCent() > 0 && (observation != null || action != null), INVALID_ARGUMENT);
        if (observation != null) {
            require(identity.equals(observation.identity()) && amount.equals(observation.amount()), INVALID_ARGUMENT);
            if (action != null) require(observation.kind() == ChannelPaymentOutcome.Kind.ACCEPTED, INVALID_ARGUMENT);
        }
        if (action != null) require(action.channel() == identity.channel(), INVALID_ARGUMENT);
        require((observation == null) == (action instanceof AlipayH5ChannelAction), INVALID_ARGUMENT);
        if (action instanceof AlipayJsapiChannelAction alipay) require(observation != null
                && alipay.tradeNo().equals(observation.channelTransactionNo()), INVALID_ARGUMENT);
    }

    @Override
    public String toString() { return ToStringAsJson.reflectionToString(this); }
}

