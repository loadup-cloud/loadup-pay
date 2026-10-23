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
package io.github.loadup.pay.customer.infrastructure.repository;

import com.mybatisflex.core.query.QueryWrapper;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.customer.client.error.CustomerResultCodeEnum;
import io.github.loadup.pay.customer.domain.gateway.MerchantAuthorizationGateway;
import io.github.loadup.pay.customer.domain.model.MerchantAction;
import io.github.loadup.pay.customer.domain.model.MerchantActionGrant;
import io.github.loadup.pay.customer.domain.model.MerchantRelationship;
import io.github.loadup.pay.customer.domain.model.PrincipalType;
import io.github.loadup.pay.customer.infrastructure.converter.AuthorizationStorageConverter;
import io.github.loadup.pay.customer.infrastructure.dataobject.MerchantActionGrantDO;
import io.github.loadup.pay.customer.infrastructure.dataobject.MerchantRelationshipDO;
import io.github.loadup.pay.customer.infrastructure.mapper.MerchantActionGrantDOMapper;
import io.github.loadup.pay.customer.infrastructure.mapper.MerchantRelationshipDOMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * All callers acquire the payee merchant row before modifying authorization records.
 */
@Repository
public class MerchantAuthorizationGatewayImpl implements MerchantAuthorizationGateway {
    private final MerchantRelationshipDOMapper relationships;
    private final MerchantActionGrantDOMapper grants;
    private final AuthorizationStorageConverter converter;

    public MerchantAuthorizationGatewayImpl(
            MerchantRelationshipDOMapper relationships,
            MerchantActionGrantDOMapper grants,
            AuthorizationStorageConverter converter) {
        this.relationships = relationships;
        this.grants = grants;
        this.converter = converter;
    }

    private QueryWrapper relationshipScope(String tenant) {
        return QueryWrapper.create()
                .where(MerchantRelationshipDO::getTenantId)
                .eq(tenant)
                .and(MerchantRelationshipDO::getDeleted)
                .eq(0);
    }

    private QueryWrapper grantScope(String tenant) {
        return QueryWrapper.create()
                .where(MerchantActionGrantDO::getTenantId)
                .eq(tenant)
                .and(MerchantActionGrantDO::getDeleted)
                .eq(0);
    }

    @Override
    public Optional<MerchantRelationship> findRelationship(String tenant, String id) {
        return Optional.ofNullable(relationships.selectOneByQuery(relationshipScope(tenant)
                        .and(MerchantRelationshipDO::getId)
                        .eq(id)))
                .map(converter::toRelationship);
    }

    @Override
    public Optional<MerchantRelationship> findLiveRelationship(String tenant, String parent, String child) {
        return Optional.ofNullable(relationships.selectOneByQuery(relationshipScope(tenant)
                        .and(MerchantRelationshipDO::getParentMerchantId)
                        .eq(parent)
                        .and(MerchantRelationshipDO::getChildMerchantId)
                        .eq(child)
                        .and(MerchantRelationshipDO::getRelationType)
                        .eq("MANAGEMENT")
                        .and(MerchantRelationshipDO::getState)
                        .ne("REVOKED")))
                .map(converter::toRelationship);
    }

    @Override
    public MerchantRelationship lockRelationship(String tenant, String id) {
        return Optional.ofNullable(relationships.selectOneByQuery(relationshipScope(tenant)
                        .and(MerchantRelationshipDO::getId)
                        .eq(id)
                        .forUpdate()))
                .map(converter::toRelationship)
                .orElseThrow(() -> new CommonException(CustomerResultCodeEnum.NOT_FOUND));
    }

    @Override
    public Optional<MerchantActionGrant> findGrantById(String tenant, String id) {
        return Optional.ofNullable(grants.selectOneByQuery(
                        grantScope(tenant).and(MerchantActionGrantDO::getId).eq(id)))
                .map(converter::toGrant);
    }

    @Override
    public MerchantActionGrant lockGrant(String tenant, String id) {
        return Optional.ofNullable(grants.selectOneByQuery(grantScope(tenant)
                        .and(MerchantActionGrantDO::getId)
                        .eq(id)
                        .forUpdate()))
                .map(converter::toGrant)
                .orElseThrow(() -> new CommonException(CustomerResultCodeEnum.NOT_FOUND));
    }

    @Override
    public Optional<MerchantActionGrant> findGrant(
            String tenant, PrincipalType type, String principal, String payee, MerchantAction action) {
        return Optional.ofNullable(grants.selectOneByQuery(grantScope(tenant)
                        .and(MerchantActionGrantDO::getPrincipalType)
                        .eq(type.name())
                        .and(MerchantActionGrantDO::getPrincipalId)
                        .eq(principal)
                        .and(MerchantActionGrantDO::getPayeeMerchantId)
                        .eq(payee)
                        .and(MerchantActionGrantDO::getAction)
                        .eq(action.name())
                        .and(MerchantActionGrantDO::getState)
                        .ne("REVOKED")))
                .map(converter::toGrant);
    }

    @Override
    public void saveRelationship(String tenant, MerchantRelationship model, long expected) {
        try {
            if (expected == -1) {
                relationships.insert(converter.toDO(tenant, model));
            } else if (relationships.updateByQuery(
                    converter.toDO(tenant, model),
                    true,
                    relationshipScope(tenant)
                            .and(MerchantRelationshipDO::getId)
                            .eq(model.id())
                            .and(MerchantRelationshipDO::getRowVersion)
                            .eq(expected))
                    != 1) {
                throw new CommonException(CustomerResultCodeEnum.CONFLICT);
            }
        } catch (DuplicateKeyException duplicate) {
            throw new CommonException(CustomerResultCodeEnum.RELATIONSHIP_CONFLICT);
        }
    }

    @Override
    public void saveGrant(String tenant, MerchantActionGrant model, long expected) {
        try {
            if (expected == -1) {
                grants.insert(converter.toDO(tenant, model));
            } else if (grants.updateByQuery(
                    converter.toDO(tenant, model),
                    true,
                    grantScope(tenant)
                            .and(MerchantActionGrantDO::getId)
                            .eq(model.id())
                            .and(MerchantActionGrantDO::getRowVersion)
                            .eq(expected))
                    != 1) {
                throw new CommonException(CustomerResultCodeEnum.CONFLICT);
            }
        } catch (DuplicateKeyException duplicate) {
            throw new CommonException(CustomerResultCodeEnum.GRANT_CONFLICT);
        }
    }
}
