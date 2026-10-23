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
package io.github.loadup.pay.realtime.domain.model;

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.common.DomainChecks;

import static io.github.loadup.pay.common.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.pay.common.DomainViolation.require;

/**
 * Versions are comparable only for the same producer and aggregate identity.
 */
public record SourceVersion(String producer, String aggregateType, String aggregateId, long number) {
    public SourceVersion {
        DomainChecks.text(producer, 32);
        DomainChecks.text(aggregateType, 64);
        DomainChecks.id(aggregateId);
        require(number >= 0, INVALID_ARGUMENT);
    }

    public boolean newerThan(SourceVersion previous) {
        require(
                producer.equals(previous.producer)
                        && aggregateType.equals(previous.aggregateType)
                        && aggregateId.equals(previous.aggregateId),
                CONFLICT);
        return number > previous.number;
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
