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
package io.github.loadup.pay.limitcenter.domain.model;

import io.github.loadup.pay.common.*;
import java.time.*;
import java.util.*;
import static io.github.loadup.pay.common.DomainViolation.Reason.*;
import static io.github.loadup.pay.common.DomainViolation.require;

/** Pure all-or-none proposal; the persistence gateway must commit every resulting row atomically. */
public record LimitBucketSet(Map<LimitBucketKey, LimitBucket> buckets) {
    public LimitBucketSet {
        buckets = Map.copyOf(buckets);
        buckets.forEach((key, value) -> require(key.equals(value.key()), CONFLICT));
    }

    public LimitBucketSet reserve(List<LimitBucketChange> changes) {
        Map<LimitBucketKey, LimitBucket> updated = new HashMap<>(buckets);
        Set<LimitBucketKey> seen = new HashSet<>();
        require(!changes.isEmpty() && changes.size() == buckets.size(), INVALID_ARGUMENT);
        for (var change : changes) {
            require(seen.add(change.key()) && buckets.containsKey(change.key()), CONFLICT);
            updated.put(
                    change.key(),
                    buckets.get(change.key()).reserve(change.requested(), change.threshold(), change.enforce()));
        }
        return new LimitBucketSet(updated);
    }
}
