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
package io.github.loadup.pay.acquire.app.security;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;
import java.util.Set;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/** Resource scope comes only from authenticated JWT claims, never the notification query. */
public record MerchantNotificationActor(String tenantId, String principalType, String principalId, String merchantId, Set<String> permissions) {
    public MerchantNotificationActor {
        DomainChecks.id(tenantId); DomainChecks.text(principalId, 128); permissions = Set.copyOf(permissions);
        require(principalType != null && Set.of("MERCHANT", "MERCHANT_APP", "OPERATOR", "SERVICE").contains(principalType), INVALID_ARGUMENT);
        if ("MERCHANT".equals(principalType) || "MERCHANT_APP".equals(principalType)) {
            DomainChecks.id(merchantId);
            require(!"MERCHANT".equals(principalType) || principalId.equals(merchantId), INVALID_ARGUMENT);
        } else require(merchantId == null, INVALID_ARGUMENT);
        if ("SERVICE".equals(principalType)) DomainChecks.id(principalId);
    }
    public boolean merchant() { return merchantId != null; }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
