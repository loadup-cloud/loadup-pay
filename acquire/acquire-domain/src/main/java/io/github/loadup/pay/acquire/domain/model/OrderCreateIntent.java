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
package io.github.loadup.pay.acquire.domain.model;

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import java.time.LocalDateTime;
import java.util.Set;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_STATE;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Stable A0 identity. Dependency failure leaves INIT recoverable with the same order id.
 */
public record OrderCreateIntent(
        String id,
        String orderId,
        String payeeMerchantId,
        String merchantOrderNo,
        String requestDigest,
        String callerScopeHash,
        String entryMode,
        String initialSessionId,
        @DiagnosticHidden String intentPayload,
        String state,
        String admissionId,
        LocalDateTime materializeBefore,
        String resultCode,
        long rowVersion) {
    public OrderCreateIntent {
        require(entryMode != null && Set.of("ORDER", "CHECKOUT", "DIRECT_PAY").contains(entryMode), INVALID_ARGUMENT);
        if ("CHECKOUT".equals(entryMode)) DomainChecks.id(initialSessionId);
        else require(initialSessionId == null, INVALID_ARGUMENT);
        DomainChecks.id(id);
        DomainChecks.id(orderId);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.id(merchantOrderNo);
        DomainChecks.text(intentPayload, 16384);
        require(
                requestDigest != null
                        && requestDigest.matches("[0-9a-f]{64}")
                        && callerScopeHash != null
                        && callerScopeHash.matches("[0-9a-f]{64}")
                        && rowVersion >= 0,
                INVALID_ARGUMENT);
        require(Set.of("INIT", "COMPLETED", "REJECTED", "EXPIRED").contains(state), INVALID_STATE);
        if ("INIT".equals(state))
            require(admissionId == null && materializeBefore == null && resultCode == null, INVALID_STATE);
        else {
            DomainChecks.id(admissionId);
            DomainChecks.id(resultCode);
        }
        if ("COMPLETED".equals(state) || "EXPIRED".equals(state)) require(materializeBefore != null, INVALID_STATE);
    }

    public OrderCreateIntent finish(String next, String admission, LocalDateTime before, String code) {
        require("INIT".equals(state) && !"INIT".equals(next), INVALID_STATE);
        return new OrderCreateIntent(
                id,
                orderId,
                payeeMerchantId,
                merchantOrderNo,
                requestDigest,
                callerScopeHash,
                entryMode,
                initialSessionId,
                intentPayload,
                next,
                admission,
                before,
                code,
                Math.incrementExact(rowVersion));
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
