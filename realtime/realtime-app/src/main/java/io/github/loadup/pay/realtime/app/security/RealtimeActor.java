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
package io.github.loadup.pay.realtime.app.security;

import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import java.util.Set;

/**
 * Exactly one service or merchant scope, supplied only by the verified JWT adapter.
 */
public record RealtimeActor(String tenantId, String serviceId, String merchantId, Set<String> permissions) {
    public RealtimeActor {
        DomainChecks.id(tenantId);
        permissions = Set.copyOf(permissions);
        DomainViolation.require((serviceId == null) != (merchantId == null), DomainViolation.Reason.INVALID_ARGUMENT);
        if (serviceId != null) DomainChecks.id(serviceId);
        if (merchantId != null) DomainChecks.id(merchantId);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
