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

import io.github.loadup.productcenter.client.enums.UnpaidOrderAction;

public record OrderPolicy(
        long waitSeconds,
        long maxWaitSeconds,
        UnpaidOrderAction unpaidAction,
        boolean extensionAllowed,
        long extensionMaxSeconds,
        long sessionSeconds) {
    public OrderPolicy {
        PolicyChecks.require(waitSeconds > 0
                && maxWaitSeconds >= waitSeconds
                && sessionSeconds > 0
                && extensionMaxSeconds >= 0
                && (extensionAllowed ? extensionMaxSeconds > 0 : extensionMaxSeconds == 0)
                && unpaidAction != null);
    }
}
