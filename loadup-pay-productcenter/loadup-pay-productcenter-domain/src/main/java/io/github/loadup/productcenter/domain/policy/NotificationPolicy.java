/*
 * Copyright (C) 2026 LoadUp Cloud
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.loadup.productcenter.domain.policy;

import io.github.loadup.commons.validation.DomainChecks;
import java.math.BigDecimal;

public record NotificationPolicy(
        boolean enabled,
        String targetRef,
        String signingIdentityRef,
        int timeoutMillis,
        long retryInitialSeconds,
        BigDecimal retryMultiplier,
        long retryMaximumSeconds,
        int retryMaxAttempts,
        long retryWindowSeconds) {
    public NotificationPolicy {
        PolicyChecks.require(timeoutMillis > 0
                && retryInitialSeconds > 0
                && retryMaximumSeconds >= retryInitialSeconds
                && retryMaxAttempts > 0
                && retryWindowSeconds > 0
                && retryMultiplier != null
                && retryMultiplier.compareTo(BigDecimal.ONE) >= 0
                && PolicyChecks.optionalText(targetRef, 128)
                && PolicyChecks.optionalText(signingIdentityRef, 128));
        if (enabled) {
            DomainChecks.text(targetRef, 128);
            DomainChecks.text(signingIdentityRef, 128);
            var normalized = retryMultiplier.stripTrailingZeros();
            PolicyChecks.require((long) normalized.precision() - normalized.scale() <= 20 && normalized.scale() <= 18);
        }
    }
}
