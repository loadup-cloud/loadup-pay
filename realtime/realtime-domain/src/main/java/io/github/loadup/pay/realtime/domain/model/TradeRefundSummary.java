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

public record TradeRefundSummary(
        String orderId, String payeeMerchantId, String currency, long refundedMinor, long reservedMinor,
        SourceVersion source, LocalDateTime snapshotAt) {
    public TradeRefundSummary {
        DomainChecks.id(orderId);
        DomainChecks.id(payeeMerchantId);
        require("CNY".equals(currency), INVALID_ARGUMENT);
        Objects.requireNonNull(source);
        Objects.requireNonNull(snapshotAt);
        require(
                refundedMinor >= 0
                        && reservedMinor >= 0
                        && orderId.equals(source.aggregateId())
                        && "payment".equals(source.producer())
                        && "OrderRefundSummary".equals(source.aggregateType()),
                INVALID_ARGUMENT);
    }
}
