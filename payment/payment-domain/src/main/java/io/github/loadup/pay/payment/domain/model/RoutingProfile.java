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
import io.github.loadup.pay.common.*;
import java.time.LocalDateTime;
import java.util.Objects;
import static io.github.loadup.pay.common.DomainViolation.require;
import static io.github.loadup.pay.common.DomainViolation.Reason.INVALID_ARGUMENT;

public record RoutingProfile(String id, RoutingProfileSpec spec, String contentHash, String callerServiceId,
        String requestKey, String requestDigest, LocalDateTime publishedAt) {
    public RoutingProfile {
        DomainChecks.id(id); Objects.requireNonNull(spec);
        DomainChecks.id(callerServiceId); DomainChecks.text(requestKey, 128);
        require(contentHash != null && contentHash.matches("[0-9a-f]{64}")
                && requestDigest != null && requestDigest.matches("[0-9a-f]{64}"), INVALID_ARGUMENT);
        Objects.requireNonNull(publishedAt);
    }
    @Override public String toString() { return ToStringAsJson.reflectionToString(this); }
}
