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

import io.github.loadup.pay.common.*;
import java.time.*;
import java.util.*;
import static io.github.loadup.pay.common.DomainViolation.Reason.*;
import static io.github.loadup.pay.common.DomainViolation.require;

/** Refunds have independent aggregates and cannot rewrite this successful amount or state. */
public record PaymentOrder(
        String id,
        String orderId,
        String allocationId,
        String payeeMerchantId,
        Money amount,
        String limitOperationId,
        LocalDateTime initiatedAt,
        PaymentState state,
        String activeAttemptId,
        String successfulAttemptId,
        long version) {
    public PaymentOrder {
        DomainChecks.id(id);
        DomainChecks.id(orderId);
        DomainChecks.id(allocationId);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.id(limitOperationId);
        Objects.requireNonNull(initiatedAt);
        Objects.requireNonNull(amount);
        Objects.requireNonNull(state);
        require(amount.minor() > 0 && version >= 0, INVALID_ARGUMENT);
        require((state == PaymentState.SUCCEEDED) == (successfulAttemptId != null), INVALID_STATE);
        if (successfulAttemptId != null) DomainChecks.id(successfulAttemptId);
        if (activeAttemptId != null) DomainChecks.id(activeAttemptId);
        if (state == PaymentState.SUCCEEDED) require(successfulAttemptId.equals(activeAttemptId), INVALID_STATE);
    }

    public static PaymentOrder init(
            String id,
            String order,
            String allocation,
            String payee,
            Money amount,
            String operation,
            LocalDateTime initiatedAt) {
        return new PaymentOrder(
                id, order, allocation, payee, amount, operation, initiatedAt, PaymentState.INIT, null, null, 0);
    }

    public PaymentOrder prepareAttempt(PaymentAttempt next, PaymentAttempt previous) {
        require(
                state != PaymentState.SUCCEEDED
                        && state != PaymentState.FAILED
                        && state != PaymentState.CLOSED
                        && state != PaymentState.CLOSING,
                INVALID_STATE);
        require(
                next.paymentId().equals(id)
                        && next.amount().equals(amount)
                        && next.binding().payeeMerchantId().equals(payeeMerchantId)
                        && next.state() == PaymentAttemptState.INIT,
                CONFLICT);
        if (activeAttemptId != null)
            require(
                    previous != null
                            && activeAttemptId.equals(previous.id())
                            && previous.paymentId().equals(id)
                            && previous.amount().equals(amount)
                            && previous.canSafelyReplace()
                            && !next.id().equals(previous.id())
                            && !next.channelRequestNo().equals(previous.channelRequestNo())
                            && next.attemptNo() == previous.attemptNo() + 1,
                    OUTCOME_UNKNOWN);
        else require(next.attemptNo() == 1, INVALID_ARGUMENT);
        return new PaymentOrder(
                id,
                orderId,
                allocationId,
                payeeMerchantId,
                amount,
                limitOperationId,
                initiatedAt,
                PaymentState.PROCESSING,
                next.id(),
                null,
                Math.incrementExact(version));
    }

    public PaymentOrder applyAttempt(PaymentAttempt attempt) {
        require(id.equals(attempt.paymentId()) && amount.equals(attempt.amount())
                && payeeMerchantId.equals(attempt.binding().payeeMerchantId())
                && Objects.equals(activeAttemptId, attempt.id()), CONFLICT);
        if (state == PaymentState.SUCCEEDED) {
            require(attempt.state() == PaymentAttemptState.SUCCEEDED, CONFLICT);
            return this;
        }
        require(state != PaymentState.CLOSED && state != PaymentState.FAILED, CONFLICT);
        var nextState = attempt.state() == PaymentAttemptState.SUCCEEDED ? PaymentState.SUCCEEDED
                : state == PaymentState.CLOSING ? PaymentState.CLOSING
                : attempt.state() == PaymentAttemptState.UNKNOWN ? PaymentState.UNKNOWN : PaymentState.PROCESSING;
        if (nextState == state) return this;
        return copy(nextState, nextState == PaymentState.SUCCEEDED ? attempt.id() : null);
    }

    public PaymentOrder requestClose() {
        if (state == PaymentState.CLOSING) return this;
        require(state == PaymentState.INIT || state == PaymentState.PROCESSING || state == PaymentState.UNKNOWN,
                INVALID_STATE);
        return copy(PaymentState.CLOSING, null);
    }

    public PaymentOrder close(NoCollectionEvidence evidence) {
        require(id.equals(evidence.paymentId()), CONFLICT);
        if (state == PaymentState.CLOSED) return this;
        require(state == PaymentState.CLOSING, INVALID_STATE);
        return copy(PaymentState.CLOSED, null);
    }

    /** Final failure is a payment-wide no-collection proof; one failed attempt is insufficient. */
    public PaymentOrder fail(NoCollectionEvidence evidence) {
        require(id.equals(evidence.paymentId()), CONFLICT);
        if (state == PaymentState.FAILED) return this;
        require(state == PaymentState.INIT || state == PaymentState.PROCESSING || state == PaymentState.UNKNOWN,
                INVALID_STATE);
        return copy(PaymentState.FAILED, null);
    }

    private PaymentOrder copy(PaymentState target, String success) {
        return new PaymentOrder(id, orderId, allocationId, payeeMerchantId, amount, limitOperationId,
                initiatedAt, target, activeAttemptId, success, Math.incrementExact(version));
    }
}
