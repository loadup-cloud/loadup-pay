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

import io.github.loadup.commons.json.*;
import io.github.loadup.pay.common.DomainChecks;
import static io.github.loadup.pay.common.DomainViolation.require;
import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT;

/** Frozen internal command, retained without any raw channel input or credential. */
public record StoredPaymentAcceptance(
        PaymentOrder payment,
        String admissionId,
        String requestDigest,
        String callerServiceId,
        @DiagnosticHidden String intentPayload,
        String payloadHash,
        String terminalEvidenceId) {
    public StoredPaymentAcceptance {
        java.util.Objects.requireNonNull(payment);
        DomainChecks.id(admissionId);
        DomainChecks.id(callerServiceId);
        DomainChecks.text(intentPayload, 262144);
        if (terminalEvidenceId != null) DomainChecks.id(terminalEvidenceId);
        if (payment.state() == PaymentState.CLOSED || payment.state() == PaymentState.FAILED)
            require(terminalEvidenceId != null, INVALID_ARGUMENT);
        require(
                requestDigest != null
                        && requestDigest.matches("[0-9a-f]{64}")
                        && payloadHash != null
                        && payloadHash.matches("[0-9a-f]{64}"),
                INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
