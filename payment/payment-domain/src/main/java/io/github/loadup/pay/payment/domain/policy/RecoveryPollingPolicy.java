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
package io.github.loadup.pay.payment.domain.policy;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Polling pace only. It neither concludes financial outcomes nor grants execution rights.
 */
public record RecoveryPollingPolicy(long initialDelaySeconds, long maximumDelaySeconds, long backoffEverySeconds,
                                    int jitterPercent) {
    public RecoveryPollingPolicy {
        require(initialDelaySeconds >= 1 && initialDelaySeconds <= 86400
                && maximumDelaySeconds >= initialDelaySeconds && maximumDelaySeconds <= 86400
                && backoffEverySeconds >= 1 && backoffEverySeconds <= 604800
                && jitterPercent >= 0 && jitterPercent <= 50, INVALID_ARGUMENT);
    }

    public LocalDateTime nextAt(String tenant, String operation, String businessId, long generation,
                                LocalDateTime startedAt, LocalDateTime previousDueAt, LocalDateTime now) {
        DomainChecks.id(tenant);
        DomainChecks.text(operation, 128);
        DomainChecks.id(businessId);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(previousDueAt);
        Objects.requireNonNull(now);
        require(generation >= 0 && !previousDueAt.isBefore(startedAt), INVALID_ARGUMENT);
        var anchor = (now.isAfter(previousDueAt) ? now : previousDueAt).truncatedTo(ChronoUnit.MICROS);
        long stages = Duration.between(startedAt, anchor).getSeconds() / backoffEverySeconds;
        long delay = initialDelaySeconds;
        // Saturate before multiplying; an arbitrarily old request never overflows or resets to a short delay.
        while (stages-- > 0 && delay < maximumDelaySeconds) {
            delay = delay >= maximumDelaySeconds - delay ? maximumDelaySeconds : delay * 2;
        }
        long spread = delay * jitterPercent / 100;
        long lower = Math.max(initialDelaySeconds, delay - spread);
        long upper = Math.min(maximumDelaySeconds, delay + spread);
        long offset = Math.floorMod((long) Objects.hash(tenant, operation, businessId, generation), upper - lower + 1);
        return anchor.plusSeconds(lower + offset);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
