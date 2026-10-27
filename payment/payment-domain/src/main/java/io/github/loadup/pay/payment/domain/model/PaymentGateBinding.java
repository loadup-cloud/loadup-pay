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
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_STATE;
import static io.github.loadup.commons.error.DomainViolation.require;

/** One validated parent/payment-gate view. No lock acquisition, authorization or submission rights. */
public record PaymentGateBinding(StoredPaymentAcceptance accepted, StoredPaymentGate parent, StoredPaymentGate gate) {
    public PaymentGateBinding {
        Objects.requireNonNull(accepted); Objects.requireNonNull(parent); Objects.requireNonNull(gate);
        requireGate(parent, accepted, null);
        requireGate(gate, accepted, accepted.payment().id());
    }

    public PaymentGateBinding requireOpen() {
        require(parent.gate().state() == GateState.OPEN && gate.gate().state() == GateState.OPEN, INVALID_STATE);
        return this;
    }

    private static void requireGate(StoredPaymentGate value, StoredPaymentAcceptance accepted, String paymentId) {
        require(value.gate().orderId().equals(accepted.payment().orderId())
                && Objects.equals(value.gate().paymentId(), paymentId)
                && value.payeeMerchantId().equals(accepted.payment().payeeMerchantId())
                && value.callerServiceId().equals(accepted.callerServiceId()) && value.gate().state() != GateState.INIT, CONFLICT);
    }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
