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

import io.github.loadup.commons.json.DiagnosticHidden;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.common.*;
import java.time.*;
import java.util.*;
import static io.github.loadup.pay.common.DomainViolation.Reason.*;
import static io.github.loadup.pay.common.DomainViolation.require;

/** References a channel-specific immutable identity; never a universal channel wire payload. */
public record ChannelBindingRevision(
        String id,
        String payeeMerchantId,
        Channel channel,
        AccessMode accessMode,
        String accountScopeHash,
        @DiagnosticHidden String credentialReference,
        Set<PaymentMethod> capabilities) {
    public ChannelBindingRevision {
        DomainChecks.id(id);
        DomainChecks.id(payeeMerchantId);
        Objects.requireNonNull(channel);
        Objects.requireNonNull(accessMode);
        DomainChecks.text(accountScopeHash, 64);
        DomainChecks.text(credentialReference, 128);
        capabilities = Set.copyOf(capabilities);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
