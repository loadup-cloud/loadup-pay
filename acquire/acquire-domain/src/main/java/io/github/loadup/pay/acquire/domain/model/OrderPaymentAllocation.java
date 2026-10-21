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

import static io.github.loadup.pay.common.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_STATE;
import static io.github.loadup.pay.common.DomainViolation.require;

public record OrderPaymentAllocation(
        String id,
        String orderId,
        String paymentId,
        String actorScope,
        String actionKey,
        String requestDigest,
        Money amount,
        AllocationState state,
        long paymentSourceVersion) {
    public OrderPaymentAllocation {
        DomainChecks.id(id);
        DomainChecks.id(orderId);
        DomainChecks.id(paymentId);
        DomainChecks.text(actorScope, 128);
        DomainChecks.text(actionKey, 128);
        DomainChecks.text(requestDigest, 64);
        DomainChecks.amount(amount);
        Objects.requireNonNull(state);
        require(amount.getCent() > 0 && paymentSourceVersion >= 0, INVALID_ARGUMENT);
    }

    public static OrderPaymentAllocation init(String orderId, PaymentIntent intent) {
        return new OrderPaymentAllocation(
                intent.allocationId(),
                orderId,
                intent.paymentId(),
                intent.actorScope(),
                intent.actionKey(),
                intent.requestDigest(),
                intent.amount(),
                AllocationState.INIT,
                0);
    }

    public OrderPaymentAllocation hold() {
        require(state == AllocationState.INIT, INVALID_STATE);
        return transition(AllocationState.HELD, 0);
    }

    /** Resolve an action-key replay before applying current order state or taking another amount hold. */
    public void requireSameAction(String expectedOrderId, PaymentIntent intent) {
        require(
                orderId.equals(expectedOrderId)
                        && actorScope.equals(intent.actorScope())
                        && actionKey.equals(intent.actionKey())
                        && requestDigest.equals(intent.requestDigest())
                        && amount.equals(intent.amount()),
                CONFLICT);
    }

    public OrderPaymentAllocation transition(AllocationState target, long sourceVersion) {
        require(
                state == AllocationState.HELD
                                && (target == AllocationState.CONSUMED || target == AllocationState.RELEASED)
                        || state == AllocationState.INIT && target == AllocationState.HELD,
                INVALID_STATE);
        return new OrderPaymentAllocation(
                id, orderId, paymentId, actorScope, actionKey, requestDigest, amount, target, sourceVersion);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
