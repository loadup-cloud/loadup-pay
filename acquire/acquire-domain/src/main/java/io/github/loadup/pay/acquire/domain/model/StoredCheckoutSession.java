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

import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

public record StoredCheckoutSession(
        CheckoutSession session,
        String payeeMerchantId,
        String requestKey,
        String requestDigest,
        @DiagnosticHidden String tokenHash,
        String tokenNonce,
        String tokenKeyRef,
        String tokenKeyVersion,
        String allowedOrigin,
        CheckoutSessionScope scope,
        long rowVersion) {
    public StoredCheckoutSession {
        Objects.requireNonNull(session);
        Objects.requireNonNull(scope);
        DomainChecks.id(payeeMerchantId);
        DomainChecks.text(requestKey, 128);
        DomainChecks.text(tokenKeyRef, 128);
        DomainChecks.id(tokenKeyVersion);
        DomainChecks.text(allowedOrigin, 512);
        require(
                requestDigest != null
                        && requestDigest.matches("[0-9a-f]{64}")
                        && tokenHash != null
                        && tokenHash.matches("[0-9a-f]{64}")
                        && tokenNonce != null
                        && tokenNonce.matches("[0-9a-f]{64}")
                        && rowVersion >= 0
                        && session.expiresAt().getNano() == 0,
                INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
