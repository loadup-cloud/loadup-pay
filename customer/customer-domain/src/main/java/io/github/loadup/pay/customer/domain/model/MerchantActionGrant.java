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

public record MerchantActionGrant(
        String id,
        PrincipalType principalType,
        String principalId,
        String payeeMerchantId,
        String relationshipId,
        MerchantAction action,
        GrantState state,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        long version) {
    public MerchantActionGrant {
        DomainChecks.id(id);
        DomainChecks.text(principalId, 128);
        DomainChecks.id(payeeMerchantId);
        if (relationshipId != null) DomainChecks.id(relationshipId);
        Objects.requireNonNull(principalType);
        Objects.requireNonNull(action);
        Objects.requireNonNull(state);
        Objects.requireNonNull(validFrom);
        require((validTo == null || validTo.isAfter(validFrom)) && version >= 0, INVALID_ARGUMENT);
    }

    /** Principal ownership is resolved from trusted identity facts, never a public request field. */
    public boolean allows(
            PrincipalType type,
            String principal,
            String principalMerchantId,
            String payee,
            MerchantAction required,
            LocalDateTime now,
            MerchantRelationship relationship) {
        return state == GrantState.ACTIVE
                && principalType == type
                && principalId.equals(principal)
                && (type != PrincipalType.MERCHANT || principal.equals(principalMerchantId))
                && payeeMerchantId.equals(payee)
                && action == required
                && !now.isBefore(validFrom)
                && (validTo == null || now.isBefore(validTo))
                && (relationshipId == null
                        || relationship != null
                                && relationshipId.equals(relationship.id())
                                && relationship.parentMerchantId().equals(principalMerchantId)
                                && payeeMerchantId.equals(relationship.childMerchantId())
                                && relationship.activeAt(now));
    }

    public static MerchantActionGrant init(String id, PrincipalType type, String principal, String payee,
            String relationship, MerchantAction action, LocalDateTime from, LocalDateTime to) {
        return new MerchantActionGrant(id, type, principal, payee, relationship, action, GrantState.INIT, from, to, 0);
    }

    public MerchantActionGrant activate() {
        if (state == GrantState.ACTIVE) return this;
        require(state == GrantState.INIT, INVALID_STATE);
        return new MerchantActionGrant(id, principalType, principalId, payeeMerchantId, relationshipId,
                action, GrantState.ACTIVE, validFrom, validTo, Math.incrementExact(version));
    }

    public MerchantActionGrant revoke() {
        if (state == GrantState.REVOKED) return this;
        return new MerchantActionGrant(id, principalType, principalId, payeeMerchantId, relationshipId,
                action, GrantState.REVOKED, validFrom, validTo, Math.incrementExact(version));
    }
}
