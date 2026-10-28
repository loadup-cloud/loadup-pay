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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Operational attention only; no outcome, capacity release, resubmission or task mutation.
 */
public record RecoveryEscalationPolicy(long reviewAfterSeconds, long urgentAfterSeconds) {
    public RecoveryEscalationPolicy {
        require(reviewAfterSeconds >= 1 && urgentAfterSeconds > reviewAfterSeconds && urgentAfterSeconds <= 31536000L, INVALID_ARGUMENT);
    }

    public long ageSeconds(LocalDateTime firstUnknownAt, LocalDateTime now) {
        return Math.max(0L, Duration.between(Objects.requireNonNull(firstUnknownAt), Objects.requireNonNull(now)).getSeconds());
    }

    public Level level(LocalDateTime firstUnknownAt, LocalDateTime now) {
        long age = ageSeconds(firstUnknownAt, now);
        return age >= urgentAfterSeconds ? Level.URGENT : age >= reviewAfterSeconds ? Level.REVIEW : Level.NORMAL;
    }

    public LocalDateTime cutoff(Level minimumLevel, LocalDateTime now) {
        Objects.requireNonNull(now);
        // NORMAL has no age predicate: a local clock regression must not hide existing UNKNOWN rows.
        return switch (Objects.requireNonNull(minimumLevel)) {
            case NORMAL -> null;
            case REVIEW -> now.minusSeconds(reviewAfterSeconds);
            case URGENT -> now.minusSeconds(urgentAfterSeconds);
        };
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }

    public enum Level {NORMAL, REVIEW, URGENT}
}
