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
package io.github.loadup.pay.customer.domain.model;

import io.github.loadup.pay.common.*;
import java.time.*;
import java.util.*;
import static io.github.loadup.pay.common.DomainViolation.Reason.*;
import static io.github.loadup.pay.common.DomainViolation.require;

public record MerchantRelationship(
        String id,
        String parentMerchantId,
        String childMerchantId,
        GrantState state,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        long version) {
    public MerchantRelationship {
        DomainChecks.id(id);
        DomainChecks.id(parentMerchantId);
        DomainChecks.id(childMerchantId);
        Objects.requireNonNull(state);
        Objects.requireNonNull(validFrom);
        require(
                !parentMerchantId.equals(childMerchantId)
                        && (validTo == null || validTo.isAfter(validFrom))
                        && version >= 0,
                INVALID_ARGUMENT);
    }

    public boolean activeAt(LocalDateTime now) {
        return state == GrantState.ACTIVE && !now.isBefore(validFrom) && (validTo == null || now.isBefore(validTo));
    }

    public static MerchantRelationship init(String id, String parent, String child,
            LocalDateTime from, LocalDateTime to) {
        return new MerchantRelationship(id, parent, child, GrantState.INIT, from, to, 0);
    }

    public MerchantRelationship activate() {
        if (state == GrantState.ACTIVE) return this;
        require(state == GrantState.INIT, INVALID_STATE);
        return new MerchantRelationship(id, parentMerchantId, childMerchantId, GrantState.ACTIVE,
                validFrom, validTo, Math.incrementExact(version));
    }

    public MerchantRelationship revoke() {
        if (state == GrantState.REVOKED) return this;
        return new MerchantRelationship(id, parentMerchantId, childMerchantId, GrantState.REVOKED,
                validFrom, validTo, Math.incrementExact(version));
    }
}
