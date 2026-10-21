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
package io.github.loadup.pay.product.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.money.Money;
import io.github.loadup.pay.common.DomainChecks;

import java.time.LocalDateTime;

import static io.github.loadup.pay.common.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.pay.common.DomainViolation.require;

/** Every accepted command field is frozen independently of a caller-supplied digest. */
public record AdmissionIntent(
        String orderId,
        String payeeMerchantId,
        String productScopeKey,
        String requestDigest,
        Money amount,
        LocalDateTime requestedDueAt,
        String callerScopeHash) {
    public AdmissionIntent {
        DomainChecks.id(orderId);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.text(productScopeKey, 128);
        require(requestDigest != null && requestDigest.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
        require(callerScopeHash != null && callerScopeHash.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
        DomainChecks.amount(amount);
        require(amount.getCent() > 0, INVALID_ARGUMENT);
        require(requestedDueAt == null || requestedDueAt.getNano() % 1000 == 0, INVALID_ARGUMENT);
    }

    public void requireSame(AdmissionIntent other) {
        require(equals(other), CONFLICT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
