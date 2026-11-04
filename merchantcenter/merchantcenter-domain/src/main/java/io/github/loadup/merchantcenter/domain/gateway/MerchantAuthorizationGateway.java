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
package io.github.loadup.merchantcenter.domain.gateway;

import io.github.loadup.merchantcenter.client.enums.MerchantAction;
import io.github.loadup.merchantcenter.client.enums.PrincipalType;
import io.github.loadup.merchantcenter.domain.model.MerchantActionGrant;
import io.github.loadup.merchantcenter.domain.model.MerchantRelationship;
import java.util.Optional;

/**
 * Persistence port; application owns the transaction and tenant/schema routing is resolved from the
 * execution context before the connection is acquired.
 */
public interface MerchantAuthorizationGateway {
    Optional<MerchantRelationship> findRelationship(String relationshipId);

    Optional<MerchantRelationship> findLiveRelationship(String parentId, String childId);

    Optional<MerchantActionGrant> findGrantById(String grantId);

    MerchantRelationship lockRelationship(String relationshipId);

    MerchantActionGrant lockGrant(String grantId);

    Optional<MerchantActionGrant> findGrant(
            PrincipalType type, String principalId, String payeeMerchantId, MerchantAction action);

    /**
     * An expected version of -1 inserts a new resource; other values require a matching persisted version.
     */
    void saveRelationship(MerchantRelationship relationship, long expectedVersion);

    /**
     * An expected version of -1 inserts a new resource; other values require a matching persisted version.
     */
    void saveGrant(MerchantActionGrant grant, long expectedVersion);
}
