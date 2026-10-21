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
import io.github.loadup.modules.contract.app.support.ContractCodec;
import io.github.loadup.modules.contract.domain.model.ContractRecord;
import io.github.loadup.modules.contract.domain.model.MerchantContractRevision;
import io.github.loadup.modules.contract.infrastructure.converter.ContractStorageConverter;
import io.github.loadup.modules.contract.infrastructure.dataobject.ContractRevisionDO;
import io.github.loadup.modules.contract.infrastructure.dataobject.MerchantContractDO;
import io.github.loadup.modules.contract.infrastructure.mapper.ContractRevisionDOMapper;
import io.github.loadup.modules.contract.infrastructure.mapper.MerchantContractDOMapper;
import io.github.loadup.pay.product.app.model.ActivatedLimitPolicy;
import io.github.loadup.pay.product.app.port.CurrentContractGateway;
import io.github.loadup.pay.product.app.port.PolicyPublicationCodec;
import io.github.loadup.pay.product.infrastructure.converter.AdmissionStorageConverter;
import io.github.loadup.pay.product.infrastructure.converter.PolicyPublicationStorageConverter;
import io.github.loadup.pay.product.infrastructure.dataobject.ContractLimitPublicationDO;
import io.github.loadup.pay.product.infrastructure.mapper.ContractLimitPublicationDOMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class CurrentContractGatewayImpl implements CurrentContractGateway {
    private final MerchantContractDOMapper contracts;
    private final ContractRevisionDOMapper revisions;
    private final ContractLimitPublicationDOMapper publications;
    private final ContractStorageConverter storage;
    private final AdmissionStorageConverter converter;
    private final ContractCodec codec;
    private final PolicyPublicationStorageConverter publicationStorage;
    private final PolicyPublicationCodec publicationCodec;

    public CurrentContractGatewayImpl(
            MerchantContractDOMapper contracts,
            ContractRevisionDOMapper revisions,
            ContractLimitPublicationDOMapper publications,
            ContractStorageConverter storage,
            AdmissionStorageConverter converter,
            ContractCodec codec,
            PolicyPublicationStorageConverter publicationStorage,
            PolicyPublicationCodec publicationCodec) {
        this.contracts = contracts;
        this.revisions = revisions;
        this.publications = publications;
        this.storage = storage;
        this.converter = converter;
        this.codec = codec;
        this.publicationStorage = publicationStorage;
        this.publicationCodec = publicationCodec;
    }

    @Override
    public Optional<ContractRecord> lockByScope(String tenant, String merchant, String scope) {
        var query = QueryWrapper.create()
                .where(MerchantContractDO::getTenantId)
                .eq(tenant)
                .and(MerchantContractDO::getDeleted)
                .eq(0)
                .and(MerchantContractDO::getMerchantId)
                .eq(merchant)
                .and(MerchantContractDO::getScopeKey)
                .eq(scope)
                .forUpdate();
        return Optional.ofNullable(contracts.selectOneByQuery(query)).map(storage::toContract);
    }

    @Override
    public MerchantContractRevision snapshot(ContractRecord header) {
        // The reused framework currently signs immutable revision 1; reject inconsistent storage explicitly.
        var row = revisions.selectOneByQuery(QueryWrapper.create()
                .where(ContractRevisionDO::getTenantId)
                .eq(header.tenantId())
                .and(ContractRevisionDO::getDeleted)
                .eq(0)
                .and(ContractRevisionDO::getContractId)
                .eq(header.id())
                .and(ContractRevisionDO::getRevision)
                .eq(1));
        if (row == null) throw new CommonException(SNAPSHOT_INVALID);
        try {
            var snapshot = codec.read(row.getSnapshot(), MerchantContractRevision.class);
            if (!snapshot.tenantId().equals(header.tenantId())
                    || !snapshot.merchantId().equals(header.merchantId())
                    || !snapshot.scopeKey().equals(header.scopeKey())
                    || !snapshot.contractId().equals(header.id())
                    || snapshot.revision() != row.getRevision()
                    || !snapshot.snapshotHash().equals(row.getSnapshotHash()))
                throw new CommonException(SNAPSHOT_INVALID);
            return snapshot;
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public Optional<ActivatedLimitPolicy> publication(String tenant, String contract, int revision) {
        var row = publications.selectOneByQuery(QueryWrapper.create()
                .where(ContractLimitPublicationDO::getTenantId)
                .eq(tenant)
                .and(ContractLimitPublicationDO::getDeleted)
                .eq(0)
                .and(ContractLimitPublicationDO::getContractId)
                .eq(contract)
                .and(ContractLimitPublicationDO::getContractRevision)
                .eq(revision));
        // Legacy rows without a frozen v1 source/Outbox binding are not trusted activation receipts.
        if (row == null
                || !Integer.valueOf(1).equals(row.getPublicationSchemaVersion())
                || !"ACTIVE".equals(row.getState())) return Optional.empty();
        if (!tenant.equals(row.getTenantId())) throw new CommonException(SNAPSHOT_INVALID);
        try {
            publicationCodec.decode(tenant, publicationStorage.toModel(row));
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
        return Optional.of(converter.toPolicy(row));
    }
}
