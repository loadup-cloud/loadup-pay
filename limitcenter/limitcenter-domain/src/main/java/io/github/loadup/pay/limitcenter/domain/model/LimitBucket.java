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

public record LimitBucket(LimitBucketKey key, long used, long held, long version) {
    public LimitBucket {
        Objects.requireNonNull(key);
        require(used >= 0 && held >= 0 && version >= 0, INVALID_ARGUMENT);
    }

    public LimitBucket reserve(long requested, long threshold, boolean enforce) {
        require(requested > 0 && threshold >= 0, INVALID_ARGUMENT);
        if (enforce)
            require(
                    used <= threshold && held <= threshold - used && requested <= threshold - used - held,
                    AMOUNT_EXCEEDED);
        return new LimitBucket(key, used, Math.addExact(held, requested), Math.incrementExact(version));
    }

    public LimitBucket confirm(long quantity) {
        require(quantity > 0 && quantity <= held, CONFLICT);
        return new LimitBucket(key, Math.addExact(used, quantity), held - quantity, Math.incrementExact(version));
    }

    public LimitBucket release(long quantity) {
        require(quantity > 0 && quantity <= held, CONFLICT);
        return new LimitBucket(key, used, held - quantity, Math.incrementExact(version));
    }

    public LimitBucket restore(long quantity) {
        require(quantity > 0 && quantity <= used, CONFLICT);
        return new LimitBucket(key, used - quantity, held, Math.incrementExact(version));
    }
}
