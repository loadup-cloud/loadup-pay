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
package io.github.loadup.pay.customer.domain.gateway;

import io.github.loadup.pay.customer.domain.model.MerchantAction;
import io.github.loadup.pay.customer.domain.model.MerchantActionGrant;
import io.github.loadup.pay.customer.domain.model.MerchantRelationship;
import io.github.loadup.pay.customer.domain.model.PrincipalType;

import java.util.Optional;

/** Persistence port; application owns the transaction and adapters enforce explicit tenant isolation. */
public interface MerchantAuthorizationGateway {
    Optional<MerchantRelationship> findRelationship(String tenantId, String relationshipId);

    Optional<MerchantRelationship> findLiveRelationship(String tenantId, String parentId, String childId);

    Optional<MerchantActionGrant> findGrantById(String tenantId, String grantId);

    MerchantRelationship lockRelationship(String tenantId, String relationshipId);

    MerchantActionGrant lockGrant(String tenantId, String grantId);

    Optional<MerchantActionGrant> findGrant(
            String tenantId, PrincipalType type, String principalId, String payeeMerchantId, MerchantAction action);

    /** An expected version of -1 inserts a new resource; other values require a matching persisted version. */
    void saveRelationship(String tenantId, MerchantRelationship relationship, long expectedVersion);

    /** An expected version of -1 inserts a new resource; other values require a matching persisted version. */
    void saveGrant(String tenantId, MerchantActionGrant grant, long expectedVersion);
}
