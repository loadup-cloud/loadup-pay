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
package io.github.loadup.pay.realtime.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.AccessMode;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.validation.DomainChecks;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_STATE;
import static io.github.loadup.commons.error.DomainViolation.require;

public record TradePaymentProjection(
        String paymentId,
        String orderId,
        String payeeMerchantId,
        Money amount,
        String state,
        Channel channel,
        AccessMode accessMode,
        LocalDateTime initiatedAt,
        LocalDateTime succeededAt,
        SourceVersion source,
        LocalDateTime snapshotAt) {
    public TradePaymentProjection {
        DomainChecks.id(paymentId);
        DomainChecks.id(orderId);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.cnyAmount(amount);
        Objects.requireNonNull(source);
        Objects.requireNonNull(snapshotAt);
        Objects.requireNonNull(initiatedAt);
        require("SUCCEEDED".equals(state) == (succeededAt != null), INVALID_STATE);
        if (succeededAt != null) {
            require(!succeededAt.isBefore(initiatedAt), INVALID_ARGUMENT);
        }
        require(
                amount.getCent() > 0
                        && state != null
                        && Set.of("INIT", "PROCESSING", "UNKNOWN", "CLOSING", "SUCCEEDED", "FAILED", "CLOSED")
                        .contains(state),
                INVALID_ARGUMENT);
        require((channel == null) == (accessMode == null), INVALID_ARGUMENT);
        require(
                paymentId.equals(source.aggregateId())
                        && "payment".equals(source.producer())
                        && "PaymentOrder".equals(source.aggregateType()),
                INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
