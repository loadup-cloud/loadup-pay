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

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import java.time.LocalDateTime;
import java.util.Objects;

import static io.github.loadup.commons.error.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Pure continuation protocol shared inside payment; no native lease, financial state or dispatch permission.
 */
public record RecoveryPosition(String subjectId, long generation, LocalDateTime dueAt, boolean completed) {
    public RecoveryPosition {
        DomainChecks.id(subjectId);
        Objects.requireNonNull(dueAt);
        require(generation >= 0, INVALID_ARGUMENT);
    }

    public boolean current(RecoveryTaskKey key, LocalDateTime originalExecuteAt) {
        Objects.requireNonNull(key);
        require(subjectId.equals(key.subjectId()) && key.generation() <= generation, CONFLICT);
        if (completed || key.generation() < generation) {
            return false;
        }
        require(dueAt.equals(originalExecuteAt), CONFLICT);
        return true;
    }

    public RecoveryPosition schedule(LocalDateTime nextAt, long expectedGeneration) {
        require(!completed && generation == expectedGeneration, CONFLICT);
        require(nextAt != null && nextAt.isAfter(dueAt), INVALID_ARGUMENT);
        return new RecoveryPosition(subjectId, Math.incrementExact(generation), nextAt, false);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
