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
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.pay.channel.api.model.ChannelPaymentIdentity;
import io.github.loadup.pay.channel.api.model.PaymentObservation;

import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

public record PaymentChannelReceipt(String id, ChannelPaymentIdentity identity, PaymentObservation.Source source,
                                    String receiptKey, String factsDigest, String sourceDigest,
                                    @DiagnosticHidden String observationPayload,
                                    PaymentReceiptDisposition disposition, String reasonCode,
                                    LocalDateTime receivedAt) {
    public PaymentChannelReceipt {
        DomainChecks.id(id);
        Objects.requireNonNull(identity);
        Objects.requireNonNull(source);
        DomainChecks.text(receiptKey, 128);
        DomainChecks.text(observationPayload, 32768);
        require(factsDigest != null && factsDigest.matches("[0-9a-f]{64}")
                && sourceDigest != null && sourceDigest.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
        Objects.requireNonNull(disposition);
        Objects.requireNonNull(receivedAt);
        if (reasonCode != null) {
            DomainChecks.text(reasonCode, 64);
        }
        require(disposition != PaymentReceiptDisposition.CONFLICT || reasonCode != null, INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
