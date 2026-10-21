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
package io.github.loadup.pay.limitcenter.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.money.Money;
import io.github.loadup.pay.common.DomainChecks;

import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.pay.common.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_STATE;
import static io.github.loadup.pay.common.DomainViolation.Reason.OUTCOME_UNKNOWN;
import static io.github.loadup.pay.common.DomainViolation.require;

public record LimitReservation(
        String id,
        String operationId,
        String paymentId,
        String orderId,
        String payeeMerchantId,
        Money amount,
        String policyId,
        int policyVersion,
        LocalDateTime initiatedAt,
        LimitReservationState state,
        LocalDateTime reserveExpiresAt,
        String activeAttemptId,
        long executionEpoch,
        long paymentSourceVersion,
        long version) {
    public LimitReservation {
        DomainChecks.id(id);
        DomainChecks.id(operationId);
        DomainChecks.id(paymentId);
        DomainChecks.id(orderId);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.id(policyId);
        DomainChecks.amount(amount);
        Objects.requireNonNull(initiatedAt);
        Objects.requireNonNull(reserveExpiresAt);
        Objects.requireNonNull(state);
        require(
                amount.getCent() > 0
                        && policyVersion > 0
                        && reserveExpiresAt.isAfter(initiatedAt)
                        && executionEpoch >= 0
                        && paymentSourceVersion >= 0
                        && version >= 0,
                INVALID_ARGUMENT);
        if (state == LimitReservationState.EXECUTING)
            require(activeAttemptId != null && executionEpoch > 0, INVALID_STATE);
    }

    public static LimitReservation init(
            String id,
            String operationId,
            String paymentId,
            String orderId,
            String payee,
            Money amount,
            String policyId,
            int policyVersion,
            LocalDateTime initiatedAt,
            LocalDateTime expiresAt) {
        return new LimitReservation(
                id,
                operationId,
                paymentId,
                orderId,
                payee,
                amount,
                policyId,
                policyVersion,
                initiatedAt,
                LimitReservationState.INIT,
                expiresAt,
                null,
                0,
                0,
                0);
    }

    public LimitReservation reserve() {
        if (state == LimitReservationState.RESERVED) return this;
        require(state == LimitReservationState.INIT, INVALID_STATE);
        return copy(LimitReservationState.RESERVED, null, 0, 0);
    }

    public LimitReservation release(LimitReleaseEvidence evidence) {
        require(paymentId.equals(evidence.paymentId()) && amount.equals(evidence.amount()), CONFLICT);
        if (state == LimitReservationState.RELEASED) return this;
        require(state == LimitReservationState.RESERVED || state == LimitReservationState.EXECUTING, CONFLICT);
        return copy(LimitReservationState.RELEASED, activeAttemptId, executionEpoch, evidence.paymentVersion());
    }

    public LimitReservation beginExecution(String attemptId, LocalDateTime now) {
        return beginExecution(attemptId, now, null);
    }

    public LimitReservation beginExecution(String attemptId, LocalDateTime now, LimitAttemptEvidence previous) {
        DomainChecks.id(attemptId);
        Objects.requireNonNull(now);
        if (state == LimitReservationState.EXECUTING && attemptId.equals(activeAttemptId)) return this;
        if (state == LimitReservationState.EXECUTING) {
            require(
                    previous != null
                            && operationId.equals(previous.operationId())
                            && paymentId.equals(previous.paymentId())
                            && activeAttemptId.equals(previous.previousAttemptId())
                            && executionEpoch == previous.previousExecutionEpoch(),
                    OUTCOME_UNKNOWN);
        } else {
            require(state == LimitReservationState.RESERVED && now.isBefore(reserveExpiresAt), INVALID_STATE);
            require(previous == null, INVALID_ARGUMENT);
        }
        return copy(
                LimitReservationState.EXECUTING, attemptId, Math.incrementExact(executionEpoch), paymentSourceVersion);
    }

    public LimitReservation expire(LocalDateTime now) {
        require(state == LimitReservationState.RESERVED && !now.isBefore(reserveExpiresAt), INVALID_STATE);
        return copy(LimitReservationState.EXPIRED, activeAttemptId, executionEpoch, paymentSourceVersion);
    }

    public LimitReservation confirm(String sourcePaymentId, Money paid, long sourceVersion) {
        require(paymentId.equals(sourcePaymentId) && amount.equals(paid) && sourceVersion > 0, CONFLICT);
        if (state == LimitReservationState.CONFIRMED) return this;
        require(state == LimitReservationState.EXECUTING, CONFLICT);
        return copy(LimitReservationState.CONFIRMED, activeAttemptId, executionEpoch, sourceVersion);
    }

    public void requireSameIntent(
            String sourcePayment,
            String sourceOrder,
            String sourcePayee,
            Money requested,
            String sourcePolicy,
            int sourcePolicyVersion,
            LocalDateTime sourceInitiatedAt) {
        require(
                paymentId.equals(sourcePayment)
                        && orderId.equals(sourceOrder)
                        && payeeMerchantId.equals(sourcePayee)
                        && amount.equals(requested)
                        && policyId.equals(sourcePolicy)
                        && policyVersion == sourcePolicyVersion
                        && initiatedAt.equals(sourceInitiatedAt),
                CONFLICT);
    }

    private LimitReservation copy(LimitReservationState target, String attempt, long epoch, long sourceVersion) {
        return new LimitReservation(
                id,
                operationId,
                paymentId,
                orderId,
                payeeMerchantId,
                amount,
                policyId,
                policyVersion,
                initiatedAt,
                target,
                reserveExpiresAt,
                attempt,
                epoch,
                sourceVersion,
                Math.incrementExact(version));
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
