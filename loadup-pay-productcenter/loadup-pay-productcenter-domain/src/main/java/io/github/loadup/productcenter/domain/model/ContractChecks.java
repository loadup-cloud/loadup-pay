/*
 * Copyright (C) 2025-2026 LoadUp Cloud
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
package io.github.loadup.productcenter.domain.model;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.productcenter.client.error.ContractResultCodeEnum;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

final class ContractChecks {
    private ContractChecks() {}

    static String code(String value) {
        Objects.requireNonNull(value, "code");
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) {
            throw new CommonException(ContractResultCodeEnum.INVALID_IDENTIFIER);
        }
        return value;
    }

    static int version(int value) {
        if (value < 1) {
            throw new CommonException(ContractResultCodeEnum.VERSION_MUST_BE_POSITIVE);
        }
        return value;
    }

    static void interval(Instant from, Instant to) {
        Objects.requireNonNull(from, "interval start");
        if (to != null && !from.isBefore(to)) {
            throw new CommonException(ContractResultCodeEnum.INVALID_INTERVAL);
        }
    }

    static <T> Map<String, T> map(Map<String, T> values) {
        Objects.requireNonNull(values, "values");
        if (values.size() > 256) {
            throw new CommonException(ContractResultCodeEnum.TOO_MANY_ENTRIES);
        }
        values.keySet().forEach(ContractChecks::code);
        return Map.copyOf(values);
    }
}
