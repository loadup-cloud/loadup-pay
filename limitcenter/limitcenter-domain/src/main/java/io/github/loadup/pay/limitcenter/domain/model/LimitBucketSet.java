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

import io.github.loadup.commons.json.ToStringAsJson;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.github.loadup.commons.error.DomainViolation.Reason.CONFLICT;
import static io.github.loadup.commons.error.DomainViolation.Reason.INVALID_ARGUMENT;
import static io.github.loadup.commons.error.DomainViolation.require;

/**
 * Pure all-or-none proposal; the persistence gateway must commit every resulting row atomically.
 */
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

    public LimitBucketSet confirm(List<LimitReservationItem> items) {
        require(!items.isEmpty() && items.size() == buckets.size(), CONFLICT);
        Map<LimitBucketKey, LimitBucket> updated = new HashMap<>(buckets);
        Set<LimitBucketKey> seen = new HashSet<>();
        for (var item : items) {
            require(seen.add(item.bucket()) && buckets.containsKey(item.bucket())
                    && item.consumed() == 0 && item.restored() == 0, CONFLICT);
            updated.put(item.bucket(), buckets.get(item.bucket()).confirm(item.reserved()));
        }
        return new LimitBucketSet(updated);
    }

    /**
     * Requires a trusted release decision at the application boundary.
     */
    public LimitBucketSet releaseReserved(List<LimitReservationItem> items) {
        require(!items.isEmpty() && items.size() == buckets.size(), CONFLICT);
        Map<LimitBucketKey, LimitBucket> updated = new HashMap<>(buckets);
        Set<LimitBucketKey> seen = new HashSet<>();
        for (var item : items) {
            require(
                    seen.add(item.bucket())
                            && buckets.containsKey(item.bucket())
                            && item.consumed() == 0
                            && item.restored() == 0,
                    CONFLICT);
            updated.put(item.bucket(), buckets.get(item.bucket()).release(item.reserved()));
        }
        return new LimitBucketSet(updated);
    }

    @Override
    public String toString() {
        return ToStringAsJson.reflectionToString(this);
    }

    /**
     * Applies only positive deltas to the original buckets, preserving their held quantities.
     */
    public LimitBucketSet restoreRefund(List<LimitItemRestore> changes) {
        require(!changes.isEmpty() && changes.size() == buckets.size(), CONFLICT);
        Map<LimitBucketKey, LimitBucket> updated = new HashMap<>(buckets);
        Set<LimitBucketKey> seen = new HashSet<>();
        for (var change : changes) {
            var key = change.item().bucket();
            require(seen.add(key) && buckets.containsKey(key), CONFLICT);
            if (change.delta() > 0) {
                updated.put(key, buckets.get(key).restore(change.delta()));
            }
        }
        return new LimitBucketSet(updated);
    }
}
