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
package io.github.loadup.productcenter.domain.gateway;

import io.github.loadup.commons.domain.PageResult;
import io.github.loadup.productcenter.domain.model.ContractRecord;
import io.github.loadup.productcenter.domain.model.ContractRevisionRecord;
import io.github.loadup.productcenter.domain.model.ContractStatus;
import java.util.Optional;

public interface MerchantContractGateway {
    Optional<ContractRecord> find(String id);

    Optional<ContractRecord> findByScope(String merchantId, String scopeKey);

    Optional<ContractRecord> lockByScope(String merchantId, String scopeKey);

    Optional<ContractRecord> findByRequest(String requestKey);

    Optional<ContractRevisionRecord> revision(String contractId, int revision);

    void insert(ContractRecord contract, ContractRevisionRecord revision);

    void updateStatus(String id, long expectedGeneration, ContractStatus status);

    PageResult<ContractRecord> page(String merchantId, int page, int size);
}
