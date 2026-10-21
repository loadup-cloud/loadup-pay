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

import io.github.loadup.pay.common.*;
import java.time.*;
import java.util.*;
import static io.github.loadup.pay.common.DomainViolation.Reason.*;
import static io.github.loadup.pay.common.DomainViolation.require;

public record TradeOrderProjection(
        String orderId,
        String payeeMerchantId,
        String merchantOrderNo,
        Money amount,
        long paidMinor,
        long reservedMinor,
        String state,
        SourceVersion source,
        LocalDateTime snapshotAt) {
    public TradeOrderProjection {
        DomainChecks.id(orderId);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.text(merchantOrderNo, 64);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(source);
        Objects.requireNonNull(snapshotAt);
        require(
                paidMinor >= 0
                        && reservedMinor >= 0
                        && paidMinor <= amount.minor()
                        && reservedMinor <= amount.minor() - paidMinor
                        && orderId.equals(source.aggregateId())
                        && "acquire".equals(source.producer())
                        && "AcquireOrder".equals(source.aggregateType()),
                INVALID_ARGUMENT);
        require(amount.minor() > 0 && state != null
                && Set.of("INIT", "PENDING", "CLOSING", "PAID", "CLOSED").contains(state), INVALID_ARGUMENT);
        require("PAID".equals(state) == (paidMinor == amount.minor())
                && (!"CLOSED".equals(state) || reservedMinor == 0), INVALID_ARGUMENT);
    }
}
