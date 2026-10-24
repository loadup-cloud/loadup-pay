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

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.payment.Channel;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.payment.PaymentMethod;

import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Ciphertext and immutable use scope; raw payer input never enters a payment intent.
 */
public record PaymentInput(
        @DiagnosticHidden String id, String paymentId, String orderId, String allocationId,
        String payeeMerchantId, Money amount, String inputKey, Channel channel, PaymentMethod method,
        String callerServiceId, String fingerprintKeyId, @DiagnosticHidden String inputDigest,
        String encryptionKey, int encryptionVersion, @DiagnosticHidden String ciphertext,
        LocalDateTime preparedAt, LocalDateTime expiresAt, PaymentInputState state,
        String boundAttemptId, long rowVersion) {
    public PaymentInput {
        DomainChecks.id(id);
        DomainChecks.id(paymentId);
        DomainChecks.id(orderId);
        DomainChecks.id(allocationId);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.id(callerServiceId);
        DomainChecks.text(inputKey, 128);
        DomainChecks.id(fingerprintKeyId);
        DomainChecks.text(encryptionKey, 128);
        DomainChecks.text(ciphertext, 65536);
        DomainChecks.cnyAmount(amount);
        DomainChecks.positive(amount.getCent());
        Objects.requireNonNull(channel);
        Objects.requireNonNull(method);
        Objects.requireNonNull(state);
        Objects.requireNonNull(preparedAt);
        Objects.requireNonNull(expiresAt);
        require(method == PaymentMethod.PAYMENT_CODE || method == PaymentMethod.JSAPI || method == PaymentMethod.H5, INVALID_ARGUMENT);
        require(inputDigest != null && inputDigest.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
        require(encryptionVersion > 0 && expiresAt.isAfter(preparedAt) && rowVersion >= 0, INVALID_ARGUMENT);
        if (boundAttemptId != null) DomainChecks.id(boundAttemptId);
        require((state == PaymentInputState.INIT && boundAttemptId == null && rowVersion == 0)
                || (state == PaymentInputState.BOUND && boundAttemptId != null && rowVersion == 1), INVALID_ARGUMENT);
    }

    public boolean expired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public PaymentInput bind(String attemptId, LocalDateTime now) {
        DomainChecks.id(attemptId);
        require(!expired(now), INVALID_ARGUMENT);
        require(boundAttemptId == null || boundAttemptId.equals(attemptId), DomainViolation.Reason.CONFLICT);
        if (state == PaymentInputState.BOUND) return this;
        return new PaymentInput(id, paymentId, orderId, allocationId, payeeMerchantId, amount, inputKey,
                channel, method, callerServiceId, fingerprintKeyId, inputDigest, encryptionKey, encryptionVersion,
                ciphertext, preparedAt, expiresAt, PaymentInputState.BOUND, attemptId, 1);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
