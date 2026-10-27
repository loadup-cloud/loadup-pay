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
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.payment.PaymentMethod;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import java.util.Objects;
import static io.github.loadup.commons.error.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_STATE;
import static io.github.loadup.commons.error.DomainViolation.require;

/** Immutable cross-record binding only. Not a lock, actor authorization, gate permission or Y2 submission ticket. */
public record PaymentAttemptBinding(StoredPaymentAcceptance accepted, StoredPaymentAttempt stored) {
    public PaymentAttemptBinding {
        Objects.requireNonNull(accepted); Objects.requireNonNull(stored);
        var payment = accepted.payment();
        var attempt = stored.attempt();
        require(samePayment(accepted, stored)
                && payment.amount().equals(attempt.amount())
                && payment.payeeMerchantId().equals(attempt.binding().payeeMerchantId())
                && payment.limitOperationId().equals(stored.limitOperationId())
                && accepted.callerServiceId().equals(stored.callerServiceId())
                && accepted.requestDigest().equals(stored.paymentRequestDigest()), CONFLICT);
    }

    public PaymentAttemptBinding requireActive() {
        require(active(accepted, stored), CONFLICT);
        return this;
    }

    /** Eligibility before durable Y2; the caller must still hold and check both OPEN gates. */
    public PaymentAttemptBinding requireSubmittable() {
        requireActive();
        require(accepted.payment().state() == PaymentState.PROCESSING && stored.attempt().state() == PaymentAttemptState.INIT
                && !submitted(stored), INVALID_STATE);
        return this;
    }

    /** Submitted original facts remain observable after fencing, replacement or a terminal winner. */
    public PaymentAttemptBinding requireObservable() {
        require(submitted(stored), INVALID_STATE);
        return this;
    }

    /** Closing eligibility does not establish no-collection evidence or release any capacity. */
    public PaymentAttemptBinding requireClosable() {
        requireActive();
        var state = accepted.payment().state();
        require(state != PaymentState.SUCCEEDED && state != PaymentState.FAILED && state != PaymentState.CLOSED, INVALID_STATE);
        return this;
    }

    /** Rechecks the already-issued in-memory winner before building its original request; never issues a ticket. */
    public PaymentAttemptBinding requireSubmissionRequest() {
        requireActive(); requireObservable();
        require(accepted.payment().state() == PaymentState.PROCESSING && stored.attempt().state() == PaymentAttemptState.SUBMITTING, INVALID_STATE);
        return this;
    }

    public PaymentAttemptBinding requireRoute(Channel channel, PaymentMethod method, String preparedInputRef) {
        require(stored.attempt().binding().channel() == channel && stored.attempt().method() == method
                && Objects.equals(stored.preparedInputRef(), preparedInputRef), CONFLICT);
        return this;
    }

    public enum ObservationRejection { PAYMENT_IDENTITY_MISMATCH, PAYMENT_AMOUNT_MISMATCH, OBSERVATION_BEFORE_SUBMISSION }

    /** Wrong targets are auditable observation conflicts; corrupt bound records still fail instead of fabricating receipts. */
    public static ObservationRejection observationRejection(StoredPaymentAcceptance accepted, StoredPaymentAttempt stored,
            ChannelPaymentIdentity observedIdentity, Money observedAmount) {
        Objects.requireNonNull(accepted); Objects.requireNonNull(stored);
        if (!stored.attempt().identity().equals(observedIdentity) || !samePayment(accepted, stored) || !active(accepted, stored))
            return ObservationRejection.PAYMENT_IDENTITY_MISMATCH;
        new PaymentAttemptBinding(accepted, stored);
        if (!stored.attempt().amount().equals(observedAmount)) return ObservationRejection.PAYMENT_AMOUNT_MISMATCH;
        return submitted(stored) ? null : ObservationRejection.OBSERVATION_BEFORE_SUBMISSION;
    }

    private static boolean samePayment(StoredPaymentAcceptance accepted, StoredPaymentAttempt stored) {
        return accepted.payment().id().equals(stored.attempt().paymentId());
    }
    private static boolean active(StoredPaymentAcceptance accepted, StoredPaymentAttempt stored) {
        return Objects.equals(accepted.payment().activeAttemptId(), stored.attempt().id());
    }
    private static boolean submitted(StoredPaymentAttempt stored) { return stored.attempt().executionEpoch() > 0; }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
