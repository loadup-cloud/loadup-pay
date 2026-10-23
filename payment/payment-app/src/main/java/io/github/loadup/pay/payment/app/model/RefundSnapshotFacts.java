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
package io.github.loadup.pay.payment.app.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.money.Money;
import io.github.loadup.pay.common.DomainChecks;
import java.time.LocalDateTime;
import java.util.Objects;

public record RefundSnapshotFacts(String refundId, String refundRequestId, String paymentId, String orderId, String payeeMerchantId,
        Money amount, String state, LocalDateTime refundCreatedAt, LocalDateTime sourceUpdatedAt, LocalDateTime succeededAt, long sourceVersion) {
    public RefundSnapshotFacts {
        DomainChecks.id(refundId); DomainChecks.id(refundRequestId); DomainChecks.id(paymentId); DomainChecks.id(orderId); DomainChecks.id(payeeMerchantId);
        DomainChecks.amount(amount); DomainChecks.text(state, 24); Objects.requireNonNull(refundCreatedAt); Objects.requireNonNull(sourceUpdatedAt);
    }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
