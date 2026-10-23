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
package io.github.loadup.pay.product.domain.model;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;

import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_STATE;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * The original decision and its self-contained terms remain immutable after contract stop.
 */
public record StoredOrderAdmission(
        AdmissionIntent intent,
        OrderAdmission admission,
        long merchantFactVersion,
        long authorizationVersion,
        @DiagnosticHidden String compiledTerms) {
    public StoredOrderAdmission {
        Objects.requireNonNull(intent);
        Objects.requireNonNull(admission);
        admission.requireSameIntent(intent.orderId(), intent.payeeMerchantId(), intent.requestDigest());
        require(merchantFactVersion >= 0 && authorizationVersion >= 0, INVALID_ARGUMENT);
        require(admission.decision() != AdmissionDecision.INIT, INVALID_STATE);
        require(
                admission.decision() == AdmissionDecision.ADMITTED
                        ? compiledTerms != null && !compiledTerms.isBlank()
                        : compiledTerms == null,
                INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
