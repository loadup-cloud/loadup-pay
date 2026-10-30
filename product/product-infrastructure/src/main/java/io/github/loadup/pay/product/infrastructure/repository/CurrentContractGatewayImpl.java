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

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.modules.contract.app.support.ContractCodec;
import io.github.loadup.modules.contract.client.facade.ContractPersistenceFacade;
import io.github.loadup.modules.contract.domain.model.ContractRecord;
import io.github.loadup.modules.contract.domain.model.ContractStatus;
import io.github.loadup.modules.contract.domain.model.MerchantContractRevision;
import io.github.loadup.pay.product.app.model.ActivatedLimitPolicy;
import io.github.loadup.pay.product.app.port.CurrentContractGateway;
import io.github.loadup.pay.product.app.port.PolicyPublicationCodec;
import io.github.loadup.pay.product.infrastructure.converter.AdmissionStorageConverter;
import io.github.loadup.pay.product.infrastructure.converter.PolicyPublicationStorageConverter;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static io.github.loadup.pay.product.client.error.ProductResultCodeEnum.SNAPSHOT_INVALID;

@Repository
public class CurrentContractGatewayImpl implements CurrentContractGateway {
    private final ContractPersistenceFacade contracts;
    private final ContractLimitPublicationRepository publications;
    private final AdmissionStorageConverter converter;
    private final ContractCodec codec;
    private final PolicyPublicationStorageConverter publicationStorage;
    private final PolicyPublicationCodec publicationCodec;

    public CurrentContractGatewayImpl(
            ContractPersistenceFacade contracts,
            ContractLimitPublicationRepository publications,
            AdmissionStorageConverter converter,
            ContractCodec codec,
            PolicyPublicationStorageConverter publicationStorage,
            PolicyPublicationCodec publicationCodec) {
        this.contracts = contracts;
        this.publications = publications;
        this.converter = converter;
        this.codec = codec;
        this.publicationStorage = publicationStorage;
        this.publicationCodec = publicationCodec;
    }

    @Override
    public Optional<ContractRecord> lockByScope(String tenant, String merchant, String scope) {
        return contracts.lockByScope(tenant, merchant, scope).map(row -> new ContractRecord(
                row.id(), row.tenantId(), row.merchantId(), row.scopeKey(), row.planVersionId(),
                ContractStatus.valueOf(row.status()), row.generation(), row.requestKey(), row.requestDigest(),
                row.createdBy(), row.createdAt(), row.updatedAt()));
    }

    @Override
    public MerchantContractRevision snapshot(ContractRecord header) {
        // The reused framework currently signs immutable revision 1; reject inconsistent storage explicitly.
        var row = contracts.revision(header.tenantId(), header.id(), 1)
                .orElseThrow(() -> new CommonException(SNAPSHOT_INVALID));
        try {
            var snapshot = codec.read(row.snapshot(), MerchantContractRevision.class);
            if (!snapshot.tenantId().equals(header.tenantId())
                    || !snapshot.merchantId().equals(header.merchantId())
                    || !snapshot.scopeKey().equals(header.scopeKey())
                    || !snapshot.contractId().equals(header.id())
                    || snapshot.revision() != row.revision()
                    || !snapshot.snapshotHash().equals(row.snapshotHash()))
                throw new CommonException(SNAPSHOT_INVALID);
            return snapshot;
        } catch (RuntimeException invalid) {
            throw new CommonException(SNAPSHOT_INVALID);
        }
    }

    @Override
    public Optional<ActivatedLimitPolicy> publication(String tenant, String contract, int revision) {
        var row = publications
                .findPublished(contract, revision)
                .orElse(null);
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
