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

import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Frozen original collecting identity; a refund never selects a new payment route.
 */
public record StoredRefundChild(RefundOrder refund, int sequenceNo, ChannelPaymentIdentity original,
                                String channelTransactionNo, LocalDateTime originalSucceededAt) {
    public StoredRefundChild {
        Objects.requireNonNull(refund);
        Objects.requireNonNull(original);
        Objects.requireNonNull(originalSucceededAt);
        DomainChecks.text(channelTransactionNo, 128);
        require(sequenceNo > 0 && refund.paymentId().equals(original.paymentId()), CONFLICT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
