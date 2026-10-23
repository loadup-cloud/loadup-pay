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
import io.github.loadup.pay.common.DomainChecks;

import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.pay.common.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.pay.common.DomainViolation.require;

/**
 * Immutable terminal fact committed with the order and its original allocation.
 */
public record PaymentOutcomeReceipt(
        String id, String allocationId, VerifiedPaymentOutcome outcome, LocalDateTime observedAt) {
    public PaymentOutcomeReceipt {
        DomainChecks.id(id);
        DomainChecks.id(allocationId);
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(observedAt);
        require(outcome.kind() != VerifiedPaymentOutcome.Kind.UNKNOWN, INVALID_ARGUMENT);
    }

    public void requireBoundTo(OrderPaymentAllocation allocation) {
        require(allocationId.equals(allocation.id())
                && outcome.orderId().equals(allocation.orderId())
                && outcome.paymentId().equals(allocation.paymentId())
                && outcome.amount().equals(allocation.amount())
                && outcome.paymentVersion() == allocation.paymentSourceVersion()
                && allocation.state() == (outcome.kind() == VerifiedPaymentOutcome.Kind.SUCCEEDED
                ? AllocationState.CONSUMED : AllocationState.RELEASED), CONFLICT);
    }

    public void requireSameOutcome(VerifiedPaymentOutcome observed) {
        require(outcome.equals(observed), CONFLICT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
