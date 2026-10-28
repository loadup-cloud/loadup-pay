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

import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Verified trigger metadata, never a financial outcome or a no-collection proof.
 */
public record VerifiedChannelNotification(ChannelPaymentIdentity identity, Money amount, String notificationId,
                                          String channelTransactionNo, String channelStatus, String sourceDigest) {
    public VerifiedChannelNotification {
        Objects.requireNonNull(identity);
        DomainChecks.cnyAmount(amount);
        require(amount.getCent() > 0, INVALID_ARGUMENT);
        DomainChecks.text(notificationId, 128);
        DomainChecks.text(channelTransactionNo, 128);
        DomainChecks.text(channelStatus, 64);
        require(sourceDigest != null && sourceDigest.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
