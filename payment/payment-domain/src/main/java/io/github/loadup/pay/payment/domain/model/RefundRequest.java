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

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.money.Money;
import io.github.loadup.pay.common.*;
import java.time.*;
import java.util.*;
import static io.github.loadup.pay.common.DomainViolation.Reason.*;
import static io.github.loadup.pay.common.DomainViolation.require;

public record RefundRequest(
        String id,
        String orderId,
        String payeeMerchantId,
        String merchantRefundNo,
        Money amount,
        int planVersion,
        List<RefundOrder> children,
        RefundRequestState state,
        long version) {
    public RefundRequest {
        DomainChecks.id(id);
        DomainChecks.id(orderId);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.text(merchantRefundNo, 64);
        DomainChecks.amount(amount);
        Objects.requireNonNull(state);
        children = List.copyOf(children);
        require(amount.getCent() > 0 && planVersion > 0 && version >= 0 && !children.isEmpty(), INVALID_ARGUMENT);
        long total = 0;
        Set<String> ids = new HashSet<>();
        Set<String> payments = new HashSet<>();
        boolean unfinishedSeen = false;
        for (var child : children) {
            require(
                    id.equals(child.refundRequestId())
                            && amount.getCurrencyValue().equals(child.amount().getCurrencyValue())
                            && ids.add(child.id()) && payments.add(child.paymentId()),
                    CONFLICT);
            if (unfinishedSeen) require(child.state() == RefundState.INIT, INVALID_STATE);
            if (!child.terminal()) unfinishedSeen = true;
            total = Math.addExact(total, child.amount().getCent());
        }
        require(total == amount.getCent(), CONFLICT);
        if (state == RefundRequestState.PROCESSING)
            require(children.stream().anyMatch(c -> !c.terminal())
                    && children.stream().anyMatch(c -> c.state() != RefundState.INIT), INVALID_STATE);
        if (state == RefundRequestState.INIT)
            require(children.stream().allMatch(c -> c.state() == RefundState.INIT), INVALID_STATE);
        if (state == RefundRequestState.SUCCEEDED)
            require(children.stream().allMatch(c -> c.state() == RefundState.SUCCEEDED), INVALID_STATE);
        if (state == RefundRequestState.FAILED)
            require(children.stream().allMatch(c -> c.state() == RefundState.FAILED), INVALID_STATE);
        if (state == RefundRequestState.PARTIAL_SUCCEEDED)
            require(
                    children.stream().allMatch(RefundOrder::terminal)
                            && children.stream().anyMatch(c -> c.state() == RefundState.SUCCEEDED)
                            && children.stream().anyMatch(c -> c.state() == RefundState.FAILED),
                    INVALID_STATE);
    }

    public static RefundRequest init(
            String id,
            String orderId,
            String payee,
            String merchantRefundNo,
            Money amount,
            List<RefundOrder> children) {
        return new RefundRequest(id, orderId, payee, merchantRefundNo, amount, 1, children, RefundRequestState.INIT, 0);
    }

    public long succeededMinor() {
        return children.stream()
                .filter(c -> c.state() == RefundState.SUCCEEDED)
                .mapToLong(c -> c.amount().getCent())
                .reduce(0, Math::addExact);
    }

    public long failedMinor() {
        return children.stream()
                .filter(c -> c.state() == RefundState.FAILED)
                .mapToLong(c -> c.amount().getCent())
                .reduce(0, Math::addExact);
    }

    public long unknownCount() {
        return children.stream().filter(c -> c.state() == RefundState.UNKNOWN).count();
    }

    /** List order is the frozen sequenceNo order; an unknown or running child blocks later children. */
    public Optional<RefundOrder> nextExecutable() {
        return children.stream().filter(c -> !c.terminal()).findFirst()
                .filter(c -> c.state() == RefundState.INIT);
    }

    public RefundRequest apply(RefundOrder updated) {
        require(id.equals(updated.refundRequestId()), CONFLICT);
        var current = children.stream()
                .filter(c -> c.id().equals(updated.id()))
                .findFirst()
                .orElseThrow(() -> new DomainViolation(CONFLICT));
        require(
                current.paymentId().equals(updated.paymentId())
                        && current.amount().equals(updated.amount())
                        && current.channelRefundNo().equals(updated.channelRefundNo()),
                CONFLICT);
        if (updated.version() < current.version()) return this;
        if (updated.version() == current.version()) {
            require(current.equals(updated), CONFLICT);
            return this;
        }
        require(!current.terminal(), CONFLICT);
        require(updated.state() != RefundState.INIT, INVALID_STATE);
        for (var child : children) {
            if (child.id().equals(updated.id())) break;
            require(child.terminal(), OUTCOME_UNKNOWN);
        }
        var next = children.stream()
                .map(c -> c.id().equals(updated.id()) ? updated : c)
                .toList();
        boolean complete = next.stream().allMatch(RefundOrder::terminal);
        var nextState = !complete
                ? RefundRequestState.PROCESSING
                : next.stream().allMatch(c -> c.state() == RefundState.SUCCEEDED)
                        ? RefundRequestState.SUCCEEDED
                        : next.stream().allMatch(c -> c.state() == RefundState.FAILED)
                                ? RefundRequestState.FAILED
                                : RefundRequestState.PARTIAL_SUCCEEDED;
        return new RefundRequest(
                id,
                orderId,
                payeeMerchantId,
                merchantRefundNo,
                amount,
                planVersion,
                next,
                nextState,
                Math.incrementExact(version));
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
