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
package io.github.loadup.reconciliation.client.support;

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.reconciliation.client.error.ReconciliationResultCodeEnum;

public final class ReconciliationContracts {
    private ReconciliationContracts() {}

    public static void require(boolean valid) {
        if (!valid) {
            throw new CommonException(ReconciliationResultCodeEnum.INVALID_ARGUMENT);
        }
    }

    public static void text(String value, int limit) {
        require(value != null
                && !value.isBlank()
                && value.length() <= limit
                && value.chars().noneMatch(Character::isISOControl));
    }

    public static void hash(String value) {
        require(value != null && value.matches("[0-9a-f]{64}"));
    }
}
