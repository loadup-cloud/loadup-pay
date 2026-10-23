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
package io.github.loadup.pay.product.client.dto;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;
import io.github.loadup.commons.error.DomainViolation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record NotificationTermsDTO(
        @Schema(description = "Enabled") boolean enabled,
        @Schema(description = "Target ref") @Size(max = 128) String targetRef,

        @Schema(description = "Signing identity ref") @Size(max = 128)
        String signingIdentityRef,

        @Schema(description = "Timeout millis") @Positive int timeoutMillis,

        @Schema(description = "Retry initial seconds") @Positive
        long retryInitialSeconds,

        @Schema(description = "Retry multiplier") BigDecimal retryMultiplier,

        @Schema(description = "Retry maximum seconds") @Positive
        long retryMaximumSeconds,

        @Schema(description = "Retry max attempts") @Positive
        int retryMaxAttempts,

        @Schema(description = "Retry window seconds") @Positive
        long retryWindowSeconds) {
    public NotificationTermsDTO {
        DomainViolation.require(
                timeoutMillis > 0
                        && retryInitialSeconds > 0
                        && retryMaximumSeconds >= retryInitialSeconds
                        && retryMaxAttempts > 0
                        && retryWindowSeconds > 0
                        && retryMultiplier != null
                        && retryMultiplier.compareTo(BigDecimal.ONE) >= 0,
                DomainViolation.Reason.INVALID_ARGUMENT);
        if (enabled) {
            DomainChecks.text(targetRef, 128);
            DomainChecks.text(signingIdentityRef, 128);
        }
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
