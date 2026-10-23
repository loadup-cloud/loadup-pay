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
package io.github.loadup.pay.acquire.app.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.commons.error.DomainViolation;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Actual persisted header metadata, including original audit creation time, read inside the order transaction.
 */
public record OrderSnapshotFacts(String orderId, String payeeMerchantId, String merchantOrderNo, Money amount,
                                 long paidMinor, long reservedMinor, String state, LocalDateTime orderCreatedAt,
                                 LocalDateTime sourceUpdatedAt,
                                 long sourceVersion) {
    public OrderSnapshotFacts {
        DomainChecks.id(orderId);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.text(merchantOrderNo, 64);
        DomainChecks.cnyAmount(amount);
        Objects.requireNonNull(orderCreatedAt);
        Objects.requireNonNull(sourceUpdatedAt);
        DomainViolation.require(amount.getCent() > 0 && sourceVersion >= 0 && paidMinor >= 0 && reservedMinor >= 0
                        && paidMinor <= amount.getCent() && reservedMinor <= amount.getCent() - paidMinor,
                DomainViolation.Reason.INVALID_ARGUMENT);
        DomainChecks.text(state, 24);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
