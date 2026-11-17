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

import io.github.loadup.commons.error.DomainViolation;

final class PolicyChecks {
    private PolicyChecks() {}

    static void require(boolean condition) {
        DomainViolation.require(condition, DomainViolation.Reason.INVALID_ARGUMENT);
    }

    static boolean optionalText(String value, int maximum) {
        return value == null || value.length() <= maximum;
    }

    static boolean requiredText(String value, int maximum) {
        return value != null && !value.isBlank() && value.length() <= maximum;
    }
}
