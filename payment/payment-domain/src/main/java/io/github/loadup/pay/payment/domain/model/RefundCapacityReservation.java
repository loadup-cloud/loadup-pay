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

import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.commons.error.DomainViolation.require;

public record RefundCapacityReservation(PaymentRefundCapacity previous, PaymentRefundCapacity reserved) {
    public RefundCapacityReservation {
        Objects.requireNonNull(previous);
        Objects.requireNonNull(reserved);
        require(previous.paymentId().equals(reserved.paymentId()) && previous.orderId().equals(reserved.orderId())
                && previous.payeeMerchantId().equals(reserved.payeeMerchantId()) && previous.succeededAt().equals(reserved.succeededAt())
                && previous.succeededAmount().equals(reserved.succeededAmount()) && previous.refundedMinor() == reserved.refundedMinor()
                && reserved.reservedMinor() > previous.reservedMinor() && reserved.version() == Math.incrementExact(previous.version()), CONFLICT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
