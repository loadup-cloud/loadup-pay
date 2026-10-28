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
package io.github.loadup.pay.payment.app.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.money.Money;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.payment.client.enums.PaymentRecoveryKind;

import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Persisted UNKNOWN identity and cursor metadata only, never an execution ticket.
 */
public record UnknownRecoveryEntry(PaymentRecoveryKind kind, String recordId, String subjectId, String orderId,
                                   String payeeMerchantId, Money amount, LocalDateTime firstUnknownAt, Long rowVersion,
                                   Long recoveryGeneration, Boolean recoveryCompleted) {
    public UnknownRecoveryEntry {
        Objects.requireNonNull(kind);
        DomainChecks.id(recordId);
        DomainChecks.id(subjectId);
        DomainChecks.id(orderId);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.cnyAmount(amount);
        Objects.requireNonNull(firstUnknownAt);
        Objects.requireNonNull(rowVersion);
        require(amount.getCent() > 0 && rowVersion >= 0 && firstUnknownAt.getNano() % 1000 == 0
                && firstUnknownAt.getYear() >= 1970 && firstUnknownAt.getYear() <= 9998, INVALID_ARGUMENT);
        require((recoveryGeneration == null) == (recoveryCompleted == null)
                && (recoveryGeneration == null || recoveryGeneration >= 0), INVALID_ARGUMENT);
        require(kind != PaymentRecoveryKind.COLLECTION || recordId.equals(subjectId), INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
