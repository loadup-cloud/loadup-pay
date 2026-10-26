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

import io.github.loadup.commons.error.DomainViolation;
import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.commons.validation.DomainChecks;

import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/** A native order task key; deadline versions and closure generations belong to their respective task types. */
public record OrderTaskKey(String orderId, long generation) {
    public OrderTaskKey {
        DomainChecks.id(orderId);
        require(generation >= 0, INVALID_ARGUMENT);
    }

    public String bizId() { return orderId + "." + generation; }

    public static OrderTaskKey parse(String value) {
        require(value != null && value.length() <= 128, INVALID_ARGUMENT);
        int separator = value.lastIndexOf('.');
        require(separator > 0 && separator < value.length() - 1, INVALID_ARGUMENT);
        final long generation;
        try {
            generation = Long.parseLong(value.substring(separator + 1));
        } catch (NumberFormatException invalid) {
            throw new DomainViolation(INVALID_ARGUMENT);
        }
        var key = new OrderTaskKey(value.substring(0, separator), generation);
        require(key.bizId().equals(value), INVALID_ARGUMENT);
        return key;
    }

    @Override
    public String toString() { return ToStringAsJson.reflectionToString(this); }
}
