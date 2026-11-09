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
package io.github.loadup.productcenter.app.service;

import io.github.loadup.productcenter.client.dto.ContractPersistenceHeader;
import io.github.loadup.productcenter.client.dto.ContractPersistenceRevision;
import io.github.loadup.productcenter.client.facade.ContractPersistenceFacade;
import io.github.loadup.productcenter.domain.gateway.MerchantContractGateway;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractPersistenceService implements ContractPersistenceFacade {
    private final MerchantContractGateway gateway;

    public ContractPersistenceService(MerchantContractGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<ContractPersistenceHeader> lockByScope(String merchant, String scope) {
        return gateway.lockByScope(merchant, scope)
                .map(row -> new ContractPersistenceHeader(
                        row.id(),
                        row.merchantId(),
                        row.scopeKey(),
                        row.planVersionId(),
                        row.status().name(),
                        row.generation(),
                        row.requestKey(),
                        row.requestDigest(),
                        row.createdBy(),
                        row.createdAt(),
                        row.updatedAt()));
    }

    @Override
    public Optional<ContractPersistenceRevision> revision(String id, int revision) {
        return gateway.revision(id, revision)
                .map(row -> new ContractPersistenceRevision(row.revision(), row.snapshot(), row.snapshotHash()));
    }
}
