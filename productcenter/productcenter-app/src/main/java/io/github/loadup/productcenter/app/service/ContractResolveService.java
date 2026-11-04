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

import io.github.loadup.commons.error.CommonException;
import io.github.loadup.productcenter.app.converter.ContractConverter;
import io.github.loadup.productcenter.app.support.ContractIdentity;
import io.github.loadup.productcenter.app.support.MerchantFacts;
import io.github.loadup.productcenter.client.dto.ContractDecisionDTO;
import io.github.loadup.productcenter.client.error.ContractResultCodeEnum;
import io.github.loadup.productcenter.client.facade.ContractResolveFacade;
import io.github.loadup.productcenter.client.query.ContractResolveQuery;
import io.github.loadup.productcenter.domain.gateway.MerchantContractGateway;
import io.github.loadup.productcenter.domain.model.ContractRuntimeResolver;
import io.github.loadup.productcenter.domain.model.MerchantContract;
import io.github.loadup.productcenter.domain.model.TypedValue;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ContractResolveService implements ContractResolveFacade {
    private final MerchantContractGateway gateway;
    private final MerchantContractService contracts;
    private final MerchantFacts facts;
    private final ContractConverter converter;
    private final Clock clock;

    public ContractResolveService(
            MerchantContractGateway gateway,
            MerchantContractService contracts,
            MerchantFacts facts,
            ContractConverter converter,
            Clock clock) {
        this.gateway = gateway;
        this.contracts = contracts;
        this.facts = facts;
        this.converter = converter;
        this.clock = clock;
    }

    public ContractDecisionDTO resolve(ContractResolveQuery query) {
        ContractIdentity.code(query.merchantId(), 64);
        ContractIdentity.code(query.scopeKey(), 128);
        ContractIdentity.code(query.itemKey(), 256);
        Map<String, TypedValue> values = new HashMap<>(facts.load(query.merchantId()));
        if (query.transactionFacts() == null || query.transactionFacts().size() > 256) {
            throw new CommonException(ContractResultCodeEnum.INVALID_FACTS);
        }
        query.transactionFacts().forEach((key, value) -> {
            if (key == null || value == null || !key.startsWith("transaction.")) {
                throw new CommonException(ContractResultCodeEnum.TRANSACTION_FACT_NAMESPACE_REQUIRED);
            }
            values.put(key, converter.toValue(value));
        });
        // Read authoritative state after external fact loading; do not use asynchronous caches for revocation.
        var header = gateway.findByScope(query.merchantId(), query.scopeKey())
                .orElseThrow(() -> new CommonException(ContractResultCodeEnum.NOT_FOUND));
        var snapshot = contracts.snapshot(header);
        var view = new MerchantContract(
                header.merchantId(),
                header.scopeKey(),
                header.id(),
                header.status(),
                header.generation(),
                List.of(snapshot));
        return converter.toDecision(
                new ContractRuntimeResolver().resolve(view, query.itemKey(), clock.instant(), values));
    }
}
