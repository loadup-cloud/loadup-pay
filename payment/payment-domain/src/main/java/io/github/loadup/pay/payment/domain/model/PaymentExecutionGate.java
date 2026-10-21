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
import io.github.loadup.pay.common.*;
import java.time.*;
import java.util.*;
import static io.github.loadup.pay.common.DomainViolation.Reason.*;
import static io.github.loadup.pay.common.DomainViolation.require;

/** A null paymentId denotes the persistent parent-order gate. */
public record PaymentExecutionGate(String orderId, String paymentId, GateState state, long fenceEpoch) {
    public PaymentExecutionGate {
        DomainChecks.id(orderId);
        if (paymentId != null) DomainChecks.id(paymentId);
        Objects.requireNonNull(state);
        require(fenceEpoch >= 0, INVALID_ARGUMENT);
        require((state == GateState.FENCED) == (fenceEpoch > 0), INVALID_STATE);
    }

    public static PaymentExecutionGate init(String orderId, String paymentId) {
        return new PaymentExecutionGate(orderId, paymentId, GateState.INIT, 0);
    }

    public PaymentExecutionGate fence(long epoch) {
        require(epoch > 0, INVALID_ARGUMENT);
        return state == GateState.FENCED && epoch <= fenceEpoch
                ? this
                : new PaymentExecutionGate(orderId, paymentId, GateState.FENCED, Math.max(epoch, fenceEpoch));
    }

    /** A tombstone received before creation can never be opened by a late execution intent. */
    public PaymentExecutionGate open() {
        if (state == GateState.OPEN) return this;
        require(state == GateState.INIT, INVALID_STATE);
        return new PaymentExecutionGate(orderId, paymentId, GateState.OPEN, 0);
    }

    public void requireOpen() {
        require(state == GateState.OPEN, INVALID_STATE);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
