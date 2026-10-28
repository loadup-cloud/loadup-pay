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
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.commons.validation.DomainChecks;

import java.time.OffsetDateTime;
import java.util.Objects;

public record AlipayJsapiChannelAction(@DiagnosticHidden String tradeNo, OffsetDateTime expiresAt)
        implements ChannelPaymentAction {
    public AlipayJsapiChannelAction {
        DomainChecks.text(tradeNo, 64);
        Objects.requireNonNull(expiresAt);
        expiresAt = expiresAt.atZoneSameInstant(io.github.loadup.commons.time.BusinessTime.zone()).toOffsetDateTime()
                .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    @Override
    public Channel channel() {
        return Channel.ALIPAY;
    }

    @Override
    public PaymentMethod method() {
        return PaymentMethod.JSAPI;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}

