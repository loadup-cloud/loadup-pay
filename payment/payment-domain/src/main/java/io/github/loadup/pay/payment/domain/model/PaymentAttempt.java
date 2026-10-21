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

public record PaymentAttempt(
        String id,
        String paymentId,
        int attemptNo,
        Money amount,
        ChannelBindingRevision binding,
        PaymentMethod method,
        String channelRequestNo,
        PaymentAttemptState state,
        long executionEpoch,
        String noCollectionEvidenceId) {
    public PaymentAttempt {
        DomainChecks.id(id);
        DomainChecks.id(paymentId);
        DomainChecks.text(channelRequestNo, 64);
        DomainChecks.amount(amount);
        Objects.requireNonNull(binding);
        Objects.requireNonNull(method);
        Objects.requireNonNull(state);
        require(
                attemptNo > 0
                        && amount.getCent() > 0
                        && executionEpoch >= 0
                        && binding.capabilities().contains(method),
                INVALID_ARGUMENT);
        if (Set.of(
                        PaymentAttemptState.SUBMITTING,
                        PaymentAttemptState.PENDING,
                        PaymentAttemptState.UNKNOWN,
                        PaymentAttemptState.SUCCEEDED)
                .contains(state)) require(executionEpoch > 0, INVALID_STATE);
        if (state == PaymentAttemptState.FAILED || state == PaymentAttemptState.CLOSED)
            DomainChecks.id(noCollectionEvidenceId);
    }

    public PaymentAttempt beginSubmission(long epoch) {
        require(state == PaymentAttemptState.INIT && epoch > 0, INVALID_STATE);
        return new PaymentAttempt(
                id,
                paymentId,
                attemptNo,
                amount,
                binding,
                method,
                channelRequestNo,
                PaymentAttemptState.SUBMITTING,
                epoch,
                null);
    }

    public static PaymentAttempt init(String id, String paymentId, int attemptNo, Money amount,
            ChannelBindingRevision binding, PaymentMethod method, String channelRequestNo) {
        return new PaymentAttempt(id, paymentId, attemptNo, amount, binding, method, channelRequestNo,
                PaymentAttemptState.INIT, 0, null);
    }

    public ChannelPaymentIdentity identity() {
        return new ChannelPaymentIdentity(paymentId, id, binding.channel(), binding.id(),
                binding.accountScopeHash(), channelRequestNo);
    }

    /** The adapter supplies verified facts; NOT_FOUND and transport failures must be UNKNOWN. */
    public PaymentAttempt recordOutcome(ChannelPaymentOutcome outcome, boolean executionFenced) {
        require(identity().equals(outcome.identity()) && amount.equals(outcome.amount()), CONFLICT);
        // A channel reversal with returned funds needs an independent financial fact and compensation.
        require(outcome.kind() != ChannelPaymentOutcome.Kind.CLOSED_WITH_FUNDS_RETURNED, CONFLICT);
        var target = switch (outcome.kind()) {
            case ACCEPTED -> PaymentAttemptState.PENDING;
            case SUCCEEDED -> PaymentAttemptState.SUCCEEDED;
            case NO_COLLECTION -> PaymentAttemptState.FAILED;
            case UNKNOWN -> PaymentAttemptState.UNKNOWN;
            case CLOSED_WITH_FUNDS_RETURNED -> throw new DomainViolation(CONFLICT);
        };
        if (terminal()) {
            // Late intermediate observations cannot undo a verified terminal fact.
            if (target == PaymentAttemptState.PENDING || target == PaymentAttemptState.UNKNOWN) return this;
            require(state == target || canSafelyReplace() && target == PaymentAttemptState.FAILED, CONFLICT);
            return this;
        }
        require(state != PaymentAttemptState.INIT, INVALID_STATE);
        if (target == PaymentAttemptState.FAILED) require(executionFenced, OUTCOME_UNKNOWN);
        if (state == target || state == PaymentAttemptState.UNKNOWN && target == PaymentAttemptState.PENDING)
            return this;
        return new PaymentAttempt(id, paymentId, attemptNo, amount, binding, method, channelRequestNo,
                target, executionEpoch, target == PaymentAttemptState.FAILED ? outcome.evidenceId() : null);
    }

    private boolean terminal() {
        return state == PaymentAttemptState.SUCCEEDED || state == PaymentAttemptState.FAILED
                || state == PaymentAttemptState.CLOSED;
    }

    public boolean canSafelyReplace() {
        return (state == PaymentAttemptState.FAILED || state == PaymentAttemptState.CLOSED)
                && noCollectionEvidenceId != null;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
