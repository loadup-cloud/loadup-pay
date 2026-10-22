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
package io.github.loadup.pay.acquire.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.money.Money;
import io.github.loadup.pay.common.DomainChecks;

import java.util.Objects;

import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.pay.common.DomainViolation.require;

/** Only trusted, persisted payment facts may be adapted to this value. */
public record VerifiedPaymentOutcome(
        String orderId,
        String paymentId,
        Money amount,
        Kind kind,
        long paymentVersion,
        String evidenceId,
        boolean executionFenced) {
    public enum Kind {
        SUCCEEDED,
        NO_COLLECTION,
        NOT_ACCEPTED,
        UNKNOWN
    }

    public VerifiedPaymentOutcome {
        DomainChecks.id(orderId);
        DomainChecks.id(paymentId);
        DomainChecks.amount(amount);
        Objects.requireNonNull(kind);
        // A tombstone has no payment row or invented payment version.
        require(kind == Kind.NOT_ACCEPTED ? paymentVersion == 0 : paymentVersion > 0, INVALID_ARGUMENT);
        if (kind != Kind.UNKNOWN) DomainChecks.id(evidenceId);
        if (kind == Kind.NO_COLLECTION || kind == Kind.NOT_ACCEPTED) require(executionFenced, INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
