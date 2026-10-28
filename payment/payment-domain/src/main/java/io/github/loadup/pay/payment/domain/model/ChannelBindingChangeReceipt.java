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

import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

public record ChannelBindingChangeReceipt(String id, String bindingId, String kind, String requestKey,
                                          String requestDigest, String callerServiceId, String bindingRevisionId,
                                          int revision,
                                          ChannelBindingState state, long appliedVersion, LocalDateTime appliedAt) {
    public ChannelBindingChangeReceipt {
        DomainChecks.id(id);
        DomainChecks.id(bindingId);
        DomainChecks.id(bindingRevisionId);
        DomainChecks.text(requestKey, 128);
        DomainChecks.id(callerServiceId);
        Objects.requireNonNull(appliedAt);
        DomainViolation.require(Set.of("PUBLISH", "SUSPEND", "REVOKE").contains(kind) && revision > 0 && appliedVersion > 0
                && requestDigest != null && requestDigest.matches("[0-9a-f]{64}")
                && ("PUBLISH".equals(kind) && state == ChannelBindingState.ACTIVE
                || "SUSPEND".equals(kind) && state == ChannelBindingState.SUSPENDED
                || "REVOKE".equals(kind) && state == ChannelBindingState.REVOKED), DomainViolation.Reason.INVALID_ARGUMENT);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
