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
package io.github.loadup.pay.product.infrastructure.repository;

import com.mybatisflex.core.query.QueryWrapper;
import io.github.loadup.commons.error.CommonException;
import io.github.loadup.pay.product.domain.gateway.PolicyPublicationGateway;
import io.github.loadup.pay.product.domain.model.PolicyPublication;
import io.github.loadup.pay.product.infrastructure.converter.PolicyPublicationStorageConverter;
import io.github.loadup.pay.product.infrastructure.dataobject.ContractLimitPublicationDO;
import io.github.loadup.pay.product.infrastructure.mapper.ContractLimitPublicationDOMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.POLICY_PUBLICATION_CONFLICT;
import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class PolicyPublicationGatewayImpl implements PolicyPublicationGateway {
    private final ContractLimitPublicationDOMapper mapper;
    private final PolicyPublicationStorageConverter converter;

    public PolicyPublicationGatewayImpl(
            ContractLimitPublicationDOMapper mapper, PolicyPublicationStorageConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    private QueryWrapper scope(String tenant) {
        return QueryWrapper.create()
                .where(ContractLimitPublicationDO::getTenantId)
                .eq(tenant)
                .and(ContractLimitPublicationDO::getDeleted)
                .eq(0);
    }

    private Optional<PolicyPublication> read(String tenant, ContractLimitPublicationDO row) {
        if (row == null) return Optional.empty();
        if (!tenant.equals(row.getTenantId())) throw new CommonException(SNAPSHOT_INVALID);
        try {
            return Optional.of(converter.toModel(row));
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public Optional<PolicyPublication> findById(String tenant, String id) {
        return read(
                tenant,
                mapper.selectOneByQuery(
                        scope(tenant).and(ContractLimitPublicationDO::getId).eq(id)));
    }

    @Override
    public Optional<PolicyPublication> lockById(String tenant, String id) {
        return read(
                tenant,
                mapper.selectOneByQuery(scope(tenant)
                        .and(ContractLimitPublicationDO::getId)
                        .eq(id)
                        .forUpdate()));
    }

    @Override
    public Optional<PolicyPublication> findByContract(String tenant, String contract, int revision) {
        return read(
                tenant,
                mapper.selectOneByQuery(scope(tenant)
                        .and(ContractLimitPublicationDO::getContractId)
                        .eq(contract)
                        .and(ContractLimitPublicationDO::getContractRevision)
                        .eq(revision)));
    }

    @Override
    public Optional<PolicyPublication> findByMerchant(String tenant, String merchant) {
        return read(
                tenant,
                mapper.selectOneByQuery(scope(tenant)
                        .and(ContractLimitPublicationDO::getPayeeMerchantId)
                        .eq(merchant)));
    }

    @Override
    public Optional<PolicyPublication> findByPolicy(String tenant, String policy) {
        return read(
                tenant,
                mapper.selectOneByQuery(scope(tenant)
                        .and(ContractLimitPublicationDO::getPolicyId)
                        .eq(policy)));
    }

    @Override
    public void insert(String tenant, PolicyPublication value) {
        mapper.insert(converter.toDO(tenant, value));
    }

    @Override
    public void update(String tenant, PolicyPublication value, long expectedVersion) {
        int changed = mapper.updateByQuery(
                converter.toDO(tenant, value),
                true,
                scope(tenant)
                        .and(ContractLimitPublicationDO::getId)
                        .eq(value.id())
                        .and(ContractLimitPublicationDO::getRowVersion)
                        .eq(expectedVersion));
        if (changed != 1) throw new CommonException(POLICY_PUBLICATION_CONFLICT);
    }
}
