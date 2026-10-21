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
import io.github.loadup.pay.common.DomainChecks;

import java.util.Objects;

import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_STATE;
import static io.github.loadup.pay.common.DomainViolation.require;

/** Persists a release even when Reserve has not arrived. */
public record LimitOperationGate(
        String operationId,
        String paymentId,
        GateState state,
        long paymentSourceVersion,
        String noCollectionEvidenceId) {
    public LimitOperationGate {
        DomainChecks.id(operationId);
        DomainChecks.id(paymentId);
        Objects.requireNonNull(state);
        require(paymentSourceVersion >= 0, INVALID_ARGUMENT);
        if (state == GateState.FENCED) DomainChecks.id(noCollectionEvidenceId);
    }

    public static LimitOperationGate init(String operationId, String paymentId) {
        return new LimitOperationGate(operationId, paymentId, GateState.INIT, 0, null);
    }

    public LimitOperationGate fence(long sourceVersion, String evidenceId) {
        require(sourceVersion > 0, INVALID_ARGUMENT);
        if (state == GateState.FENCED && sourceVersion <= paymentSourceVersion) return this;
        return new LimitOperationGate(operationId, paymentId, GateState.FENCED, sourceVersion, evidenceId);
    }

    public void requireOpen() {
        require(state == GateState.OPEN, INVALID_STATE);
    }

    public LimitOperationGate open() {
        if (state == GateState.OPEN) return this;
        require(state == GateState.INIT, INVALID_STATE);
        return new LimitOperationGate(operationId, paymentId, GateState.OPEN, 0, null);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
