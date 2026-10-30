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

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Frozen contract policy. Backoff arithmetic saturates and rounds upward, never overflowing.
 */
public record MerchantNotificationPolicy(String targetRef, String signingIdentityRef, int timeoutMillis,
                                         long retryInitialSeconds, BigDecimal retryMultiplier, long retryMaximumSeconds,
                                         int retryMaxAttempts, long retryWindowSeconds) {
    public MerchantNotificationPolicy {
        DomainChecks.text(targetRef, 128);
        DomainChecks.text(signingIdentityRef, 128);
        require(timeoutMillis > 0 && retryInitialSeconds > 0 && retryMaximumSeconds >= retryInitialSeconds
                && retryMaxAttempts > 0 && retryWindowSeconds > 0 && retryMultiplier != null
                && retryMultiplier.compareTo(BigDecimal.ONE) >= 0, INVALID_ARGUMENT);
        retryMultiplier = retryMultiplier.stripTrailingZeros();
        require((long) retryMultiplier.precision() - retryMultiplier.scale() <= 20 && retryMultiplier.scale() <= 18, INVALID_ARGUMENT);
    }

    public long delaySeconds(int attempt) {
        require(attempt > 0, INVALID_ARGUMENT);
        var cap = BigDecimal.valueOf(retryMaximumSeconds);
        var context = new MathContext(18, RoundingMode.CEILING);
        var factor = retryMultiplier.min(cap).round(context);
        var delay = BigDecimal.valueOf(retryInitialSeconds);
        int exponent = attempt - 1;
        while (exponent > 0) {
            if ((exponent & 1) != 0) {
                delay = delay.multiply(factor, context).min(cap);
            }
            exponent >>>= 1;
            if (exponent > 0) {
                factor = factor.multiply(factor, context).min(cap);
            }
        }
        return delay.setScale(0, RoundingMode.CEILING).min(cap).longValueExact();
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
