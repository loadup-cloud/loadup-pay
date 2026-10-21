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

import io.github.loadup.commons.json.ToStringAsJson;
import io.github.loadup.pay.common.*;
import java.time.*;
import java.util.*;
import static io.github.loadup.pay.common.DomainViolation.Reason.*;
import static io.github.loadup.pay.common.DomainViolation.require;

/** A trusted parent-fence acknowledgement, separate from per-payment convergence. */
public record ParentCloseEvidence(String orderId, long closeEpoch, boolean fenced, boolean allChildrenConverged) {
    public ParentCloseEvidence {
        DomainChecks.id(orderId);
        require(closeEpoch > 0 && fenced && allChildrenConverged, OUTCOME_UNKNOWN);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }
}
