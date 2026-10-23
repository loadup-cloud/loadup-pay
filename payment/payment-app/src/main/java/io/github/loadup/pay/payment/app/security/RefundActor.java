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
package io.github.loadup.pay.payment.app.security;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import java.util.Set;

/**
 * Merchant identity and permissions extracted only from a verified credential.
 */
public record RefundActor(String tenantId, String merchantId, String subjectHash, Set<String> permissions) {
    public RefundActor {
        DomainChecks.id(tenantId);
        DomainChecks.id(merchantId);
        io.github.loadup.commons.error.DomainViolation.require(subjectHash != null && subjectHash.matches("[0-9a-f]{64}"),
                io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT);
        permissions = Set.copyOf(permissions);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
